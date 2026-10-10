package com.uap.controlMedico.controller.api;

import java.security.Principal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;
import com.uap.controlMedico.model.service.monitoreo.WebSocketPresenceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.bind.annotation.PutMapping;

import com.uap.controlMedico.model.dao.ConfiguracionMonitoreoDao;
import com.uap.controlMedico.model.dao.EventoAbandonoDao;
import com.uap.controlMedico.model.dao.SesionMonitoreoDao;
import com.uap.controlMedico.model.dao.AusenciaNoJustificadaDao;
import com.uap.controlMedico.model.dao.UsuarioDao;
import com.uap.controlMedico.model.dto.monitoreo.CodigoQrDto;
import com.uap.controlMedico.model.dto.monitoreo.ConfirmarPresenciaRequest;
import com.uap.controlMedico.model.dto.monitoreo.ConfiguracionMonitoreoDto;
import com.uap.controlMedico.model.dto.monitoreo.EscanearQrRequest;
import com.uap.controlMedico.model.dto.monitoreo.EstadisticasMonitoreoDto;
import com.uap.controlMedico.model.dto.monitoreo.EventoAbandonoDto;
import com.uap.controlMedico.model.dto.monitoreo.IncidenciaPersonalDto;
import com.uap.controlMedico.model.dto.monitoreo.IncidenciaRevisionDto;
import com.uap.controlMedico.model.dto.monitoreo.LoteUbicacionResponse;
import com.uap.controlMedico.model.dto.monitoreo.PersonalActivoMapaDto;
import com.uap.controlMedico.model.dto.monitoreo.PersonalEnTurnoDto;
import com.uap.controlMedico.model.dto.monitoreo.RegistrarMovimientoRequest;
import com.uap.controlMedico.model.dto.monitoreo.RegistroAsistenciaDto;
import com.uap.controlMedico.model.dto.monitoreo.ResumenRondaDto;
import com.uap.controlMedico.model.dto.monitoreo.RondaVerificacionDto;
import com.uap.controlMedico.model.dto.monitoreo.RondaVerificacionRequest;
import com.uap.controlMedico.model.dto.monitoreo.SesionMonitoreoDto;
import com.uap.controlMedico.model.dto.monitoreo.UbicacionRequest;
import com.uap.controlMedico.model.dto.monitoreo.UbicacionResponse;
import com.uap.controlMedico.model.dto.bitacoraDto.BitacoraEntradaResponse;
import com.uap.controlMedico.model.service.bitacora.BitacoraService;
import com.uap.controlMedico.model.dto.monitoreo.VerificacionPresenciaDto;
import com.uap.controlMedico.model.dto.response.ApiResponse;
import com.uap.controlMedico.model.entity.ConfiguracionMonitoreo;
import com.uap.controlMedico.model.entity.EventoAbandono;
import com.uap.controlMedico.model.entity.SesionMonitoreo;
import com.uap.controlMedico.model.entity.AusenciaNoJustificada;
import com.uap.controlMedico.model.enums.EstadoEvento;
import com.uap.controlMedico.model.enums.EstadoRonda;
import com.uap.controlMedico.model.enums.EstadoZona;
import com.uap.controlMedico.model.dto.permisosDto.AccionConObservacionRequest;
import com.uap.controlMedico.model.service.MonitoreoService;
import com.uap.controlMedico.model.service.monitoreo.JustificacionService;
import com.uap.controlMedico.model.service.monitoreo.UbicacionService;
import com.uap.controlMedico.model.service.rolturno.RolTurnoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/monitoreo")
@RequiredArgsConstructor
public class MonitoreoApiController {

    private final MonitoreoService          monitoreoService;
    private final SesionMonitoreoDao        sesionDao;
    private final EventoAbandonoDao         eventoDao;
    private final AusenciaNoJustificadaDao  ausenciaDao;
    private final ConfiguracionMonitoreoDao configDao;
    private final RolTurnoService           rolTurnoService;
    private final WebSocketPresenceService  presenceService;
    private final UsuarioDao                usuarioDao;
    private final UbicacionService          ubicacionService;
    private final JustificacionService      justificacionService;
    private final BitacoraService           bitacoraService;

