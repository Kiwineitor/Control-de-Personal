package com.uap.controlMedico.model.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uap.controlMedico.model.dao.CodigoQrDao;
import com.uap.controlMedico.model.dao.InternoJornadaDao;
import com.uap.controlMedico.model.dao.JornadaDao;
import com.uap.controlMedico.model.dao.LugarDao;
import com.uap.controlMedico.model.dao.RegistroAsistenciaDao;
import com.uap.controlMedico.model.dao.RondaVerificacionDao;
import com.uap.controlMedico.model.dao.TitularJornadaDao;
import com.uap.controlMedico.model.dao.VerificacionPresenciaDao;
import com.uap.controlMedico.model.dto.monitoreo.CodigoQrDto;
import com.uap.controlMedico.model.dto.monitoreo.ConfirmarPresenciaRequest;
import com.uap.controlMedico.model.dto.monitoreo.EscanearQrRequest;
import com.uap.controlMedico.model.dto.monitoreo.EstadisticasMonitoreoDto;
import com.uap.controlMedico.model.dto.monitoreo.RegistrarMovimientoRequest;
import com.uap.controlMedico.model.dto.monitoreo.RegistroAsistenciaDto;
import com.uap.controlMedico.model.dto.monitoreo.ResumenRondaDto;
import com.uap.controlMedico.model.dto.monitoreo.RondaVerificacionDto;
import com.uap.controlMedico.model.dto.monitoreo.RondaVerificacionRequest;
import com.uap.controlMedico.model.dto.monitoreo.VerificacionPresenciaDto;
import com.uap.controlMedico.model.entity.CodigoQr;
import com.uap.controlMedico.model.entity.InternoJornada;
import com.uap.controlMedico.model.entity.Jornada;
import com.uap.controlMedico.model.entity.Lugar;
import com.uap.controlMedico.model.entity.Persona;
import com.uap.controlMedico.model.entity.RegistroAsistencia;
import com.uap.controlMedico.model.entity.RondaVerificacion;
import com.uap.controlMedico.model.entity.TitularJornada;
import com.uap.controlMedico.model.entity.VerificacionPresencia;
import com.uap.controlMedico.model.enums.EstadoEntrada;
import com.uap.controlMedico.model.enums.EstadoJornada;
import com.uap.controlMedico.model.enums.EstadoRonda;
import com.uap.controlMedico.model.enums.EstadoSalida;
import com.uap.controlMedico.model.enums.EstadoVerificacion;
import com.uap.controlMedico.model.enums.MetodoRegistro;
import com.uap.controlMedico.model.enums.MetodoVerificacion;
import com.uap.controlMedico.model.enums.TipoRonda;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class MonitoreoServiceImpl implements MonitoreoService  {
    
    private final CodigoQrDao            codigoQrDao;
    private final RondaVerificacionDao   rondaDao;
    private final VerificacionPresenciaDao verificacionDao;
    private final RegistroAsistenciaDao  registroDao;
    private final LugarDao               lugarDao;
    private final JornadaDao             jornadaDao;
    private final TitularJornadaDao      titularJornadaDao;
    private final InternoJornadaDao      internoJornadaDao;

    // ════════════════════════════════════════════════════════════════════════
    //  CÓDIGOS QR
    // ════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public CodigoQrDto crearQrParaLugar(Long idLugar) {
        Lugar lugar = findLugarOrThrow(idLugar);
        if (codigoQrDao.existsByLugar_IdLugar(idLugar))
            throw new IllegalStateException("El lugar ya tiene un QR. Use /regenerar.");
        CodigoQr qr = CodigoQr.builder()
            .lugar(lugar)
            .codigo(UUID.randomUUID().toString())
            .activo(true)
            .build();
        return toQrDto(codigoQrDao.save(qr));
    }

    @Override
    @Transactional
    public CodigoQrDto regenerarQr(Long idLugar) {
        CodigoQr qr = codigoQrDao.findByLugar_IdLugar(idLugar)
            .orElseGet(() -> {
                Lugar l = findLugarOrThrow(idLugar);
                return CodigoQr.builder().lugar(l).activo(true).build();
            });
        qr.setCodigo(UUID.randomUUID().toString());
        qr.setActivo(true);
        qr.setImagenUrl(null); // se regenerará en el frontend/servicio de imagen
        log.info("QR regenerado para lugar {}", idLugar);
        return toQrDto(codigoQrDao.save(qr));
    }

    @Override
    @Transactional
    public CodigoQrDto toggleQr(Long idLugar) {
        CodigoQr qr = codigoQrDao.findByLugar_IdLugar(idLugar)
            .orElseThrow(() -> new EntityNotFoundException("Sin QR para lugar: " + idLugar));
        qr.setActivo(!qr.isActivo());
        return toQrDto(codigoQrDao.save(qr));
    }

    @Override
    @Transactional(readOnly = true)
    public CodigoQrDto findQrByLugar(Long idLugar) {
        return codigoQrDao.findByLugar_IdLugar(idLugar)
            .map(this::toQrDto)
            .orElseThrow(() -> new EntityNotFoundException("Sin QR para lugar: " + idLugar));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CodigoQrDto> listarQrs() {
        return codigoQrDao.findAllByOrderByLugar_NombreAsc()
            .stream().map(this::toQrDto).toList();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  RONDAS
    // ════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public Page<RondaVerificacionDto> listarRondas(String q, EstadoRonda estado,
                                                    LocalDate fecha, Pageable pageable) {
        return rondaDao.findConFiltros(q == null ? "" : q, estado, fecha, pageable)
            .map(this::toRondaDto);
    }

    @Override
    @Transactional(readOnly = true)
    public RondaVerificacionDto findRondaById(Long id) {
        return toRondaDto(findRondaOrThrow(id));
    }

    @Override
    @Transactional
    public RondaVerificacionDto crearRonda(RondaVerificacionRequest request) {
        RondaVerificacion ronda = RondaVerificacion.builder()
            .nombre(request.getNombre())
            .tipoRonda(request.getTipoRonda() != null ? request.getTipoRonda() : TipoRonda.ALEATORIA)
            .estado(EstadoRonda.PENDIENTE)
            .fechaHoraInicio(request.getFechaHoraInicio())
            .timeoutMinutos(request.getTimeoutMinutos() != null ? request.getTimeoutMinutos() : 10)
            .observaciones(request.getObservaciones())
            .build();
        ronda = rondaDao.save(ronda);

        // Generar verificaciones para el personal de la jornada (o todas las activas)
        generarVerificaciones(ronda, request.getIdJornada());
        return toRondaDto(rondaDao.findById(ronda.getIdRondaVerificacion()).orElse(ronda));
    }

    @Override
    @Transactional
    public RondaVerificacionDto iniciarRonda(Long id) {
        RondaVerificacion ronda = findRondaOrThrow(id);
        if (ronda.getEstado() != EstadoRonda.PENDIENTE)
            throw new IllegalStateException("Solo se pueden iniciar rondas PENDIENTES");
        ronda.setEstado(EstadoRonda.EN_CURSO);
        ronda.setFechaHoraInicio(LocalDateTime.now());
        return toRondaDto(rondaDao.save(ronda));
    }

    @Override
    @Transactional
    public RondaVerificacionDto cerrarRonda(Long id) {
        RondaVerificacion ronda = findRondaOrThrow(id);
        if (ronda.getEstado() == EstadoRonda.CANCELADA)
            throw new IllegalStateException("No se puede cerrar una ronda CANCELADA");

        // Marcar pendientes como SIN_RESPUESTA
        List<VerificacionPresencia> pendientes = verificacionDao
            .findByRondaVerificacion_IdRondaVerificacionAndEstadoVerificacion(
                id, EstadoVerificacion.PENDIENTE);
        pendientes.forEach(v -> v.setEstadoVerificacion(EstadoVerificacion.SIN_RESPUESTA));
        verificacionDao.saveAll(pendientes);

        ronda.setEstado(EstadoRonda.COMPLETADA);
        ronda.setFechaHoraFin(LocalDateTime.now());
        return toRondaDto(rondaDao.save(ronda));
    }

    @Override
    @Transactional
    public RondaVerificacionDto cancelarRonda(Long id) {
        RondaVerificacion ronda = findRondaOrThrow(id);
        if (ronda.getEstado() == EstadoRonda.COMPLETADA)
            throw new IllegalStateException("No se puede cancelar una ronda COMPLETADA");
        ronda.setEstado(EstadoRonda.CANCELADA);
        ronda.setFechaHoraFin(LocalDateTime.now());
        return toRondaDto(rondaDao.save(ronda));
    }

    // ════════════════════════════════════════════════════════════════════════
    //  VERIFICACIONES
    // ════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<VerificacionPresenciaDto> findVerificacionesByRonda(Long idRonda) {
        return verificacionDao.findByRondaConDetalle(idRonda)
            .stream().map(this::toVerifDto).toList();
    }

    @Override
    @Transactional
    public VerificacionPresenciaDto confirmarPresencia(Long idVerificacion,
                                                        ConfirmarPresenciaRequest request) {
        VerificacionPresencia v = verificacionDao.findById(idVerificacion)
            .orElseThrow(() -> new EntityNotFoundException("Verificación no encontrada: " + idVerificacion));

        if (v.getEstadoVerificacion() != EstadoVerificacion.PENDIENTE)
            throw new IllegalStateException("Esta verificación ya fue procesada");

        // Validar geocerca si tiene GPS
        if (request.getLatitud() != null && request.getLongitud() != null) {
            validarGeocercaPersonal(v, request.getLatitud(), request.getLongitud());
        }

        v.setEstadoVerificacion(EstadoVerificacion.PRESENTE);
        v.setMetodoVerificacion(request.getMetodo());
        v.setFechaHoraRespuesta(LocalDateTime.now());
        v.setLatitud(request.getLatitud());
        v.setLongitud(request.getLongitud());
        v.setFotoUrl(request.getFotoUrl());
        if (request.getObservaciones() != null) v.setObservaciones(request.getObservaciones());

        return toVerifDto(verificacionDao.save(v));
    }

    @Override
    @Transactional
    public VerificacionPresenciaDto marcarAusente(Long idVerificacion, String observaciones) {
        VerificacionPresencia v = verificacionDao.findById(idVerificacion)
            .orElseThrow(() -> new EntityNotFoundException("Verificación no encontrada: " + idVerificacion));
        v.setEstadoVerificacion(EstadoVerificacion.AUSENTE);
        v.setFechaHoraRespuesta(LocalDateTime.now());
        v.setObservaciones(observaciones);
        return toVerifDto(verificacionDao.save(v));
    }

    @Override
    @Transactional
    public VerificacionPresenciaDto escanearQr(EscanearQrRequest request) {
        // 1. Validar el QR del lugar
        CodigoQr qr = codigoQrDao.findByCodigo(request.getCodigoQr())
            .orElseThrow(() -> new EntityNotFoundException("QR inválido o no reconocido"));

        if (!qr.esValido())
            throw new IllegalStateException("El QR del lugar está inactivo o expirado");

        // 2. Validar geocerca del lugar (si tiene configurada)
        Lugar lugar = qr.getLugar();
        if (request.getLatitud() != null && lugar.getLatitudCentro() != null) {
            double dist = calcularDistanciaMetros(
                request.getLatitud().doubleValue(),  request.getLongitud().doubleValue(),
                lugar.getLatitudCentro().doubleValue(), lugar.getLongitudCentro().doubleValue()
            );
            int radio = lugar.getRadioMetros() != null ? lugar.getRadioMetros() : 100;
            if (dist > radio * 1.5) // 50% de tolerancia extra para QR
                throw new IllegalStateException("GPS demasiado lejos del lugar verificado");
        }

        // 3. Buscar verificación pendiente para esta persona en rondas activas
        Optional<VerificacionPresencia> opt;
        if ("INTERNO".equalsIgnoreCase(request.getTipoPersona())) {
            opt = rondaDao.findByEstadoOrderByFechaHoraInicioDesc(EstadoRonda.EN_CURSO)
                .stream()
                .map(r -> verificacionDao
                    .findByRondaVerificacion_IdRondaVerificacionAndInterno_IdInterno(
                        r.getIdRondaVerificacion(), request.getIdPersona())
                    .filter(v -> v.getEstadoVerificacion() == EstadoVerificacion.PENDIENTE)
                    .orElse(null))
                .filter(Objects::nonNull)
                .findFirst();
        } else {
            opt = rondaDao.findByEstadoOrderByFechaHoraInicioDesc(EstadoRonda.EN_CURSO)
                .stream()
                .map(r -> verificacionDao
                    .findByRondaVerificacion_IdRondaVerificacionAndTitular_IdTitular(
                        r.getIdRondaVerificacion(), request.getIdPersona())
                    .filter(v -> v.getEstadoVerificacion() == EstadoVerificacion.PENDIENTE)
                    .orElse(null))
                .filter(Objects::nonNull)
                .findFirst();
        }

        VerificacionPresencia v = opt.orElseThrow(() ->
            new EntityNotFoundException("Sin verificación pendiente en rondas activas para este personal"));

        v.setEstadoVerificacion(EstadoVerificacion.PRESENTE);
        v.setMetodoVerificacion(MetodoVerificacion.QR);
        v.setFechaHoraRespuesta(LocalDateTime.now());
        v.setLatitud(request.getLatitud());
        v.setLongitud(request.getLongitud());
        return toVerifDto(verificacionDao.save(v));
    }

    // ════════════════════════════════════════════════════════════════════════
    //  ASISTENCIA
    // ════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<RegistroAsistenciaDto> findAsistenciaByJornada(Long idJornada) {
        return registroDao.findByJornada(idJornada)
            .stream().map(this::toRegistroDto).toList();
    }

    @Override
    @Transactional
    public RegistroAsistenciaDto registrarEntrada(RegistrarMovimientoRequest request) {
        validarMovimientoRequest(request);
        RegistroAsistencia reg = obtenerOCrearRegistro(request);

        if (reg.tieneEntrada())
            throw new IllegalStateException("La entrada ya fue registrada");

        LocalTime ahora = LocalTime.now();
        reg.setHoraEntradaReal(ahora);
        reg.setMetodoRegistroEntrada(request.getMetodo() != null ? request.getMetodo() : MetodoRegistro.MANUAL);
        reg.setLatitudEntrada(request.getLatitud());
        reg.setLongitudEntrada(request.getLongitud());
        if (request.getObservaciones() != null) reg.setObservaciones(request.getObservaciones());

        // Calcular estado de entrada
        if (reg.getHoraEntradaEsperada() != null) {
            long tardanza = java.time.Duration.between(reg.getHoraEntradaEsperada(), ahora).toMinutes();
            reg.setEstadoEntrada(tardanza > 5 ? EstadoEntrada.TARDANZA : EstadoEntrada.A_TIEMPO);
        }
        return toRegistroDto(registroDao.save(reg));
    }

    @Override
    @Transactional
    public RegistroAsistenciaDto registrarSalida(RegistrarMovimientoRequest request) {
        validarMovimientoRequest(request);
        RegistroAsistencia reg = obtenerOCrearRegistro(request);

        if (!reg.tieneEntrada())
            throw new IllegalStateException("No se puede registrar salida sin entrada previa");
        if (reg.tieneSalida())
            throw new IllegalStateException("La salida ya fue registrada");

        LocalTime ahora = LocalTime.now();
        reg.setHoraSalidaReal(ahora);
        reg.setMetodoRegistroSalida(request.getMetodo() != null ? request.getMetodo() : MetodoRegistro.MANUAL);
        reg.setLatitudSalida(request.getLatitud());
        reg.setLongitudSalida(request.getLongitud());

        // Calcular estado de salida
        if (reg.getHoraSalidaEsperada() != null) {
            long diff = java.time.Duration.between(ahora, reg.getHoraSalidaEsperada()).toMinutes();
            reg.setEstadoSalida(diff > 10 ? EstadoSalida.SALIDA_ANTICIPADA : EstadoSalida.COMPLETO);
        } else {
            reg.setEstadoSalida(EstadoSalida.COMPLETO);
        }
        return toRegistroDto(registroDao.save(reg));
    }

    @Override
    @Transactional
    public void inicializarRegistrosJornada(Long idJornada) {
        Jornada jornada = jornadaDao.findById(idJornada)
            .orElseThrow(() -> new EntityNotFoundException("Jornada no encontrada: " + idJornada));

        // Crear registros para internos
        internoJornadaDao.findByJornada_IdJornada(idJornada).forEach(ij -> {
            if (registroDao.findByInternoJornada_IdInternoJornada(ij.getIdInternoJornada()).isEmpty()) {
                registroDao.save(RegistroAsistencia.builder()
                    .internoJornada(ij)
                    .horaEntradaEsperada(entradaEsperada(ij))
                    .horaSalidaEsperada(salidaEsperada(ij))
                    .estadoEntrada(EstadoEntrada.AUSENTE)
                    .estadoSalida(EstadoSalida.NO_REGISTRADA)
                    .build());
            }
        });

        // Crear registros para titulares
        titularJornadaDao.findByJornada_IdJornada(idJornada).forEach(tj -> {
            if (registroDao.findByTitularJornada_IdTitularJornada(tj.getIdTitularJornada()).isEmpty()) {
                registroDao.save(RegistroAsistencia.builder()
                    .titularJornada(tj)
                    .horaEntradaEsperada(entradaEsperada(tj))
                    .horaSalidaEsperada(salidaEsperada(tj))
                    .estadoEntrada(EstadoEntrada.AUSENTE)
                    .estadoSalida(EstadoSalida.NO_REGISTRADA)
                    .build());
            }
        });
    }

    // ── Horario esperado de una asignación ──────────────────────────────────
    // La jornada es un contenedor: su horario es el valor por defecto, no el de
    // cada persona. Guardar el general como hora esperada marcaba TARDANZA a
    // quien entra más tarde que el turno y SALIDA_ANTICIPADA a quien sale antes,
    // aunque ambos cumplieran su horario propio al minuto.

    /** Hora a la que el titular debe fichar entrada: el arranque de su primera franja. */
    private LocalTime entradaEsperada(TitularJornada tj) {
        LocalTime ini = tj.isTrabajaManana() ? tj.inicioManana() : tj.inicioTarde();
        return ini != null ? ini : tj.inicioManana();
    }

    /** Hora a la que el titular debe fichar salida: el cierre de su última franja. */
    private LocalTime salidaEsperada(TitularJornada tj) {
        LocalTime fin = tj.esPartida() && tj.isTrabajaTarde() ? tj.finTarde() : tj.finManana();
        return fin != null ? fin : tj.finManana();
    }

    private LocalTime entradaEsperada(InternoJornada ij) {
        return ij.getHoraInicioOverride() != null
            ? ij.getHoraInicioOverride() : ij.getJornada().getHoraInicio();
    }

    private LocalTime salidaEsperada(InternoJornada ij) {
        return ij.getHoraFinOverride() != null
            ? ij.getHoraFinOverride() : ij.getJornada().getHoraFin();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  REPORTES
    // ════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public ResumenRondaDto resumenRonda(Long idRonda) {
        RondaVerificacion ronda = findRondaOrThrow(idRonda);
        List<VerificacionPresencia> lista = verificacionDao.findByRondaConDetalle(idRonda);

        Map<EstadoVerificacion, Long> conteos = lista.stream()
            .collect(Collectors.groupingBy(VerificacionPresencia::getEstadoVerificacion, Collectors.counting()));

        int total = lista.size();
        long pres = conteos.getOrDefault(EstadoVerificacion.PRESENTE, 0L);

        return ResumenRondaDto.builder()
            .idRondaVerificacion(idRonda)
            .nombre(ronda.getNombre())
            .estado(ronda.getEstado())
            .fechaHoraInicio(ronda.getFechaHoraInicio())
            .total(total)
            .presentes((int) pres)
            .ausentes(conteos.getOrDefault(EstadoVerificacion.AUSENTE, 0L).intValue())
            .sinRespuesta(conteos.getOrDefault(EstadoVerificacion.SIN_RESPUESTA, 0L).intValue())
            .pendientes(conteos.getOrDefault(EstadoVerificacion.PENDIENTE, 0L).intValue())
            .porcentajePresencia(total == 0 ? 0 : Math.round((pres * 100.0 / total) * 10) / 10.0)
            .verificaciones(lista.stream().map(this::toVerifDto).toList())
            .build();
    }

    @Override
    @Transactional(readOnly = true)
    public EstadisticasMonitoreoDto estadisticasGlobales() {
        List<Object[]> byEstado = verificacionDao.countByEstadoForRonda(0L); // placeholder
        // Estadísticas simples desde DAOs
        long totalRondas = rondaDao.count();
        long totalVerif  = verificacionDao.count();

        // todo: queries más específicas para producción
        return EstadisticasMonitoreoDto.builder()
            .totalRondas(totalRondas)
            .verificacionesTotales(totalVerif)
            .build();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  HELPERS PRIVADOS
    // ════════════════════════════════════════════════════════════════════════

    private void generarVerificaciones(RondaVerificacion ronda, Long idJornada) {
        List<Jornada> jornadas = idJornada != null
            ? jornadaDao.findById(idJornada).map(List::of).orElse(List.of())
            : jornadaDao.findByEstadoOrderByFechaInicioDesc(EstadoJornada.ACTIVA);

        LocalDateTime ahora = LocalDateTime.now();
        List<VerificacionPresencia> verificaciones = new ArrayList<>();

        for (Jornada j : jornadas) {
            titularJornadaDao.findByJornada_IdJornada(j.getIdJornada()).forEach(tj ->
                verificaciones.add(VerificacionPresencia.builder()
                    .rondaVerificacion(ronda)
                    .titular(tj.getTitular())
                    .estadoVerificacion(EstadoVerificacion.PENDIENTE)
                    .fechaHoraEnvio(ahora)
                    .build()));

            internoJornadaDao.findByJornada_IdJornada(j.getIdJornada()).forEach(ij ->
                verificaciones.add(VerificacionPresencia.builder()
                    .rondaVerificacion(ronda)
                    .interno(ij.getInterno())
                    .estadoVerificacion(EstadoVerificacion.PENDIENTE)
                    .fechaHoraEnvio(ahora)
                    .build()));
        }
        verificacionDao.saveAll(verificaciones);
        log.info("Ronda {}: {} verificaciones generadas", ronda.getIdRondaVerificacion(), verificaciones.size());
    }

    /** Fórmula de Haversine para distancia en metros entre dos coordenadas GPS. */
    private double calcularDistanciaMetros(double lat1, double lng1, double lat2, double lng2) {
        final int R = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private void validarGeocercaPersonal(VerificacionPresencia v,
                                          BigDecimal lat, BigDecimal lng) {
        // En el futuro: buscar el Lugar del titular/interno en la jornada
        // Por ahora: sin validación automática de geocerca en verif manual
        log.debug("GPS verificación {}: {}, {}", v.getIdVerificacionPresencia(), lat, lng);
    }

    private void validarMovimientoRequest(RegistrarMovimientoRequest req) {
        boolean tieneInterno  = req.getIdInternoJornada()  != null;
        boolean tieneTitular  = req.getIdTitularJornada()  != null;
        if (tieneInterno == tieneTitular)
            throw new IllegalArgumentException("Especificar exactamente uno: idInternoJornada o idTitularJornada");
    }

    private RegistroAsistencia obtenerOCrearRegistro(RegistrarMovimientoRequest req) {
    
        if (req.getIdInternoJornada() != null) {
            // 1. Buscamos el registro
            Optional<RegistroAsistencia> optRegistro = registroDao.findByInternoJornada_IdInternoJornada(req.getIdInternoJornada());
            
            // 2. Si existe, lo devolvemos inmediatamente
            if (optRegistro.isPresent()) {
                return optRegistro.get();
            }

            // 3. Si no existe, buscamos el InternoJornada (Aislado, sin confundir al compilador)
            InternoJornada ij = internoJornadaDao.findById(req.getIdInternoJornada())
                    .orElseThrow(() -> new EntityNotFoundException("InternoJornada no encontrada: " + req.getIdInternoJornada()));

            // 4. Creamos y guardamos
            return registroDao.save(RegistroAsistencia.builder()
                    .internoJornada(ij)
                    .horaEntradaEsperada(entradaEsperada(ij))
                    .horaSalidaEsperada(salidaEsperada(ij))
                    .estadoEntrada(EstadoEntrada.AUSENTE)
                    .estadoSalida(EstadoSalida.NO_REGISTRADA)
                    .build());

        } else {
            // --- LO MISMO PARA EL TITULAR ---
            Optional<RegistroAsistencia> optRegistro = registroDao.findByTitularJornada_IdTitularJornada(req.getIdTitularJornada());
            
            if (optRegistro.isPresent()) {
                return optRegistro.get();
            }

            TitularJornada tj = titularJornadaDao.findById(req.getIdTitularJornada())
                    .orElseThrow(() -> new EntityNotFoundException("TitularJornada no encontrada: " + req.getIdTitularJornada()));

            return registroDao.save(RegistroAsistencia.builder()
                    .titularJornada(tj)
                    .horaEntradaEsperada(entradaEsperada(tj))
                    .horaSalidaEsperada(salidaEsperada(tj))
                    .estadoEntrada(EstadoEntrada.AUSENTE)
                    .estadoSalida(EstadoSalida.NO_REGISTRADA)
                    .build());
        }
    }

    private Lugar findLugarOrThrow(Long id) {
        return lugarDao.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Lugar no encontrado: " + id));
    }

    private RondaVerificacion findRondaOrThrow(Long id) {
        return rondaDao.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Ronda no encontrada: " + id));
    }

    // ── Mappers ───────────────────────────────────────────────────────────

    private CodigoQrDto toQrDto(CodigoQr q) {
        return CodigoQrDto.builder()
            .idCodigoQr(q.getIdCodigoQr())
            .idLugar(q.getLugar().getIdLugar())
            .nombreLugar(q.getLugar().getNombre())
            .descripcionLugar(q.getLugar().getDescripcion())
            .codigo(q.getCodigo())
            .activo(q.isActivo())
            .esValido(q.esValido())
            .fechaExpiracion(q.getFechaExpiracion())
            .imagenUrl(q.getImagenUrl())
            .fechaCreacion(q.getFechaCreacion())
            .build();
    }

    private RondaVerificacionDto toRondaDto(RondaVerificacion r) {
        long total    = titularJornadaDao.countByJornada_IdJornada(0L); // se usa list size
        long totalV   = r.getVerificaciones().size();
        long presentes = r.getTotalPresentes();
        return RondaVerificacionDto.builder()
            .idRondaVerificacion(r.getIdRondaVerificacion())
            .nombre(r.getNombre())
            .tipoRonda(r.getTipoRonda())
            .estado(r.getEstado())
            .fechaHoraInicio(r.getFechaHoraInicio())
            .fechaHoraFin(r.getFechaHoraFin())
            .timeoutMinutos(r.getTimeoutMinutos())
            .observaciones(r.getObservaciones())
            .totalVerificaciones((int) totalV)
            .totalPresentes((int) presentes)
            .totalSinRespuesta((int) r.getTotalSinRespuesta())
            .totalPendientes((int) r.getVerificaciones().stream()
                .filter(v -> v.getEstadoVerificacion() == EstadoVerificacion.PENDIENTE).count())
            .porcentajePresencia(totalV == 0 ? 0 : Math.round((presentes * 100.0 / totalV) * 10) / 10.0)
            .fechaCreacion(r.getFechaCreacion())
            .build();
    }

    private VerificacionPresenciaDto toVerifDto(VerificacionPresencia v) {
        return VerificacionPresenciaDto.builder()
            .idVerificacionPresencia(v.getIdVerificacionPresencia())
            .idRondaVerificacion(v.getRondaVerificacion().getIdRondaVerificacion())
            .tipoPersonal(v.getTipoPersonal())
            .idPersona(v.getTitular() != null
                ? v.getTitular().getIdTitular()
                : v.getInterno() != null ? v.getInterno().getIdInterno() : null)
            .nombrePersonal(v.getNombrePersonal())
            .estadoVerificacion(v.getEstadoVerificacion())
            .metodoVerificacion(v.getMetodoVerificacion())
            .fechaHoraEnvio(v.getFechaHoraEnvio())
            .fechaHoraRespuesta(v.getFechaHoraRespuesta())
            .tiempoRespuestaMinutos(v.getTiempoRespuestaMinutos())
            .latitud(v.getLatitud())
            .longitud(v.getLongitud())
            .fotoUrl(v.getFotoUrl())
            .observaciones(v.getObservaciones())
            .build();
    }

    private RegistroAsistenciaDto toRegistroDto(RegistroAsistencia ra) {
        String tipo; Long idPersona; String nombre; Long idJornada; String nombreJornada;

        if (ra.esDeInterno()) {
            Persona p = ra.getInternoJornada().getInterno().getPersona();
            tipo = "INTERNO";
            idPersona = ra.getInternoJornada().getInterno().getIdInterno();
            nombre = p.getApellido() + " " + p.getNombre();
            idJornada = ra.getInternoJornada().getJornada().getIdJornada();
            nombreJornada = ra.getInternoJornada().getJornada().getNombre();
        } else {
            Persona p = ra.getTitularJornada().getTitular().getPersona();
            tipo = "TITULAR";
            idPersona = ra.getTitularJornada().getTitular().getIdTitular();
            nombre = p.getApellido() + " " + p.getNombre();
            idJornada = ra.getTitularJornada().getJornada().getIdJornada();
            nombreJornada = ra.getTitularJornada().getJornada().getNombre();
        }

        return RegistroAsistenciaDto.builder()
            .idRegistroAsistencia(ra.getIdRegistroAsistencia())
            .tipoPersonal(tipo)
            .idPersona(idPersona)
            .nombrePersonal(nombre)
            .idJornada(idJornada)
            .nombreJornada(nombreJornada)
            .horaEntradaEsperada(ra.getHoraEntradaEsperada())
            .horaSalidaEsperada(ra.getHoraSalidaEsperada())
            .horaEntradaReal(ra.getHoraEntradaReal())
            .horaSalidaReal(ra.getHoraSalidaReal())
            .estadoEntrada(ra.getEstadoEntrada())
            .estadoSalida(ra.getEstadoSalida())
            .metodoEntrada(ra.getMetodoRegistroEntrada())
            .metodoSalida(ra.getMetodoRegistroSalida())
            .minutosTardanza(ra.getMinutosTardanza())
            .tieneEntrada(ra.tieneEntrada())
            .tieneSalida(ra.tieneSalida())
            .observaciones(ra.getObservaciones())
            .build();
    }

}
