package com.uap.controlMedico.model.service.monitoreo;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.uap.controlMedico.model.dao.EventoAbandonoDao;
import com.uap.controlMedico.model.dao.UsuarioDao;
import com.uap.controlMedico.model.dto.monitoreo.IncidenciaRevisionDto;
import com.uap.controlMedico.model.entity.EventoAbandono;
import com.uap.controlMedico.model.entity.SesionMonitoreo;
import com.uap.controlMedico.model.enums.EstadoJustificacion;
import com.uap.controlMedico.model.service.notificacion.NotificacionService;
import com.uap.controlMedico.shared.storage.FileStorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class JustificacionServiceImpl implements JustificacionService {

    private final EventoAbandonoDao   eventoDao;
    private final UsuarioDao          usuarioDao;
    private final FileStorageService  fileStorage;
    private final NotificacionService notificacionService;

    @Override
    @Transactional
    public void solicitarJustificacion(Long idEvento, String motivo,
                                       MultipartFile justificante, String username) {
        if (!StringUtils.hasText(motivo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El motivo de la justificación es obligatorio.");
        }

        EventoAbandono evento = eventoDao.findById(idEvento)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Incidencia no encontrada: " + idEvento));

        // La incidencia debe pertenecer al usuario que intenta justificarla.
        Long propietario = idPersonaDeEvento(evento);
        Long solicitante = idPersonaDeUsuario(username);
        if (propietario == null || solicitante == null || !propietario.equals(solicitante)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "No puedes justificar una incidencia que no es tuya.");
        }

        if (!evento.isPuedeJustificar()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Esta incidencia no admite justificación en su estado actual ("
                    + evento.getEstadoJustificacionEfectivo() + ").");
        }

        String url = null;
        if (justificante != null && !justificante.isEmpty()) {
            try {
                url = fileStorage.guardarJustificante(justificante, idEvento);
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo guardar el justificante.");
            }
        }

        evento.setMotivoJustificacion(motivo);
        evento.setJustificanteUrl(url);
        evento.setFechaSolicitudJustificacion(LocalDateTime.now());
        evento.setEstadoJustificacion(EstadoJustificacion.EN_REVISION);
        eventoDao.save(evento);

        notificacionService.notificarNuevaJustificacionAlAdmin(evento);
        log.info("Justificación enviada: evento={}, usuario={}", idEvento, username);
    }

    @Override
    @Transactional
    public void aprobarJustificacion(Long idEvento, String observacion, String adminUsername) {
        EventoAbandono evento = cargarParaRevision(idEvento);
        evento.setEstadoJustificacion(EstadoJustificacion.JUSTIFICADA);
        finalizarRevision(evento, observacion, adminUsername);
        notificacionService.notificarJustificacionRevisadaAlFuncionario(evento, true);
        log.info("Justificación APROBADA: evento={}, por={}", idEvento, adminUsername);
    }

    @Override
    @Transactional
    public void rechazarJustificacion(Long idEvento, String observacion, String adminUsername) {
        EventoAbandono evento = cargarParaRevision(idEvento);
        evento.setEstadoJustificacion(EstadoJustificacion.RECHAZADA);
        finalizarRevision(evento, observacion, adminUsername);
        notificacionService.notificarJustificacionRevisadaAlFuncionario(evento, false);
        log.info("Justificación RECHAZADA: evento={}, por={}", idEvento, adminUsername);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncidenciaRevisionDto> listarPendientesRevision() {
        return eventoDao.findByEstadoJustificacion(EstadoJustificacion.EN_REVISION).stream()
            .map(this::toRevisionDto).toList();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private EventoAbandono cargarParaRevision(Long idEvento) {
        EventoAbandono evento = eventoDao.findById(idEvento)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Incidencia no encontrada: " + idEvento));
        if (evento.getEstadoJustificacionEfectivo() != EstadoJustificacion.EN_REVISION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Solo se revisan justificaciones EN_REVISION. Estado actual: "
                    + evento.getEstadoJustificacionEfectivo());
        }
        return evento;
    }

    private void finalizarRevision(EventoAbandono evento, String observacion, String adminUsername) {
        evento.setRevisadoPor(usuarioDao.findByUsername(adminUsername).orElse(null));
        evento.setFechaRevision(LocalDateTime.now());
        evento.setObservacionRevision(observacion);
        eventoDao.save(evento);
    }

    private Long idPersonaDeEvento(EventoAbandono e) {
        SesionMonitoreo s = e.getSesion();
        if (s == null) return null;
        if (s.getTitular() != null && s.getTitular().getPersona() != null) {
            return s.getTitular().getPersona().getIdPersona();
        }
        if (s.getInterno() != null && s.getInterno().getPersona() != null) {
            return s.getInterno().getPersona().getIdPersona();
        }
        return null;
    }

    private Long idPersonaDeUsuario(String username) {
        return usuarioDao.findByUsernameWithPersona(username)
            .map(u -> u.getPersona() != null ? u.getPersona().getIdPersona() : null)
            .orElse(null);
    }

    private IncidenciaRevisionDto toRevisionDto(EventoAbandono e) {
        return IncidenciaRevisionDto.builder()
            .idEvento(e.getIdEvento())
            .nombrePersonal(e.getNombrePersonal())
            .tipo(e.getTipoEfectivo().name())
            .tipoLabel(e.getTipoEfectivo().getLabel())
            .estado(e.getEstado().name())
            .estadoJustificacion(e.getEstadoJustificacionEfectivo().name())
            .nombreLugar(e.getSesion() != null && e.getSesion().getLugar() != null
                ? e.getSesion().getLugar().getNombre() : null)
            .timestampSalida(e.getTimestampSalida() != null ? e.getTimestampSalida().toString() : null)
            .tiempoFueraMinutos(e.getTiempoFueraMinutos())
            .minutosIncidencia(e.getMinutosIncidencia())
            .observaciones(e.getObservaciones())
            .motivoJustificacion(e.getMotivoJustificacion())
            .justificanteUrl(e.getJustificanteUrl())
            .fechaSolicitudJustificacion(e.getFechaSolicitudJustificacion() != null
                ? e.getFechaSolicitudJustificacion().toString() : null)
            .build();
    }
}