    // ── Códigos QR ────────────────────────────────────────────────────────

    @GetMapping("/codigos-qr")
    public ResponseEntity<ApiResponse<List<CodigoQrDto>>> listarQrs() {
        return ResponseEntity.ok(ApiResponse.ok(monitoreoService.listarQrs()));
    }

    @GetMapping("/codigos-qr/lugar/{idLugar}")
    public ResponseEntity<ApiResponse<CodigoQrDto>> qrPorLugar(@PathVariable Long idLugar) {
        return ResponseEntity.ok(ApiResponse.ok(monitoreoService.findQrByLugar(idLugar)));
    }

    @PostMapping("/codigos-qr/lugar/{idLugar}")
    public ResponseEntity<ApiResponse<CodigoQrDto>> crearQr(@PathVariable Long idLugar) {
        return ResponseEntity.ok(ApiResponse.ok(monitoreoService.crearQrParaLugar(idLugar)));
    }

    @PostMapping("/codigos-qr/lugar/{idLugar}/regenerar")
    public ResponseEntity<ApiResponse<CodigoQrDto>> regenerarQr(@PathVariable Long idLugar) {
        return ResponseEntity.ok(ApiResponse.ok(monitoreoService.regenerarQr(idLugar)));
    }

    @PostMapping("/codigos-qr/lugar/{idLugar}/toggle")
    public ResponseEntity<ApiResponse<CodigoQrDto>> toggleQr(@PathVariable Long idLugar) {
        return ResponseEntity.ok(ApiResponse.ok(monitoreoService.toggleQr(idLugar)));
    }

    // ── Rondas ────────────────────────────────────────────────────────────

