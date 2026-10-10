package com.uap.controlMedico.model.service.permiso;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.uap.controlMedico.mapper.PermisosMapper;
import com.uap.controlMedico.model.dao.DelegacionDao;
import com.uap.controlMedico.model.dao.InternoDao;
import com.uap.controlMedico.model.dao.JornadaDao;
import com.uap.controlMedico.model.dao.SolicitudPermisoDao;
import com.uap.controlMedico.model.dao.TitularDao;
import com.uap.controlMedico.model.dao.TitularJornadaDao;
import com.uap.controlMedico.model.dao.UsuarioDao;
import com.uap.controlMedico.model.dto.permisosDto.AccionConObservacionRequest;
import com.uap.controlMedico.model.dto.permisosDto.AprobarPermisoRequest;
import com.uap.controlMedico.model.dto.permisosDto.DelegacionRequest;
import com.uap.controlMedico.model.dto.permisosDto.DelegacionResponse;
import com.uap.controlMedico.model.dto.permisosDto.SolicitudPermisoRequest;
import com.uap.controlMedico.model.dto.permisosDto.SolicitudPermisoResponse;
import com.uap.controlMedico.model.entity.Delegacion;
import com.uap.controlMedico.model.entity.Interno;
import com.uap.controlMedico.model.entity.SolicitudPermiso;
import com.uap.controlMedico.model.entity.Titular;
import com.uap.controlMedico.model.entity.TitularJornada;
import com.uap.controlMedico.model.entity.Usuario;
import com.uap.controlMedico.model.enums.EstadoDelegacion;
import com.uap.controlMedico.model.enums.EstadoSolicitud;
import com.uap.controlMedico.model.enums.TipoPermiso;
import com.uap.controlMedico.model.service.notificacion.NotificacionService;
import com.uap.controlMedico.shared.storage.FileStorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PermisoServiceImpl implements PermisoService {
    
    private final SolicitudPermisoDao solicitudRepo;
    private final DelegacionDao       delegacionRepo;
    private final TitularDao          titularRepo;
    private final TitularJornadaDao   titularJornadaRepo;
    private final JornadaDao          jornadaRepo;
    private final InternoDao          internoRepo;
    private final UsuarioDao          usuarioRepo;
    private final PermisosMapper             mapper;

    private final FileStorageService    fileStorageService;
    private final NotificacionService   notificacionService;

    // ══════════════════════════════════════════════════════════════
    //  SOLICITUDES
    // ══════════════════════════════════════════════════════════════

    @Override
    public Page<SolicitudPermisoResponse> getSolicitudes(
            Long idTitular, Long idJornada,
            EstadoSolicitud estado, TipoPermiso tipoPermiso, Pageable pageable) {
        return solicitudRepo.findWithFilters(idTitular, idJornada, estado, tipoPermiso, pageable)
                            .map(mapper::toSolicitudResponse);
    }

    @Override
    public SolicitudPermisoResponse getSolicitud(Long id) {
        return mapper.toSolicitudResponse(findSolicitudOrThrow(id));
    }

    @Override
    @Transactional
    public SolicitudPermisoResponse crearSolicitud(SolicitudPermisoRequest req) {
        Titular titular = titularRepo.findById(req.getIdTitular())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Titular no encontrado: " + req.getIdTitular()));

        var solicitud = SolicitudPermiso.builder()
            .titular(titular)
            .tipoPermiso(req.getTipoPermiso())
            .motivoSolicitud(req.getMotivoSolicitud())
            .estadoSolicitud(EstadoSolicitud.PENDIENTE)
            .fechaHoraSolicitud(LocalDateTime.now())
            .fechaPermiso(parseDate(req.getFechaPermiso()))
            .horaInicioSolicitada(parseTime(req.getHoraInicioSolicitada()))
            .horaFinSolicitada(parseTime(req.getHoraFinSolicitada()))
            .build();

        if (req.getIdTitularJornada() != null) {
            TitularJornada tj = titularJornadaRepo.findById(req.getIdTitularJornada())
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Asignación de jornada no encontrada"));
            if (!tj.getTitular().getIdTitular().equals(req.getIdTitular())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La asignación no pertenece al titular indicado");
            }
            solicitud.setTitularJornada(tj);
            solicitud.setJornada(tj.getJornada());
        } else if (req.getIdJornada() != null) {
            solicitud.setJornada(jornadaRepo.findById(req.getIdJornada())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Jornada no encontrada")));
        }

        SolicitudPermiso guardada = solicitudRepo.save(solicitud);
        notificacionService.notificarNuevaSolicitud(guardada);

        return mapper.toSolicitudResponse(guardada);
    }

    @Override
    @Transactional
    public SolicitudPermisoResponse modificarSolicitud(Long id, TipoPermiso tipoPermiso,
            String motivoSolicitud, String fechaPermiso, String horaInicio, String horaFin) {

        SolicitudPermiso solicitud = findSolicitudOrThrow(id);

        if (!EstadoSolicitud.EN_OBSERVACION.equals(solicitud.getEstadoSolicitud())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Solo se pueden modificar solicitudes EN_OBSERVACION. Estado actual: "
                + solicitud.getEstadoSolicitud());
        }

        solicitud.setTipoPermiso(tipoPermiso);
        solicitud.setMotivoSolicitud(motivoSolicitud);
        solicitud.setFechaPermiso(parseDate(fechaPermiso));
        solicitud.setHoraInicioSolicitada(parseTime(horaInicio));
        solicitud.setHoraFinSolicitada(parseTime(horaFin));
        solicitud.setEstadoSolicitud(EstadoSolicitud.PENDIENTE);
        solicitud.setObservacionRevision(null);
        solicitud.setRevisadoPor(null);
        solicitud.setFechaHoraRevision(null);
        solicitud.setFechaHoraSolicitud(LocalDateTime.now());

        SolicitudPermiso guardada = solicitudRepo.save(solicitud);
        notificacionService.notificarPermisoModificado(guardada);

        return mapper.toSolicitudResponse(guardada);
    }

    @Override
    @Transactional
    public SolicitudPermisoResponse aprobarSolicitud(Long id, AprobarPermisoRequest req, String username) {
        SolicitudPermiso solicitud = findSolicitudOrThrow(id);

        if (!solicitud.isPendiente()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Solo se pueden aprobar solicitudes PENDIENTES. Estado actual: " + solicitud.getEstadoSolicitud());
        }

        solicitud.setEstadoSolicitud(EstadoSolicitud.APROBADA);
        solicitud.setRevisadoPor(findUsuario(username));
        solicitud.setFechaHoraRevision(LocalDateTime.now());
        solicitud.setObservacionRevision(req.getObservacion());

        if (req.getIdInternoResponsable() != null) {
            crearDelegacionDesdeSolicitud(solicitud, req, findUsuario(username));
        } else {
            log.info("Solicitud {} aprobada SIN delegación (sin interno designado)", id);
        }

        SolicitudPermiso guardada = solicitudRepo.save(solicitud);
        notificacionService.notificarCambioEstado(guardada, "APROBADA");

        return mapper.toSolicitudResponse(guardada);
    }

    private void crearDelegacionDesdeSolicitud(SolicitudPermiso solicitud,
        AprobarPermisoRequest req, Usuario aprobador) {

        TitularJornada tj = solicitud.getTitularJornada();

        if (tj == null && solicitud.getJornada() != null) {
            tj = titularJornadaRepo
                .findByTitularAndJornada(
                    solicitud.getTitular().getIdTitular(),
                    solicitud.getJornada().getIdJornada()
                )
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.format(
                        "El titular '%s' no tiene asignación en la jornada '%s'. " +
                        "Asígnelo primero desde el módulo de Jornadas.",
                        solicitud.getTitular().getPersona().getNombreCompleto(),
                        solicitud.getJornada().getNombre()
                    )));
        }

        if (tj == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "No se puede crear la delegación: la solicitud no tiene jornada asociada");
        }

        Interno interno = internoRepo.findById(req.getIdInternoResponsable())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Interno no encontrado: " + req.getIdInternoResponsable()));

        LocalTime inicio = StringUtils.hasText(req.getHoraInicioPlanificada())
            ? parseTime(req.getHoraInicioPlanificada())
            : solicitud.getHoraInicioSolicitada();

        LocalTime fin = StringUtils.hasText(req.getHoraFinPlanificada())
            ? parseTime(req.getHoraFinPlanificada())
            : solicitud.getHoraFinSolicitada();

        var delegacion = Delegacion.builder()
            .titularJornada(tj)
            .internoResponsable(interno)
            .motivo(solicitud.getMotivoSolicitud())
            .estado(EstadoDelegacion.APROBADA)
            .aprobadoPor(aprobador)
            .fechaAprobacion(LocalDateTime.now())
            .horaInicioPlanificada(inicio)
            .horaFinPlanificada(fin)
            .solicitudPermiso(solicitud)
            .build();

        delegacionRepo.save(delegacion);
        log.info("Delegación creada: titular={} → interno={}, jornada={}",
            tj.getTitular().getPersona().getNombreCompleto(),
            interno.getPersona().getNombreCompleto(),
            tj.getJornada().getNombre());
    }

    @Override
    @Transactional
    public SolicitudPermisoResponse rechazarSolicitud(Long id, AccionConObservacionRequest req, String username) {
        SolicitudPermiso solicitud = findSolicitudOrThrow(id);

        if (!solicitud.isPendiente()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Solo se pueden rechazar solicitudes PENDIENTES o EN_OBSERVACION");
        }

        solicitud.setEstadoSolicitud(EstadoSolicitud.RECHAZADA);
        solicitud.setRevisadoPor(findUsuario(username));
        solicitud.setFechaHoraRevision(LocalDateTime.now());
        if (req != null) solicitud.setObservacionRevision(req.getObservacion());

        SolicitudPermiso guardada = solicitudRepo.save(solicitud);
        notificacionService.notificarCambioEstado(guardada, "RECHAZADA");
        return mapper.toSolicitudResponse(guardada);
    }

    @Override
    @Transactional
    public SolicitudPermisoResponse observarSolicitud(Long id, AccionConObservacionRequest req, String username) {
        SolicitudPermiso solicitud = findSolicitudOrThrow(id);

        if (!solicitud.isPendiente()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Solo se pueden observar solicitudes PENDIENTES");
        }
        if (req == null || !StringUtils.hasText(req.getObservacion())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "La observación es obligatoria al devolver una solicitud");
        }

        solicitud.setEstadoSolicitud(EstadoSolicitud.EN_OBSERVACION);
        solicitud.setRevisadoPor(findUsuario(username));
        solicitud.setFechaHoraRevision(LocalDateTime.now());
        solicitud.setObservacionRevision(req.getObservacion());

        SolicitudPermiso guardada = solicitudRepo.save(solicitud);
        notificacionService.notificarObservacion(guardada, req.getObservacion());
        return mapper.toSolicitudResponse(guardada);
    }

    @Override
    @Transactional
    public SolicitudPermisoResponse cancelarSolicitud(Long id) {
        SolicitudPermiso solicitud = findSolicitudOrThrow(id);

        if (!solicitud.isPendiente()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Solo se pueden cancelar solicitudes PENDIENTES");
        }

        solicitud.setEstadoSolicitud(EstadoSolicitud.CANCELADA);
        return mapper.toSolicitudResponse(solicitudRepo.save(solicitud));
    }

    @Override
    public List<SolicitudPermisoResponse> getSolicitudesPendientes() {
        return solicitudRepo.findByEstadoOrderByFecha(EstadoSolicitud.PENDIENTE)
                            .stream().map(mapper::toSolicitudResponse).toList();
    }

    @Override
    public List<SolicitudPermisoResponse> getSolicitudesPorTitular(Long idTitular) {
        return solicitudRepo.findByTitular(idTitular)
                            .stream().map(mapper::toSolicitudResponse).toList();
    }

    @Override
    public Map<String, Object> getEstadisticas() {
        Map<String, Object> stats = new LinkedHashMap<>();

        Map<String, Long> solicitudes = new LinkedHashMap<>();
        for (EstadoSolicitud e : EstadoSolicitud.values()) {
            solicitudes.put(e.name(), solicitudRepo.countByEstado(e));
        }
        stats.put("solicitudes", solicitudes);

        Map<String, Long> delegaciones = new LinkedHashMap<>();
        for (EstadoDelegacion e : EstadoDelegacion.values()) {
            delegaciones.put(e.name(), delegacionRepo.countByEstado(e));
        }
        stats.put("delegaciones", delegaciones);

        stats.put("totalSolicitudes", solicitudRepo.count());
        stats.put("totalDelegaciones", delegacionRepo.count());
        stats.put("delegacionesActivas",
            delegacionRepo.countByEstado(EstadoDelegacion.EN_CURSO));

        return stats;
    }

    // ══════════════════════════════════════════════════════════════
    //  DELEGACIONES
    // ══════════════════════════════════════════════════════════════

    @Override
    public Page<DelegacionResponse> getDelegaciones(Long idJornada, EstadoDelegacion estado, Pageable pageable) {
        return delegacionRepo.findWithFilters(idJornada, estado, pageable)
                             .map(mapper::toDelegacionResponse);
    }

    @Override
    public DelegacionResponse getDelegacion(Long id) {
        return mapper.toDelegacionResponse(findDelegacionOrThrow(id));
    }

    @Override
    @Transactional
    public DelegacionResponse crearDelegacion(DelegacionRequest req, String username) {
        TitularJornada tj = titularJornadaRepo.findById(req.getIdTitularJornada())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Asignación de jornada no encontrada"));

        Interno interno = internoRepo.findById(req.getIdInternoResponsable())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Interno no encontrado"));

        var delegacion = Delegacion.builder()
            .titularJornada(tj)
            .internoResponsable(interno)
            .motivo(req.getMotivo())
            .estado(EstadoDelegacion.PENDIENTE_APROBACION)
            .horaInicioPlanificada(parseTime(req.getHoraInicioPlanificada()))
            .horaFinPlanificada(parseTime(req.getHoraFinPlanificada()))
            .build();

        return mapper.toDelegacionResponse(delegacionRepo.save(delegacion));
    }

    @Override
    @Transactional
    public DelegacionResponse aprobarDelegacion(Long id, AccionConObservacionRequest req, String username) {
        Delegacion d = findDelegacionOrThrow(id);
        validateEstado(d.getEstado(), EstadoDelegacion.PENDIENTE_APROBACION,
            "Solo se pueden aprobar delegaciones en estado PENDIENTE_APROBACION");

        d.setEstado(EstadoDelegacion.APROBADA);
        d.setAprobadoPor(findUsuario(username));
        d.setFechaAprobacion(LocalDateTime.now());
        if (req != null) d.setObservacionAprobacion(req.getObservacion());

        return mapper.toDelegacionResponse(delegacionRepo.save(d));
    }

    @Override
    @Transactional
    public DelegacionResponse rechazarDelegacion(Long id, AccionConObservacionRequest req, String username) {
        Delegacion d = findDelegacionOrThrow(id);
        validateEstado(d.getEstado(), EstadoDelegacion.PENDIENTE_APROBACION,
            "Solo se pueden rechazar delegaciones en estado PENDIENTE_APROBACION");

        if (req == null || !StringUtils.hasText(req.getObservacion())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "La observación es obligatoria al rechazar");
        }

        d.setEstado(EstadoDelegacion.RECHAZADA);
        d.setAprobadoPor(findUsuario(username));
        d.setFechaAprobacion(LocalDateTime.now());
        d.setObservacionAprobacion(req.getObservacion());

        return mapper.toDelegacionResponse(delegacionRepo.save(d));
    }

    @Override
    @Transactional
    public DelegacionResponse iniciarDelegacion(Long id) {
        Delegacion d = findDelegacionOrThrow(id);
        validateEstado(d.getEstado(), EstadoDelegacion.APROBADA,
            "Solo se pueden iniciar delegaciones APROBADAS");

        d.setEstado(EstadoDelegacion.EN_CURSO);
        d.setHoraInicioReal(LocalTime.now());

        return mapper.toDelegacionResponse(delegacionRepo.save(d));
    }

    @Override
    @Transactional
    public DelegacionResponse completarDelegacion(Long id) {
        Delegacion d = findDelegacionOrThrow(id);
        validateEstado(d.getEstado(), EstadoDelegacion.EN_CURSO,
            "Solo se pueden completar delegaciones EN_CURSO");

        d.setEstado(EstadoDelegacion.COMPLETADA);
        d.setHoraFinReal(LocalTime.now());

        return mapper.toDelegacionResponse(delegacionRepo.save(d));
    }

    @Override
    @Transactional
    public DelegacionResponse cancelarDelegacion(Long id, AccionConObservacionRequest req) {
        Delegacion d = findDelegacionOrThrow(id);

        if (EstadoDelegacion.COMPLETADA.equals(d.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "No se puede cancelar una delegación ya COMPLETADA");
        }

        d.setEstado(EstadoDelegacion.CANCELADA);
        if (req != null && StringUtils.hasText(req.getObservacion())) {
            d.setObservacionAprobacion(req.getObservacion());
        }

        return mapper.toDelegacionResponse(delegacionRepo.save(d));
    }

    @Override
    public List<DelegacionResponse> getDelegacionesActivas() {
        return delegacionRepo.findByEstadoIn(
                List.of(EstadoDelegacion.APROBADA, EstadoDelegacion.EN_CURSO))
            .stream().map(mapper::toDelegacionResponse).toList();
    }

    @Override
    public List<DelegacionResponse> getDelegacionesPorJornada(Long idJornada) {
        return delegacionRepo.findByJornada(idJornada)
                             .stream().map(mapper::toDelegacionResponse).toList();
    }

    @Override
    @Transactional
    public SolicitudPermisoResponse subirJustificante(Long idSolicitud, MultipartFile file)
        throws IOException {

        SolicitudPermiso solicitud = findSolicitudOrThrow(idSolicitud);

        // Eliminar justificante anterior si existe
        if (solicitud.getJustificanteUrl() != null)
            fileStorageService.eliminarArchivo(solicitud.getJustificanteUrl());

        // Guardar en subcarpeta "justificantes" dentro del path configurado
        String rutaRelativa = fileStorageService.guardarJustificante(file, idSolicitud);
        solicitud.setJustificanteUrl(rutaRelativa);

        return mapper.toSolicitudResponse(solicitudRepo.save(solicitud));
}

    // ══════════════════════════════════════════════════════════════
    //  HELPERS PRIVADOS
    // ══════════════════════════════════════════════════════════════

    private SolicitudPermiso findSolicitudOrThrow(Long id) {
        return solicitudRepo.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Solicitud de permiso no encontrada: " + id));
    }

    private Delegacion findDelegacionOrThrow(Long id) {
        return delegacionRepo.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Delegación no encontrada: " + id));
    }

    private Usuario findUsuario(String username) {
        try {
            return usuarioRepo.findByUsername(username).orElse(null);
        } catch (Exception e) {
            log.warn("No se encontró usuario con username: {}", username);
            return null;
        }
    }

    private void validateEstado(EstadoDelegacion actual, EstadoDelegacion requerido, String mensaje) {
        if (!requerido.equals(actual)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, mensaje + ". Estado actual: " + actual);
        }
    }

    private LocalDate parseDate(String fecha) {
        return StringUtils.hasText(fecha) ? LocalDate.parse(fecha) : null;
    }

    private LocalTime parseTime(String hora) {
        return StringUtils.hasText(hora) ? LocalTime.parse(hora) : null;
    }
}