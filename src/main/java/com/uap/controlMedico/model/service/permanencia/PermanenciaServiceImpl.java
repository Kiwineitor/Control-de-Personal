package com.uap.controlMedico.model.service.permanencia;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uap.controlMedico.exception.BusinessException;
import com.uap.controlMedico.exception.ResourceNotFoundException;
import com.uap.controlMedico.model.dao.InternoDao;
import com.uap.controlMedico.model.dao.RondaVerificacionDao;
import com.uap.controlMedico.model.dao.TitularDao;
import com.uap.controlMedico.model.dao.VerificacionDao;
import com.uap.controlMedico.model.dto.permanenciaDto.RondaVerificacionRequest;
import com.uap.controlMedico.model.dto.permanenciaDto.RondaVerificacionResponse;
import com.uap.controlMedico.model.dto.permanenciaDto.VerificacionPresenciaRequest;
import com.uap.controlMedico.model.entity.Interno;
import com.uap.controlMedico.model.entity.RondaVerificacion;
import com.uap.controlMedico.model.entity.Titular;
import com.uap.controlMedico.model.entity.VerificacionPresencia;
import com.uap.controlMedico.model.enums.EstadoAsignacion;
import com.uap.controlMedico.model.enums.EstadoJornada;
import com.uap.controlMedico.model.enums.EstadoRonda;
import com.uap.controlMedico.model.enums.EstadoVerificacion;
import com.uap.controlMedico.model.service.SisuMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PermanenciaServiceImpl implements PermanenciaService {
    
    private final RondaVerificacionDao     rondaDao;
    private final VerificacionDao verificacionDao;
    private final TitularDao               titularDao;
    private final InternoDao               internoDao;
    private final SisuMapper               mapper;
 
    @Value("${sisu.permanencia.timeout-respuesta-minutos:10}")
    private int timeoutMinutos;
 
    @Override
    @Transactional
    public RondaVerificacionResponse crearRonda(RondaVerificacionRequest request) {
 
        RondaVerificacion ronda = RondaVerificacion.builder()
                .nombre(request.getNombre())
                .tipoRonda(request.getTipoRonda())
                .estado(EstadoRonda.EN_CURSO)
                .fechaHoraInicio(LocalDateTime.now())
                .timeoutMinutos(request.getTimeoutMinutos() != null
                    ? request.getTimeoutMinutos() : timeoutMinutos)
                .observaciones(request.getObservaciones())
                .build();
 
        ronda = rondaDao.save(ronda);
 
        // FIX: variable efectivamente final para usar dentro de lambdas
        final RondaVerificacion rondaFinal = ronda;
 
        List<VerificacionPresencia> verificaciones = new ArrayList<>();
 
        // Titulares en turno ahora
        titularDao.findTitularesEnTurnoActual(
                    EstadoAsignacion.EN_CURSO,
    EstadoAsignacion.CONFIRMADO,
    EstadoJornada.ACTIVA

        ).forEach(t -> verificaciones.add(crearVerificacion(rondaFinal, t, null)));
 
        // Internos en turno ahora
        internoDao.findInternosEnTurnoActual(
                    EstadoAsignacion.EN_CURSO,
    EstadoAsignacion.CONFIRMADO,
    EstadoJornada.ACTIVA

        ).forEach(i -> verificaciones.add(crearVerificacion(rondaFinal, null, i)));
 
        verificacionDao.saveAll(verificaciones);
 
        return mapper.toRondaResponse(
            rondaDao.findById(rondaFinal.getIdRondaVerificacion()).orElseThrow()
        );
    }
 
    @Override
    @Transactional(readOnly = true)
    public RondaVerificacionResponse buscarRonda(Long id) {
        return mapper.toRondaResponse(
            rondaDao.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RondaVerificacion", id))
        );
    }
 
    @Override
    @Transactional(readOnly = true)
    public List<RondaVerificacionResponse> findRondasHoy() {
        return rondaDao.findByFecha(LocalDate.now()).stream()
                .map(mapper::toRondaResponse)
                .collect(Collectors.toList());
    }
 
    @Override
    @Transactional(readOnly = true)
    public List<RondaVerificacionResponse> findEnCurso() {
        return rondaDao.findEnCurso(EstadoRonda.EN_CURSO).stream()
                .map(mapper::toRondaResponse)
                .collect(Collectors.toList());
    }
 
    @Override
    @Transactional
    public RondaVerificacionResponse responderVerificacion(VerificacionPresenciaRequest request) {
        VerificacionPresencia v = verificacionDao.findById(request.getIdVerificacionPresencia())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "VerificacionPresencia", request.getIdVerificacionPresencia()));
 
        if (!EstadoVerificacion.PENDIENTE.equals(v.getEstadoVerificacion()))
            throw new BusinessException("Esta verificación ya fue respondida anteriormente");
 
        v.setEstadoVerificacion(request.getEstadoVerificacion());
        v.setFechaHoraRespuesta(LocalDateTime.now());
        v.setLatitud(request.getLatitud());
        v.setLongitud(request.getLongitud());
        v.setFotoUrl(request.getFotoUrl());
        v.setObservaciones(request.getObservaciones());
        verificacionDao.save(v);
 
        return mapper.toRondaResponse(v.getRondaVerificacion());
    }
 
    @Override
    @Transactional
    public void procesarTimeoutsVencidos() {
        LocalDateTime limite = LocalDateTime.now().minusMinutes(timeoutMinutos);
 
        verificacionDao.findPendientesVencidas(limite, EstadoVerificacion.PENDIENTE)
            .forEach(v -> {
                v.setEstadoVerificacion(EstadoVerificacion.SIN_RESPUESTA);
                verificacionDao.save(v);
                // TODO: disparar alerta al supervisor (email / notificación push)
            });
 
        // Cerrar rondas cuyo timeout global ya venció
        rondaDao.findVencidasAntesDe(limite, EstadoRonda.EN_CURSO)
            .forEach(r -> cerrarRonda(r.getIdRondaVerificacion()));
    }
 
    @Override
    @Transactional
    public void cerrarRonda(Long id) {
        RondaVerificacion ronda = rondaDao.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RondaVerificacion", id));
 
        verificacionDao.marcarSinRespuestaByRonda(
            id, EstadoVerificacion.PENDIENTE, EstadoVerificacion.SIN_RESPUESTA);
 
        ronda.setEstado(EstadoRonda.COMPLETADA);
        ronda.setFechaHoraFin(LocalDateTime.now());
        rondaDao.save(ronda);
    }
 
    // ── Helper ─────────────────────────────────────────────────────────
 
    private VerificacionPresencia crearVerificacion(
            RondaVerificacion ronda, Titular titular, Interno interno) {
        return VerificacionPresencia.builder()
                .rondaVerificacion(ronda)
                .titular(titular)
                .interno(interno)
                .estadoVerificacion(EstadoVerificacion.PENDIENTE)
                .fechaHoraEnvio(LocalDateTime.now())
                .build();
    }
}