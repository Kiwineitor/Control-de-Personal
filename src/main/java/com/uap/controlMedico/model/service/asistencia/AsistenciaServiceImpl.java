package com.uap.controlMedico.model.service.asistencia;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uap.controlMedico.exception.BusinessException;
import com.uap.controlMedico.exception.ResourceNotFoundException;
import com.uap.controlMedico.model.dao.CodigoQrDao;
import com.uap.controlMedico.model.dao.InternoJornadaDao;
import com.uap.controlMedico.model.dao.RegistroAsistenciaDao;
import com.uap.controlMedico.model.dto.asistenciaDto.QrEscaneoRequest;
import com.uap.controlMedico.model.dto.asistenciaDto.RegistroAsistenciaRequest;
import com.uap.controlMedico.model.dto.asistenciaDto.RegistroAsistenciaResponse;
import com.uap.controlMedico.model.entity.CodigoQr;
import com.uap.controlMedico.model.entity.Interno;
import com.uap.controlMedico.model.entity.InternoJornada;
import com.uap.controlMedico.model.entity.Lugar;
import com.uap.controlMedico.model.entity.RegistroAsistencia;
import com.uap.controlMedico.model.enums.EstadoAsignacion;
import com.uap.controlMedico.model.enums.EstadoEntrada;
import com.uap.controlMedico.model.enums.EstadoJornada;
import com.uap.controlMedico.model.enums.EstadoSalida;
import com.uap.controlMedico.model.enums.MetodoRegistro;
import com.uap.controlMedico.model.service.SisuMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AsistenciaServiceImpl implements AsistenciaService {
    
    private final RegistroAsistenciaDao registroAsistenciaDao;
    private final InternoJornadaDao     internoJornadaDao;
    private final CodigoQrDao           codigoQrDao;
    private final SisuMapper            mapper;
 
    @Value("${sisu.permanencia.tolerancia-tardanza-minutos:10}")
    private int toleranciaTardanzaMinutos;
 
    @Override
    @Transactional
    public RegistroAsistenciaResponse registrar(RegistroAsistenciaRequest request) {
        InternoJornada ij = internoJornadaDao.findById(request.getIdInternoJornada())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "InternoJornada", request.getIdInternoJornada()));
 
        RegistroAsistencia ra = obtenerOCrearRegistro(ij);
 
        if (Boolean.TRUE.equals(request.getEsEntrada()))
            registrarEntrada(ra, request.getMetodoRegistro(),
                request.getLatitud(), request.getLongitud());
        else
            registrarSalida(ra, request.getMetodoRegistro(),
                request.getLatitud(), request.getLongitud());
 
        ra.setObservaciones(request.getObservaciones());
        return mapper.toRegistroAsistenciaResponse(registroAsistenciaDao.save(ra));
    }
 
    @Override
    @Transactional
    public RegistroAsistenciaResponse registrarPorQr(QrEscaneoRequest request) {
        // Validar QR
        CodigoQr qr = codigoQrDao.findByCodigoAndActivoTrue(request.getCodigo())
                .orElseThrow(() -> new BusinessException("Código QR inválido o desactivado"));
 
        if (!qr.esValido())
            throw new BusinessException("El código QR ha expirado");
 
        // Buscar jornada activa del interno
        Lugar lugar = qr.getLugar();
        List<InternoJornada> jornadasActivas = internoJornadaDao
                .findJornadasActivasDeInterno(
                    lugar.getIdLugar(),
                    EstadoAsignacion.EN_CURSO,
                    EstadoAsignacion.CONFIRMADO,
                    EstadoAsignacion.PROGRAMADO,
                    EstadoJornada.ACTIVA

                );
 
        if (jornadasActivas.isEmpty())
            throw new BusinessException(
                "El interno no tiene jornada activa en este momento");
 
        InternoJornada ij = jornadasActivas.get(0);
        RegistroAsistencia ra = obtenerOCrearRegistro(ij);
 
        if (Boolean.TRUE.equals(request.getEsEntrada()))
            registrarEntrada(ra, MetodoRegistro.QR,
                request.getLatitud(), request.getLongitud());
        else
            registrarSalida(ra, MetodoRegistro.QR,
                request.getLatitud(), request.getLongitud());
 
        return mapper.toRegistroAsistenciaResponse(registroAsistenciaDao.save(ra));
    }
 
    @Override
    @Transactional(readOnly = true)
    public RegistroAsistenciaResponse buscarPorInternoJornada(Long idInternoJornada) {
        return mapper.toRegistroAsistenciaResponse(
            registroAsistenciaDao.findByInternoJornada_IdInternoJornada(idInternoJornada)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "RegistroAsistencia para InternoJornada", idInternoJornada))
        );
    }
 
    @Override
    @Transactional(readOnly = true)
    public List<RegistroAsistenciaResponse> findByFecha(LocalDate fecha) {
        return registroAsistenciaDao.findByFecha(fecha).stream()
                .map(mapper::toRegistroAsistenciaResponse).collect(Collectors.toList());
    }
 
    @Override
    @Transactional(readOnly = true)
    public List<RegistroAsistenciaResponse> findTardanzas(LocalDate desde, LocalDate hasta) {
        return registroAsistenciaDao.findTardanzasEnPeriodo(desde, hasta, EstadoEntrada.TARDANZA)
                .stream().map(mapper::toRegistroAsistenciaResponse).collect(Collectors.toList());
    }
 
    @Override
    @Transactional(readOnly = true)
    public List<RegistroAsistenciaResponse> findInasistencias(LocalDate desde, LocalDate hasta) {
        return registroAsistenciaDao
                .findInasistenciasEnPeriodo(desde, hasta, EstadoEntrada.AUSENTE)
                .stream().map(mapper::toRegistroAsistenciaResponse).collect(Collectors.toList());
    }
 
    @Override
    @Transactional(readOnly = true)
    public List<RegistroAsistenciaResponse> findHistorialDeInterno(
            Long idInterno, LocalDate desde, LocalDate hasta) {
        return registroAsistenciaDao.findHistorialDeInterno(idInterno, desde, hasta)
                .stream().map(mapper::toRegistroAsistenciaResponse).collect(Collectors.toList());
    }
 
    @Override
    @Transactional(readOnly = true)
    public List<RegistroAsistenciaResponse> findConEntradaSinSalida(LocalDate fecha) {
        return registroAsistenciaDao.findConEntradaSinSalida(fecha)
                .stream().map(mapper::toRegistroAsistenciaResponse).collect(Collectors.toList());
    }
 
    // ── Helpers privados ───────────────────────────────────────────────
 
    private RegistroAsistencia obtenerOCrearRegistro(InternoJornada ij) {
        return registroAsistenciaDao
            .findByInternoJornada_IdInternoJornada(ij.getIdInternoJornada())
            .orElseGet(() -> {
                RegistroAsistencia nuevo = RegistroAsistencia.builder()
                    .internoJornada(ij)
                    .horaEntradaEsperada(ij.getJornada().getHoraInicio())
                    .horaSalidaEsperada(ij.getJornada().getHoraFin())
                    .build();
                return registroAsistenciaDao.save(nuevo);
            });
    }
 
    private void registrarEntrada(RegistroAsistencia ra, MetodoRegistro metodo,
                                  java.math.BigDecimal lat, java.math.BigDecimal lng) {
        if (ra.tieneEntrada())
            throw new BusinessException("La entrada ya fue registrada para esta jornada");
 
        LocalTime ahora = LocalTime.now();
        ra.setHoraEntradaReal(ahora);
        ra.setMetodoRegistroEntrada(metodo);
        ra.setLatitudEntrada(lat);
        ra.setLongitudEntrada(lng);
 
        // Calcular estado de entrada
        LocalTime limite = ra.getHoraEntradaEsperada() != null
            ? ra.getHoraEntradaEsperada().plusMinutes(toleranciaTardanzaMinutos)
            : null;
 
        if (limite == null || !ahora.isAfter(limite))
            ra.setEstadoEntrada(EstadoEntrada.A_TIEMPO);
        else
            ra.setEstadoEntrada(EstadoEntrada.TARDANZA);
    }
 
    private void registrarSalida(RegistroAsistencia ra, MetodoRegistro metodo,
                                 java.math.BigDecimal lat, java.math.BigDecimal lng) {
        if (!ra.tieneEntrada())
            throw new BusinessException("No se puede registrar salida sin entrada previa");
        if (ra.tieneSalida())
            throw new BusinessException("La salida ya fue registrada para esta jornada");
 
        LocalTime ahora = LocalTime.now();
        ra.setHoraSalidaReal(ahora);
        ra.setMetodoRegistroSalida(metodo);
        ra.setLatitudSalida(lat);
        ra.setLongitudSalida(lng);
 
        // Calcular estado de salida
        if (ra.getHoraSalidaEsperada() == null || !ahora.isBefore(ra.getHoraSalidaEsperada()))
            ra.setEstadoSalida(EstadoSalida.COMPLETO);
        else
            ra.setEstadoSalida(EstadoSalida.SALIDA_ANTICIPADA);
    }

}
