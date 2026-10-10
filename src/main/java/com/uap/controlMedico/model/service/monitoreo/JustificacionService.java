package com.uap.controlMedico.model.service.monitoreo;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.uap.controlMedico.model.dto.monitoreo.IncidenciaRevisionDto;

/**
 * Flujo de justificación de incidencias punibles (abandono confirmado, ausencia
 * por señal, cierre de sesión en turno). Refleja el flujo de los permisos:
 * el funcionario solicita, el admin/supervisor aprueba o rechaza.
 */
public interface JustificacionService {

    /** Funcionario: envía la justificación de una incidencia propia (motivo + adjunto opcional). */
    void solicitarJustificacion(Long idEvento, String motivo, MultipartFile justificante, String username);

    /** Admin/supervisor: aprueba la justificación → la incidencia queda JUSTIFICADA (no penaliza). */
    void aprobarJustificacion(Long idEvento, String observacion, String adminUsername);

    /** Admin/supervisor: rechaza la justificación → la incidencia queda RECHAZADA (abandono firme). */
    void rechazarJustificacion(Long idEvento, String observacion, String adminUsername);

    /** Admin/supervisor: incidencias con justificación enviada y pendiente de revisión. */
    List<IncidenciaRevisionDto> listarPendientesRevision();
}