    @GetMapping("/rondas")
    public ResponseEntity<ApiResponse<Page<RondaVerificacionDto>>> listarRondas(
        @RequestParam(defaultValue = "") String q,
        @RequestParam(required = false)  EstadoRonda estado,
        @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
        @PageableDefault(size = 20, sort = "fechaHoraInicio", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            monitoreoService.listarRondas(q, estado, fecha, pageable)));
    }

    @GetMapping("/rondas/{id}")
    public ResponseEntity<ApiResponse<RondaVerificacionDto>> findRonda(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(monitoreoService.findRondaById(id)));
    }

    @PostMapping("/rondas")
    public ResponseEntity<ApiResponse<RondaVerificacionDto>> crearRonda(
        @Valid @RequestBody RondaVerificacionRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(monitoreoService.crearRonda(request)));
    }

    @PostMapping("/rondas/{id}/iniciar")
    public ResponseEntity<ApiResponse<RondaVerificacionDto>> iniciarRonda(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(monitoreoService.iniciarRonda(id)));
    }

    @PostMapping("/rondas/{id}/cerrar")
    public ResponseEntity<ApiResponse<RondaVerificacionDto>> cerrarRonda(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(monitoreoService.cerrarRonda(id)));
    }

    @PostMapping("/rondas/{id}/cancelar")
    public ResponseEntity<ApiResponse<RondaVerificacionDto>> cancelarRonda(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(monitoreoService.cancelarRonda(id)));
    }

    // ── Verificaciones ────────────────────────────────────────────────────

    @GetMapping("/rondas/{id}/verificaciones")
    public ResponseEntity<ApiResponse<List<VerificacionPresenciaDto>>> listarVerificaciones(
        @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.ok(monitoreoService.findVerificacionesByRonda(id)));
    }

    @PostMapping("/verificaciones/{id}/confirmar")
    public ResponseEntity<ApiResponse<VerificacionPresenciaDto>> confirmar(
        @PathVariable Long id,
        @Valid @RequestBody ConfirmarPresenciaRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            monitoreoService.confirmarPresencia(id, request)));
    }

    @PostMapping("/verificaciones/{id}/ausente")
    public ResponseEntity<ApiResponse<VerificacionPresenciaDto>> marcarAusente(
        @PathVariable Long id,
        @RequestParam(required = false) String observaciones
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            monitoreoService.marcarAusente(id, observaciones)));
    }

    @PostMapping("/verificaciones/escanear-qr")
    public ResponseEntity<ApiResponse<VerificacionPresenciaDto>> escanearQr(
        @Valid @RequestBody EscanearQrRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(monitoreoService.escanearQr(request)));
    }

    // ── Asistencia ────────────────────────────────────────────────────────

    @GetMapping("/asistencia/jornada/{idJornada}")
    public ResponseEntity<ApiResponse<List<RegistroAsistenciaDto>>> asistenciaJornada(
        @PathVariable Long idJornada
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            monitoreoService.findAsistenciaByJornada(idJornada)));
    }

    @PostMapping("/asistencia/entrada")
    public ResponseEntity<ApiResponse<RegistroAsistenciaDto>> registrarEntrada(
        @RequestBody RegistrarMovimientoRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(monitoreoService.registrarEntrada(request)));
    }

    @PostMapping("/asistencia/salida")
    public ResponseEntity<ApiResponse<RegistroAsistenciaDto>> registrarSalida(
        @RequestBody RegistrarMovimientoRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(monitoreoService.registrarSalida(request)));
    }

    @PostMapping("/asistencia/inicializar-jornada/{idJornada}")
    public ResponseEntity<ApiResponse<Void>> inicializarJornada(@PathVariable Long idJornada) {
        monitoreoService.inicializarRegistrosJornada(idJornada);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ── Reportes ──────────────────────────────────────────────────────────

    @GetMapping("/reportes/resumen-ronda/{idRonda}")
    public ResponseEntity<ApiResponse<ResumenRondaDto>> resumenRonda(@PathVariable Long idRonda) {
        return ResponseEntity.ok(ApiResponse.ok(monitoreoService.resumenRonda(idRonda)));
    }

    @GetMapping("/reportes/estadisticas")
    public ResponseEntity<ApiResponse<EstadisticasMonitoreoDto>> estadisticas() {
        return ResponseEntity.ok(ApiResponse.ok(monitoreoService.estadisticasGlobales()));
    }

    // ── Monitoreo Continuo ────────────────────────────────────────────────

    /**
     * Recibe coordenadas GPS vía HTTP (espejo del canal STOMP /app/gps/location).
     * Existe porque el WebSocket se cae cuando la app móvil queda en segundo plano:
     * el plugin de geolocalización en background sigue entregando fixes y los
     * envía por aquí, garantizando que el monitoreo no se corte con la app minimizada.
     * Reutiliza el mismo UbicacionService → misma lógica de sesión/geofence/alertas.
     */
    @PostMapping("/ubicacion")
    public ResponseEntity<ApiResponse<UbicacionResponse>> registrarUbicacion(
        @RequestBody UbicacionRequest request,
        Principal principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            ubicacionService.registrarUbicacion(principal.getName(), request)));
    }

    /**
     * Store-and-forward: recibe un LOTE de ubicaciones que el dispositivo bufferizó
     * mientras estuvo sin internet y sincroniza al recuperar conexión. Las reproduce
     * en orden cronológico con idempotencia por clientEventId; un fix DENTRO posterior
     * revierte automáticamente un abandono provisional (reconciliación).
     */
    @PostMapping("/ubicaciones/lote")
    public ResponseEntity<ApiResponse<LoteUbicacionResponse>> registrarUbicacionesLote(
        @RequestBody List<UbicacionRequest> fixes,
        Principal principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            ubicacionService.procesarLote(principal.getName(), fixes)));
    }

    @GetMapping("/sesiones/activas")
    public ResponseEntity<ApiResponse<List<SesionMonitoreoDto>>> sesionesActivas() {
        List<SesionMonitoreoDto> result = sesionDao.findActivasConDetalle()
            .stream().map(this::toSesionDto).toList();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/mapa/personal-activo")
    public ResponseEntity<ApiResponse<List<PersonalActivoMapaDto>>> personalActivoMapa() {
        List<PersonalActivoMapaDto> result = sesionDao.findActivasConDetalle()
            .stream().map(this::toMapaDto).toList();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/alertas")
    public ResponseEntity<ApiResponse<List<EventoAbandonoDto>>> alertasActivas() {
        List<EventoAbandonoDto> result = eventoDao.findAlertasActivas()
            .stream().map(this::toEventoDto).toList();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/alertas/{id}/resolver")
    public ResponseEntity<ApiResponse<EventoAbandonoDto>> resolverAlerta(
        @PathVariable Long id,
        @RequestParam(required = false) String observaciones
    ) {
        EventoAbandono evt = eventoDao.findById(id)
            .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("EventoAbandono id=" + id));
        evt.setResuelto(true);
        evt.setEstado(EstadoEvento.RESUELTO);
        if (observaciones != null) evt.setObservaciones(observaciones);
        return ResponseEntity.ok(ApiResponse.ok(toEventoDto(eventoDao.save(evt))));
    }

    // ── Historial de incidencias por usuario (ficha del admin) ─────────────

    /** Incidencias (abandonos, atrasos, faltas…) de un titular/interno, recientes primero. */
    @GetMapping("/personal/incidencias")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ApiResponse<List<IncidenciaPersonalDto>>> incidenciasPersonal(
        @RequestParam String tipo,
        @RequestParam Long id
    ) {
        List<EventoAbandono> eventos = "INTERNO".equalsIgnoreCase(tipo)
            ? eventoDao.findByInternoId(id)
            : eventoDao.findByTitularId(id);
        List<IncidenciaPersonalDto> data = eventos.stream()
            .map(MonitoreoApiController::toIncidenciaDto).toList();
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    private static IncidenciaPersonalDto toIncidenciaDto(EventoAbandono e) {
        return IncidenciaPersonalDto.builder()
            .idEvento(e.getIdEvento())
            .tipo(e.getTipoEfectivo().name())
            .tipoLabel(e.getTipoEfectivo().getLabel())
            .estado(e.getEstado().name())
            .estadoJustificacion(e.getEstadoJustificacionEfectivo().name())
            .estadoJustificacionLabel(e.getEstadoJustificacionEfectivo().getLabel())
            .nombreLugar(e.getSesion() != null && e.getSesion().getLugar() != null
                ? e.getSesion().getLugar().getNombre() : null)
            .timestampSalida(e.getTimestampSalida() != null ? e.getTimestampSalida().toString() : null)
            .timestampRetorno(e.getTimestampRetorno() != null ? e.getTimestampRetorno().toString() : null)
            .tiempoFueraMinutos(e.getTiempoFueraMinutos())
            .minutosIncidencia(e.getMinutosIncidencia())
            .observaciones(e.getObservaciones())
            .build();
    }

    // ── Justificación de incidencias ──────────────────────────────────────
    // El funcionario justifica una incidencia propia (motivo + adjunto opcional);
    // el admin/supervisor aprueba o rechaza. Refleja el flujo de los permisos.

    /** Funcionario: envía la justificación de una incidencia propia. */
    @PostMapping("/incidencias/{id}/justificar")
    @PreAuthorize("hasAnyRole('MEDICO','ENFERMERO','CAJERO','INTERNO','FARMACIA','LIMPIEZA')")
    public ResponseEntity<ApiResponse<Void>> justificarIncidencia(
        @PathVariable Long id,
        @RequestParam("motivo") String motivo,
        @RequestParam(value = "justificante", required = false) MultipartFile justificante,
        Principal principal
    ) {
        justificacionService.solicitarJustificacion(id, motivo, justificante, principal.getName());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /** Admin/supervisor: incidencias con justificación enviada, pendientes de revisión. */
    @GetMapping("/incidencias/pendientes-justificacion")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ApiResponse<List<IncidenciaRevisionDto>>> incidenciasPendientesJustificacion() {
        return ResponseEntity.ok(ApiResponse.ok(justificacionService.listarPendientesRevision()));
    }

    /** Admin/supervisor: aprueba la justificación (la incidencia queda justificada). */
    @PostMapping("/incidencias/{id}/justificacion/aprobar")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ApiResponse<Void>> aprobarJustificacion(
        @PathVariable Long id,
        @RequestBody(required = false) AccionConObservacionRequest request,
        Principal principal
    ) {
        justificacionService.aprobarJustificacion(id,
            request != null ? request.getObservacion() : null, principal.getName());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /** Admin/supervisor: rechaza la justificación (la incidencia queda firme). */
    @PostMapping("/incidencias/{id}/justificacion/rechazar")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ApiResponse<Void>> rechazarJustificacion(
        @PathVariable Long id,
        @RequestBody(required = false) AccionConObservacionRequest request,
        Principal principal
    ) {
        justificacionService.rechazarJustificacion(id,
            request != null ? request.getObservacion() : null, principal.getName());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/configuracion")
    public ResponseEntity<ApiResponse<ConfiguracionMonitoreoDto>> getConfiguracion() {
        ConfiguracionMonitoreo c = configDao.findById(ConfiguracionMonitoreo.ID)
            .orElseGet(() -> configDao.save(
                ConfiguracionMonitoreo.builder().id(ConfiguracionMonitoreo.ID).build()));
        return ResponseEntity.ok(ApiResponse.ok(toConfigDto(c)));
    }

    @PutMapping("/configuracion")
    public ResponseEntity<ApiResponse<ConfiguracionMonitoreoDto>> updateConfiguracion(
        @RequestBody ConfiguracionMonitoreoDto dto
    ) {
        ConfiguracionMonitoreo c = configDao.findById(ConfiguracionMonitoreo.ID)
            .orElse(ConfiguracionMonitoreo.builder().id(ConfiguracionMonitoreo.ID).build());
        if (dto.getTimeoutAbandonoMinutos()    != null) c.setTimeoutAbandonoMinutos(dto.getTimeoutAbandonoMinutos());
        if (dto.getTimeoutSinSenalMinutos()    != null) c.setTimeoutSinSenalMinutos(dto.getTimeoutSinSenalMinutos());
        if (dto.getToleranciaAtrasoMin()       != null) c.setToleranciaAtrasoMin(dto.getToleranciaAtrasoMin());
        if (dto.getUmbralFaltaMin()            != null) c.setUmbralFaltaMin(dto.getUmbralFaltaMin());
        if (dto.getIntervaloGpsActivoSeg()     != null) c.setIntervaloGpsActivoSeg(dto.getIntervaloGpsActivoSeg());
        if (dto.getIntervaloGpsFueraZonaSeg()  != null) c.setIntervaloGpsFueraZonaSeg(dto.getIntervaloGpsFueraZonaSeg());
        if (dto.getDeltaMinMovimientoMetros()  != null) c.setDeltaMinMovimientoMetros(dto.getDeltaMinMovimientoMetros());
        if (dto.getRadioAlertaExtraMetros()    != null) c.setRadioAlertaExtraMetros(dto.getRadioAlertaExtraMetros());
        if (dto.getConfirmacionFueraSeg()     != null) c.setConfirmacionFueraSeg(dto.getConfirmacionFueraSeg());
        if (dto.getPrecisionMaxConfiableMetros() != null) c.setPrecisionMaxConfiableMetros(dto.getPrecisionMaxConfiableMetros());
        return ResponseEntity.ok(ApiResponse.ok(toConfigDto(configDao.save(c))));
    }

    @GetMapping("/personal/en-turno-ahora")
    public ResponseEntity<ApiResponse<List<PersonalEnTurnoDto>>> personalEnTurnoAhora() {
        return ResponseEntity.ok(ApiResponse.ok(rolTurnoService.getPersonalEnTurnoAhora()));
    }

    /**
     * Usuarios actualmente conectados vía WebSocket (STOMP).
     * Cruza las sesiones STOMP activas con las sesiones GPS activas.
     * Útil para el admin: saber quién tiene la app abierta ahora mismo.
     */
    @GetMapping("/usuarios-conectados")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> usuariosConectados() {
        // Mapa username → sesión GPS activa (puede ser null si no envió GPS aún)
        Map<String, SesionMonitoreo> sesionesPorUsername = sesionDao.findByActivaTrue()
            .stream()
            .filter(s -> s.getTitular() != null || s.getInterno() != null)
            .collect(Collectors.toMap(
                s -> resolverUsername(s),
                s -> s,
                (a, b) -> a
            ));

        List<Map<String, Object>> resultado = new ArrayList<>();
        for (String username : presenceService.getConnectedUsernames()) {
            SesionMonitoreo sesion = sesionesPorUsername.get(username);

            String nombre = usuarioDao.findByUsername(username)
                .map(u -> {
                    String n = u.getNombre();
                    String a = u.getApellido();
                    if (n != null && a != null) return n + " " + a;
                    if (n != null) return n;
                    return username;
                })
                .orElse(username);

            boolean tieneGps   = sesion != null && sesion.getUltimaActualizacion() != null;
            String  estadoZona = sesion != null && sesion.getEstadoZona() != null
                                 ? sesion.getEstadoZona().name() : "SIN_SESION";
            String  ultimaSen  = tieneGps ? sesion.getUltimaActualizacion().toString() : null;

            // HashMap permite valores null (Map.of() no los permite)
            Map<String, Object> entry = new HashMap<>();
            entry.put("username",  username);
            entry.put("nombre",    nombre);
            entry.put("tieneGps",  tieneGps);
            entry.put("estadoZona", estadoZona);
            entry.put("ultimaSenal", ultimaSen);   // puede ser null
            entry.put("sesiones",  1);
            resultado.add(entry);
        }

        return ResponseEntity.ok(ApiResponse.ok(resultado));
    }

    /** Resuelve el username del propietario de una SesionMonitoreo vía perId (titular) o idPersona (interno). */
    private String resolverUsername(SesionMonitoreo s) {
        Long perId = null;
        if (s.getTitular() != null) {
            perId = s.getTitular().getPerId();
        } else if (s.getInterno() != null && s.getInterno().getPersona() != null) {
            perId = s.getInterno().getPersona().getIdPersona();
        }
        if (perId == null) return "";
        return usuarioDao.findByPerId(perId)
            .map(u -> u.getUsername())
            .orElse("");
    }

    // ── Mappers internos ──────────────────────────────────────────────────

    private SesionMonitoreoDto toSesionDto(SesionMonitoreo s) {
        String nombre = resolverNombre(s);
        String cargo  = resolverCargo(s);
        String tipo   = s.getTitular() != null ? "TITULAR" : "INTERNO";
        Long   idP    = s.getTitular() != null ? s.getTitular().getIdTitular()
                      : (s.getInterno() != null ? s.getInterno().getIdInterno() : null);
        Optional<EventoAbandono> evtAbierto =
            eventoDao.findFirstBySesion_IdSesionAndResueltoFalseOrderByIdEventoDesc(s.getIdSesion());
        boolean tieneEvt = evtAbierto.isPresent();
        Long tiempoFuera = evtAbierto.map(EventoAbandono::getTiempoFueraMinutos).orElse(0L);
        return SesionMonitoreoDto.builder()
            .idSesion(s.getIdSesion())
            .nombrePersonal(nombre)
            .cargo(cargo)
            .tipoPersonal(tipo)
            .idPersonal(idP)
            .idJornada(s.getJornada() != null ? s.getJornada().getIdJornada() : null)
            .nombreJornada(s.getJornada() != null ? s.getJornada().getNombre() : null)
            .idLugar(s.getLugar() != null ? s.getLugar().getIdLugar() : null)
            .nombreLugar(s.getLugar() != null ? s.getLugar().getNombre() : null)
            .estadoZona(s.getEstadoZona())
            .timestampInicio(s.getTimestampInicio() != null ? s.getTimestampInicio().toString() : null)
            .ultimaActualizacion(s.getUltimaActualizacion() != null ? s.getUltimaActualizacion().toString() : null)
            .tiempoFueraMinutos(tiempoFuera)
            .ultimaLatitud(s.getUltimaLatitud())
            .ultimaLongitud(s.getUltimaLongitud())
            .ultimaDistanciaMetros(s.getUltimaDistanciaMetros())
            .tieneEventoAbierto(tieneEvt)
            .build();
    }

    private PersonalActivoMapaDto toMapaDto(SesionMonitoreo s) {
        Long tiempoFuera = eventoDao.findFirstBySesion_IdSesionAndResueltoFalseOrderByIdEventoDesc(s.getIdSesion())
            .map(EventoAbandono::getTiempoFueraMinutos).orElse(0L);
        return PersonalActivoMapaDto.builder()
            .idSesion(s.getIdSesion())
            .nombrePersonal(resolverNombre(s))
            .cargo(resolverCargo(s))
            .tipoPersonal(s.getTitular() != null ? "TITULAR" : "INTERNO")
            .idLugar(s.getLugar() != null ? s.getLugar().getIdLugar() : null)
            .nombreLugar(s.getLugar() != null ? s.getLugar().getNombre() : null)
            .latitudLugar(s.getLugar() != null ? s.getLugar().getLatitudCentro() : null)
            .longitudLugar(s.getLugar() != null ? s.getLugar().getLongitudCentro() : null)
            .radioMetrosLugar(s.getLugar() != null ? s.getLugar().getRadioMetros() : null)
            .latitudPersonal(s.getUltimaLatitud())
            .longitudPersonal(s.getUltimaLongitud())
            .distanciaMetros(s.getUltimaDistanciaMetros())
            .estadoZona(s.getEstadoZona() != null ? s.getEstadoZona() : EstadoZona.DESCONOCIDO)
            .tiempoFueraMinutos(tiempoFuera)
            .ultimaActualizacion(s.getUltimaActualizacion() != null ? s.getUltimaActualizacion().toString() : null)
            .build();
    }

    private EventoAbandonoDto toEventoDto(EventoAbandono e) {
        return EventoAbandonoDto.builder()
            .idEvento(e.getIdEvento())
            .idSesion(e.getSesion() != null ? e.getSesion().getIdSesion() : null)
            .nombrePersonal(e.getNombrePersonal())
            .cargo(e.getSesion() != null ? resolverCargo(e.getSesion()) : null)
            .tipoPersonal(e.getSesion() != null && e.getSesion().getTitular() != null ? "TITULAR" : "INTERNO")
            .idLugar(e.getSesion() != null && e.getSesion().getLugar() != null
                ? e.getSesion().getLugar().getIdLugar() : null)
            .nombreLugar(e.getSesion() != null && e.getSesion().getLugar() != null
                ? e.getSesion().getLugar().getNombre() : null)
            .timestampSalida(e.getTimestampSalida() != null ? e.getTimestampSalida().toString() : null)
            .timestampAbandono(e.getTimestampAbandono() != null ? e.getTimestampAbandono().toString() : null)
            .timestampRetorno(e.getTimestampRetorno() != null ? e.getTimestampRetorno().toString() : null)
            .tiempoFueraMinutos(e.getTiempoFueraMinutos())
            .distanciaAlSalirMetros(e.getDistanciaAlSalirMetros())
            .estado(e.getEstado())
            .notificadoAdmin(e.isNotificadoAdmin())
            .resuelto(e.isResuelto())
            .observaciones(e.getObservaciones())
            .build();
    }

    // ── Datos por jornada (seguimiento por días) ───────────────────────────

    @GetMapping("/sesiones/jornada/{idJornada}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ApiResponse<List<SesionMonitoreoDto>>> sesionesPorJornada(
            @PathVariable Long idJornada) {
        List<SesionMonitoreoDto> result = sesionDao.findByJornadaIdJornadaOrderByTimestampInicioDesc(idJornada)
            .stream().map(this::toSesionDto).toList();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/ausencias/jornada/{idJornada}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> ausenciasPorJornada(
            @PathVariable Long idJornada) {
        List<Map<String, Object>> result = ausenciaDao.findByJornadaId(idJornada)
            .stream().map(a -> {
                Map<String, Object> m = new HashMap<>();
                m.put("idAusencia", a.getIdAusencia());
                m.put("idUsuario", a.getUsuario().getIdUsuario());
                String nombre = "";
                if (a.getUsuario().getPersona() != null) {
                    nombre = a.getUsuario().getPersona().getNombreCompleto();
                }
                m.put("nombrePersonal", nombre);
                m.put("inicioAusencia", a.getInicioAusencia() != null ? a.getInicioAusencia().toString() : null);
                m.put("finAusencia", a.getFinAusencia() != null ? a.getFinAusencia().toString() : null);
                m.put("resuelta", a.isResuelta());
                m.put("registradoPor", a.getRegistradoPor());
                return m;
            }).toList();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/incidencias/jornada/{idJornada}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ApiResponse<List<EventoAbandonoDto>>> incidenciasPorJornada(
            @PathVariable Long idJornada) {
        List<EventoAbandonoDto> result = eventoDao.findByJornadaId(idJornada)
            .stream().map(this::toEventoDto).toList();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // ── Bitácora (admin) ──────────────────────────────────────────────

    @GetMapping("/bitacora/jornada/{idJornada}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ApiResponse<List<BitacoraEntradaResponse>>> bitacoraPorJornada(
            @PathVariable Long idJornada) {
        return ResponseEntity.ok(ApiResponse.ok(bitacoraService.entradasPorJornadaAdmin(idJornada)));
    }

    private ConfiguracionMonitoreoDto toConfigDto(ConfiguracionMonitoreo c) {
        return ConfiguracionMonitoreoDto.builder()
            .timeoutAbandonoMinutos(c.getTimeoutAbandonoMinutos())
            .timeoutSinSenalMinutos(c.getTimeoutSinSenalMinutos())
            .toleranciaAtrasoMin(c.getToleranciaAtrasoMin())
            .umbralFaltaMin(c.getUmbralFaltaMin())
            .intervaloGpsActivoSeg(c.getIntervaloGpsActivoSeg())
            .intervaloGpsFueraZonaSeg(c.getIntervaloGpsFueraZonaSeg())
            .deltaMinMovimientoMetros(c.getDeltaMinMovimientoMetros())
            .radioAlertaExtraMetros(c.getRadioAlertaExtraMetros())
            .confirmacionFueraSeg(c.getConfirmacionFueraSeg())
            .precisionMaxConfiableMetros(c.getPrecisionMaxConfiableMetros())
            .build();
    }

    private String resolverNombre(SesionMonitoreo s) {
        if (s.getTitular() != null && s.getTitular().getPersona() != null)
            return s.getTitular().getPersona().getNombreCompleto();
        if (s.getInterno() != null && s.getInterno().getPersona() != null)
            return s.getInterno().getPersona().getNombreCompleto();
        return "Desconocido";
    }

    private String resolverCargo(SesionMonitoreo s) {
        if (s.getTitular() != null) return s.getTitular().getCargo();
        return "Interno";
    }
}
