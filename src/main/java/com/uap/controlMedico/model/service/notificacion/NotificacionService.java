package com.uap.controlMedico.model.service.notificacion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.uap.controlMedico.model.dto.notificacionDto.EnviarNotificacionRequest;
import com.uap.controlMedico.model.dto.notificacionDto.NotificacionResponse;
import com.uap.controlMedico.model.entity.ActividadJornada;
import com.uap.controlMedico.model.entity.EventoAbandono;
import com.uap.controlMedico.model.entity.HorarioInterno;
import com.uap.controlMedico.model.entity.InternoJornada;
import com.uap.controlMedico.model.entity.Jornada;
import com.uap.controlMedico.model.entity.SesionMonitoreo;
import com.uap.controlMedico.model.entity.SolicitudPermiso;
import com.uap.controlMedico.model.entity.TitularJornada;
import com.uap.controlMedico.model.entity.Usuario;

public interface NotificacionService {

    // ── Automáticas (generadas por eventos del sistema) ───────────────────────

    void notificarNuevaSolicitud(SolicitudPermiso solicitud);
    void notificarPermisoModificado(SolicitudPermiso solicitud);
    void notificarCambioEstado(SolicitudPermiso solicitud, String nuevoEstado);
    void notificarObservacion(SolicitudPermiso solicitud, String observacion);
    void notificarAusencia(Usuario usuario, Jornada jornada);

    /** Persiste notificación AUSENCIA_DETECTADA en BD y la envía al propio funcionario por WS. */
    void notificarAbandonoAlFuncionario(EventoAbandono evento);

    /**
     * Ausencia por PÉRDIDA DE SEÑAL (no por posición): el sistema dejó de recibir la
     * ubicación, así que NO afirma "estás fuera de zona" (no lo sabe). Mensaje propio.
     */
    void notificarAusenciaPorSenalAlFuncionario(EventoAbandono evento);

    /** Avisa a los admins que un funcionario cerró sesión durante su turno activo. */
    // ── Canal de supervisión: campana del admin + grupo de Telegram ──────────
    // Un aviso por hecho, con la causa explícita. Los llama el detector que ya
    // clasificó la incidencia, nunca un job genérico de "ausencia".

    /** Incidencia ya confirmada y clasificada (falta, atraso, abandono, sin señal…). */
    void notificarIncidenciaAlAdmin(EventoAbandono evento);

    /** Cierre del abandono ya avisado: volvió al área. */
    void notificarRetornoZonaAlAdmin(EventoAbandono evento);

    /** Estaba reportando y dejó de hacerlo (GPS apagado, sin internet, app cerrada). */
    void notificarSenalPerdidaAlAdmin(SesionMonitoreo sesion, long minutosSinSenal);

    /** Volvió a emitir ubicación tras un corte avisado. */
    void notificarSenalRecuperadaAlAdmin(SesionMonitoreo sesion);

    /** Su turno empezó y nunca envió una sola ubicación. */
    void notificarGpsNoActivadoAlAdmin(SesionMonitoreo sesion);

    void notificarLogoutEnTurnoAlAdmin(SesionMonitoreo sesion);

    /** Avisa a admins/supervisores que un funcionario envió una justificación a revisar. */
    void notificarNuevaJustificacionAlAdmin(EventoAbandono evento);

    /** Avisa al funcionario el resultado de su justificación (aprobada o rechazada). */
    void notificarJustificacionRevisadaAlFuncionario(EventoAbandono evento, boolean aprobada);

    /** Avisa al funcionario que se registró una incidencia (atraso, falta, etc.) en su turno. */
    void notificarIncidenciaAlFuncionario(EventoAbandono evento);

    /** Notifica al funcionario inmediatamente al detectar su salida de zona (antes del timeout). */
    void notificarSalidaZonaAlFuncionario(EventoAbandono evento);

    /**
     * Notifica al funcionario que volvió a su zona asignada (cierre de un evento de
     * salida/abandono). Llega por WS y por push (FCM) para que lo vea aunque la app
     * esté cerrada — como cualquier app real al recuperar el estado normal.
     */
    void notificarRetornoZonaAlFuncionario(EventoAbandono evento);

    /**
     * Avisa al funcionario que su GPS está desactivado/denegado durante el turno.
     * También envía alerta GPS_DENEGADO al canal admin de monitoreo.
     */
    void notificarGpsDenegadoAlFuncionario(SesionMonitoreo sesion);

    /**
     * Recordatorio enviado al iniciar el turno del día: avisa al funcionario que
     * HOY le toca turno y debe activar su ubicación. Se entrega también por push
     * (FCM) para que llegue aunque la app esté cerrada o el teléfono bloqueado.
     */
    void notificarRecordatorioTurno(SesionMonitoreo sesion);

    /**
     * Aviso de cierre de turno: confirma al funcionario que su horario terminó y que
     * el registro de ubicación se detuvo.
     *
     * Se envía SOLO cuando el turno acaba con normalidad, nunca en los cierres por
     * falta o por inactividad: decirle "turno finalizado, buen trabajo" a quien acaba
     * de acumular una ausencia sería confuso y le ocultaría el problema.
     */
    void notificarFinTurno(SesionMonitoreo sesion);
    void notificarAsignacionJornada(TitularJornada tj);
    void notificarAsignacionJornada(InternoJornada ij);

    /**
     * Avisa a TODO el personal asignado a una jornada que sus horarios cambiaron.
     *
     * Sin esto, al editar un turno la app del funcionario seguía mostrando el horario
     * viejo hasta que algo la hiciera recargar (volver a primer plano, o el aviso que
     * emite el planificador al abrir la sesión). Es decir: podía trabajar minutos con
     * un horario equivocado en pantalla.
     */
    void notificarCambioJornada(Jornada jornada);
    void notificarAsignacionActividad(ActividadJornada aj);
    void notificarHorarioAsignado(HorarioInterno horario);

    // ── API pública (admin + usuario) ─────────────────────────────────────────

    /** Notificaciones visibles para el usuario autenticado (propias + globales). */
    Page<NotificacionResponse> getMisNotificaciones(String username, Pageable pageable);

    /** Cuenta no leídas personales del usuario. */
    long getCountNoLeidas(String username);

    /** Marca una notificación personal como leída. */
    NotificacionResponse marcarLeida(Long idNotificacion, String username);

    /** Marca todas las notificaciones personales como leídas. */
    int marcarTodasLeidas(String username);

    /**
     * ADMIN: envía notificación personalizada a un usuario o broadcast global.
     * Si {@code request.usernameDestinatario} es null/vacío → broadcast a todos.
     */
    NotificacionResponse enviarPersonalizado(EnviarNotificacionRequest request,
                                             String usernameRemitente);

    /**
     * ADMIN/SUPERVISOR: notificaciones personales enviadas a un titular/interno,
     * recientes primero. Alimenta la ficha de la vista de vigilancia, donde el
     * operador necesita ver qué ya se le avisó a esa persona antes de volver a
     * escribirle.
     *
     * @param tipo  "TITULAR" | "INTERNO"
     * @param id    idTitular o idInterno según {@code tipo}
     * @param limit máximo de notificaciones a devolver
     * @return lista vacía si la persona no tiene usuario asociado
     */
    java.util.List<NotificacionResponse> getNotificacionesDePersonal(
        String tipo, Long id, int limit);
}