package com.uap.controlMedico.model.service.notificacion;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uap.controlMedico.model.dao.InternoDao;
import com.uap.controlMedico.model.dao.InternoJornadaDao;
import com.uap.controlMedico.model.dao.NotificacionDao;
import com.uap.controlMedico.model.dao.TitularDao;
import com.uap.controlMedico.model.dao.TitularJornadaDao;
import com.uap.controlMedico.model.dao.UsuarioDao;
import com.uap.controlMedico.model.dto.notificacionDto.EnviarNotificacionRequest;
import com.uap.controlMedico.model.dto.notificacionDto.NotificacionDto;
import com.uap.controlMedico.model.dto.notificacionDto.NotificacionResponse;
import com.uap.controlMedico.model.entity.ActividadJornada;
import com.uap.controlMedico.model.entity.EventoAbandono;
import com.uap.controlMedico.model.entity.HorarioInterno;
import com.uap.controlMedico.model.entity.InternoJornada;
import com.uap.controlMedico.model.entity.Jornada;
import com.uap.controlMedico.model.entity.Notificacion;
import com.uap.controlMedico.model.entity.SesionMonitoreo;
import com.uap.controlMedico.model.entity.SolicitudPermiso;
import com.uap.controlMedico.model.entity.TitularJornada;
import com.uap.controlMedico.model.entity.Usuario;
import com.uap.controlMedico.model.enums.EstadoZona;
import com.uap.controlMedico.model.enums.TipoIncidencia;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import com.uap.controlMedico.model.service.push.PushNotificationService;
import com.uap.controlMedico.model.service.telegram.TelegramNotificationService;
import com.uap.controlMedico.model.service.telegram.TelegramPlantillaService;
import com.uap.controlMedico.model.enums.TipoMensajeTelegram;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificacionServiceImpl implements NotificacionService {

    private final SimpMessagingTemplate messagingTemplate;
    private final UsuarioDao            usuarioDao;
    private final NotificacionDao       notificacionDao;
    // Para resolver a quién afecta un cambio de horario de jornada.
    private final TitularJornadaDao     titularJornadaDao;
    private final InternoJornadaDao     internoJornadaDao;
    // Para resolver titular/interno → persona → usuario en la ficha del admin.
    private final TitularDao            titularDao;
    private final InternoDao            internoDao;

    /** Inyección opcional: Telegram no bloquea el arranque si el token/chat-id no están configurados. */
    @Autowired(required = false)
    private TelegramNotificationService telegramService;

    private final TelegramPlantillaService telegramPlantillas;

    /** Inyección opcional: push no bloquea el arranque si las keys VAPID no están configuradas. */
    @Autowired(required = false)
    private PushNotificationService pushService;

    // ═══════════════════════════════════════════════════════════════════════
    //  HELPERS INTERNOS
    // ═══════════════════════════════════════════════════════════════════════

    // Formato uniforme de las notificaciones que llegan al usuario:
    //  - Título: TODO EN MAYÚSCULAS, con el/los icono(s) movidos al FINAL.
    //  - Mensaje: estilo oración (solo la primera letra en mayúscula) y SIN iconos.
    // Idempotente: aplicarlo dos veces da el mismo resultado (seguro si una notif
    // pasa por persistir() y luego por enviarPush()).
    private static final Locale  LOCALE_ES = Locale.forLanguageTag("es");
    private static final Pattern ICONOS = Pattern.compile(
        "[\\p{So}\\p{Cs}\\uFE0F\\u2190-\\u21FF\\u2300-\\u27BF\\u2B00-\\u2BFF]+");

    /** Título → MAYÚSCULAS + iconos al final. */
    static String formatearTitulo(String titulo) {
        if (titulo == null || titulo.isBlank()) return titulo;
        Matcher m = ICONOS.matcher(titulo);
        StringBuilder iconos = new StringBuilder();
        while (m.find()) iconos.append(m.group());
        String texto = ICONOS.matcher(titulo).replaceAll(" ")
            .replaceAll("\\s+", " ").trim().toUpperCase(LOCALE_ES);
        return iconos.length() > 0 ? (texto + " " + iconos).trim() : texto;
    }

    /** Mensaje → sin iconos, primera letra en mayúscula (preserva siglas/nombres). */
    static String formatearMensaje(String mensaje) {
        if (mensaje == null || mensaje.isBlank()) return mensaje;
        String s = ICONOS.matcher(mensaje).replaceAll(" ").replaceAll("\\s+", " ").trim();
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Guarda en BD y retorna el DTO con idNotificacion ya asignado. */
    private NotificacionDto persistir(NotificacionDto dto, Usuario destinatario,
                                      Usuario remitente, Long idActividadJornada,
                                      boolean esGlobal) {
        // Formato uniforme antes de guardar y de enviar por WS (el mismo dto se reusa).
        dto.setTitulo(formatearTitulo(dto.getTitulo()));
        dto.setMensaje(formatearMensaje(dto.getMensaje()));
        Notificacion n = Notificacion.builder()
            .destinatario(destinatario)
            .remitente(remitente)
            .tipo(dto.getTipo())
            .titulo(dto.getTitulo())
            .mensaje(dto.getMensaje())
            .timestamp(LocalDateTime.now())
            .idJornada(dto.getIdJornada())
            .idActividadJornada(idActividadJornada)
            .idSolicitud(dto.getIdSolicitud())
            .esGlobal(esGlobal)
            .build();

        n = notificacionDao.save(n);
        dto.setIdNotificacion(n.getIdNotificacion());
        dto.setIdActividadJornada(idActividadJornada);
        dto.setEsGlobal(esGlobal);
        return dto;
    }

    /** Encuentra username por persona local o por perId de Londra (titulares). */
    private String resolverUsername(Long idPersona, Long titularPerId) {
        String username = usuarioDao.findByPersonaIdPersona(idPersona)
            .map(Usuario::getUsername).orElse(null);
        if (username == null && titularPerId != null) {
            username = usuarioDao.findByPerIdAndActivoTrue(titularPerId)
                .map(Usuario::getUsername).orElse(null);
        }
        return username;
    }

    /** Envía WS personal y luego por topic broadcast. */
    private void enviarWsPersonal(String username, NotificacionDto dto) {
        messagingTemplate.convertAndSendToUser(username, "/queue/notificaciones", dto);
        log.debug("WS → /user/{}/queue/notificaciones [{}]", username, dto.getTipo());
    }

    /**
     * Envía push si el servicio está disponible y el usuario tiene suscripción.
     * Silencioso: nunca lanza excepción.
     */
    private void enviarPush(String username, String titulo, String mensaje,
                            String tipo, String url, boolean urgente) {
        if (pushService == null || !pushService.isEnabled()) return;
        try {
            pushService.enviarAUsuario(username,
                formatearTitulo(titulo), formatearMensaje(mensaje), tipo, url, urgente);
        } catch (Exception e) {
            log.warn("Push falló para {}: {}", username, e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TELEGRAM — mensajes de SUPERVISIÓN (dirigidos al admin, no al funcionario)
    // ═══════════════════════════════════════════════════════════════════════
    //
    // El grupo de Telegram es un canal de supervisión, no el buzón del funcionario.
    // Reusar ahí el texto en segunda persona ("abre SISU y déjala minimizada") era
    // confuso, y —peor— el aviso por pérdida de señal se leía como si la persona
    // hubiera salido de su área. Cada tipo de incidencia tiene ahora su propio
    // encabezado y su propia explicación de la CAUSA, y se envía UNA sola vez.

    private static final DateTimeFormatter TG_HORA = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Claves de avisos ya publicados en Telegram. El grupo debe recibir un mensaje
     * por hecho, no un recordatorio en cada ciclo del scheduler: antes una misma
     * ausencia se repetía cada 15 minutos hasta el fin del turno.
     *
     * Cota de memoria: al superar el máximo se vacía. Son varios miles de eventos,
     * un volumen que no se alcanza en un día de operación; y el peor caso de vaciar
     * es repetir un aviso, nunca perderlo.
     */
    private final java.util.Set<String> telegramEnviados =
        java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final int TELEGRAM_DEDUP_MAX = 5_000;

    /** true solo la primera vez que se pide publicar este aviso. */
    private boolean primerEnvioTelegram(String clave) {
        if (telegramEnviados.size() > TELEGRAM_DEDUP_MAX) telegramEnviados.clear();
        return telegramEnviados.add(clave);
    }

    /** Permite volver a avisar de una sesión cuando su situación se normaliza. */
    private void olvidarEnvioTelegram(String clave) {
        telegramEnviados.remove(clave);
    }

    /** "Lugar — Turno" de la sesión, o null si no hay ninguno de los dos. */
    private static String contextoSesion(SesionMonitoreo sesion) {
        if (sesion == null) return null;
        String lugar   = sesion.getLugar()   != null ? sesion.getLugar().getNombre()   : null;
        String jornada = sesion.getJornada() != null ? sesion.getJornada().getNombre() : null;
        if (lugar == null && jornada == null) return null;
        if (lugar == null)   return jornada;
        if (jornada == null) return lugar;
        return lugar + " — " + jornada;
    }

    /** Nombre del personal de una sesión (titular o interno). */
    private static String nombreDeSesion(SesionMonitoreo sesion) {
        if (sesion == null) return "Desconocido";
        if (sesion.getTitular() != null && sesion.getTitular().getPersona() != null) {
            return sesion.getTitular().getPersona().getNombreCompleto();
        }
        if (sesion.getInterno() != null && sesion.getInterno().getPersona() != null) {
            return sesion.getInterno().getPersona().getNombreCompleto();
        }
        return "Desconocido";
    }

    /** Línea de batería y conectividad, solo si hay algún dato. */
    private static String lineaDispositivo(SesionMonitoreo sesion) {
        if (sesion == null) return null;
        Integer bat = sesion.getUltimaBateria();
        String  con = sesion.getUltimaConectividad() != null
            ? String.valueOf(sesion.getUltimaConectividad()) : null;
        if (bat == null && con == null) return null;
        StringBuilder sb = new StringBuilder("🔋 ");
        sb.append(bat != null ? "Batería: " + bat + "%" : "Batería: sin dato");
        if (con != null) sb.append(" · Conexión: ").append(con);
        return sb.toString();
    }

    /** Envía al grupo un bloque ya armado, tolerando cualquier fallo de red. */
    private void publicarTelegram(TipoMensajeTelegram tipo, String nombre, String contexto,
                                  String momento, String dispositivo, String detalle) {
        telegramPlantillas.enviarAutomatico(tipo, java.util.Map.of(
            "nombre", nombre != null ? nombre : "Desconocido",
            "contexto", contexto != null ? contexto : "",
            "momento", momento != null ? momento : "",
            "dispositivo", dispositivo != null ? dispositivo : "",
            "detalle", detalle != null ? detalle : ""));
    }

    /**
     * Publica una incidencia en el grupo con el encabezado y la causa propios de su
     * tipo. Un mensaje por evento (dedupe por id), de modo que el grupo nunca recibe
     * el mismo hecho dos veces.
     */
    private void enviarTelegramIncidencia(EventoAbandono evt) {
        if (telegramService == null || !telegramService.isEnabled()) return;
        if (evt == null || evt.getSesion() == null) return;

        TipoIncidencia tipo = evt.getTipoEfectivo();
        // Sin id no hay con qué deduplicar: se publica igual antes que callar un
        // hecho real. Con IDENTITY el id ya viene puesto por el save() previo.
        if (evt.getIdEvento() != null
                && !primerEnvioTelegram("EVENTO#" + tipo + "#" + evt.getIdEvento())) {
            log.debug("Telegram: aviso {} del evento {} ya publicado; se omite",
                tipo, evt.getIdEvento());
            return;
        }

        SesionMonitoreo sesion = evt.getSesion();
        Integer min = evt.getMinutosIncidencia();
        String cuando;
        String causa;

        switch (tipo) {
            case FALTA -> {
                cuando = "🕐 Turno iniciado: " + evt.getTimestampSalida().format(TG_HORA);
                causa  = "No se recibió ninguna ubicación desde el inicio del turno.";
            }
            case ATRASO -> {
                cuando = "🕐 Inicio del turno: " + evt.getTimestampSalida().format(TG_HORA)
                       + " · Llegada: " + evt.getTimestampAbandono().format(TG_HORA);
                causa  = "Llegó al área asignada"
                       + (min != null ? " " + min + " min tarde" : " después de la hora de inicio") + ".";
            }
            case ABANDONO_ZONA -> {
                cuando = "🕐 Salió: " + evt.getTimestampSalida().format(TG_HORA)
                       + " · Lleva " + evt.getTiempoFueraMinutos() + " min fuera";
                causa  = "Su GPS lo ubica FUERA del área asignada y no ha regresado.";
            }
            case AUSENCIA_SENAL -> {
                cuando = "🕐 Última señal: " + evt.getTimestampSalida().format(TG_HORA)
                       + " (hace " + evt.getTiempoFueraMinutos() + " min)";
                // La distinción que faltaba: pérdida de señal NO es salir del área.
                causa  = "Dejó de enviar su ubicación: GPS apagado, sin internet o app cerrada."
                       + "\n⚠️ No se detectó salida del área: " + ultimaPosicionConocida(sesion) + ".";
            }
            case LOGOUT_EN_TURNO -> {
                cuando = "🕐 Última señal: " + evt.getTimestampSalida().format(TG_HORA);
                causa  = "Cerró sesión en la app durante su turno y dejó de reportar.";
            }
            case SALIDA_TEMPRANA -> {
                cuando = "🕐 " + evt.getTimestampAbandono().format(TG_HORA);
                causa  = "Dejó su puesto"
                       + (min != null ? " " + min + " min antes" : " antes") + " del fin del turno.";
            }
            default -> {
                cuando = "🕐 " + evt.getTimestampAbandono().format(TG_HORA);
                causa  = evt.getObservaciones() != null
                       ? evt.getObservaciones() : "Se registró una incidencia en el turno.";
            }
        }

        String contexto = contextoSesion(sesion);
        publicarTelegram(TipoMensajeTelegram.valueOf(tipo.name()),
            evt.getNombrePersonal(), contexto, cuando.replaceFirst("^🕐 ", ""),
            // Batería y conectividad solo aportan cuando la causa es la señal.
            (tipo == TipoIncidencia.AUSENCIA_SENAL || tipo == TipoIncidencia.LOGOUT_EN_TURNO)
                ? lineaDispositivo(sesion) : null,
            causa);
    }

    /** Describe dónde estaba la persona la última vez que sí reportó. */
    private static String ultimaPosicionConocida(SesionMonitoreo sesion) {
        EstadoZona zona = sesion != null ? sesion.getEstadoZona() : null;
        if (zona == EstadoZona.DENTRO) return "su última posición conocida estaba DENTRO del área";
        if (zona == EstadoZona.FUERA)  return "su última posición conocida ya estaba FUERA del área";
        return "no se llegó a confirmar su posición";
    }

    /** Título corto por tipo para la campana del admin (sin iconos: los pone persistir()). */
    private static String tituloAdminDe(TipoIncidencia tipo) {
        return switch (tipo) {
            case FALTA           -> "🚫 Falta — no se presentó";
            case ATRASO          -> "⏰ Atraso";
            case ABANDONO_ZONA   -> "📍 Abandono de área";
            case AUSENCIA_SENAL  -> "📡 Sin señal — ausencia";
            case LOGOUT_EN_TURNO -> "🚪 Cerró sesión en turno";
            case SALIDA_TEMPRANA -> "↩️ Salida temprana";
        };
    }

    /** Mensaje del admin por tipo: dice QUÉ pasó y POR QUÉ, sin ambigüedad. */
    private static String mensajeAdminDe(EventoAbandono evt) {
        TipoIncidencia tipo = evt.getTipoEfectivo();
        String nombre = evt.getNombrePersonal();
        String donde  = contextoSesion(evt.getSesion());
        String lugar  = donde != null ? " (" + donde + ")" : "";
        Integer min   = evt.getMinutosIncidencia();
        return switch (tipo) {
            case FALTA -> nombre + lugar + " no reportó ubicación en todo el inicio de su turno.";
            case ATRASO -> nombre + lugar + " llegó"
                + (min != null ? " " + min + " min" : "") + " tarde a su turno.";
            case ABANDONO_ZONA -> nombre + lugar + " salió de su área asignada y no regresó ("
                + evt.getTiempoFueraMinutos() + " min fuera).";
            // Distinción clave: sin señal NO es lo mismo que fuera del área.
            case AUSENCIA_SENAL -> nombre + lugar + " dejó de enviar su ubicación hace "
                + evt.getTiempoFueraMinutos() + " min (GPS apagado, sin internet o app cerrada). "
                + "No se detectó salida del área.";
            case LOGOUT_EN_TURNO -> nombre + lugar + " cerró sesión durante su turno.";
            case SALIDA_TEMPRANA -> nombre + lugar + " dejó su puesto"
                + (min != null ? " " + min + " min" : "") + " antes del fin del turno.";
        };
    }

    /** Persiste una notificación para cada ADMIN/SUPERVISOR activo y la envía por WS. */
    private void difundirAAdmins(String tipo, String titulo, String mensaje) {
        for (Usuario admin : usuarioDao.findActivosByRoles(List.of("ADMIN", "SUPERVISOR"))) {
            NotificacionDto dto = NotificacionDto.builder()
                .tipo(tipo).titulo(titulo).mensaje(mensaje)
                .timestamp(NotificacionDto.ahora()).build();
            persistir(dto, admin, null, null, false);
            enviarWsPersonal(admin.getUsername(), dto);
        }
    }

    private static final java.util.Map<String, String> PUSH_URL = java.util.Map.of(
        "JORNADA_ASIGNADA",    "/app/turnos",
        "ACTIVIDAD_ASIGNADA",  "/app/actividades",
        "PERMISO_APROBADO",    "/app/permisos",
        "PERMISO_RECHAZADO",   "/app/permisos",
        "PERMISO_OBSERVADO",   "/app/permisos",
        "AUSENCIA_DETECTADA",  "/app/home",
        "GPS_DENEGADO",        "/app/home"
    );

    // ═══════════════════════════════════════════════════════════════════════
    //  NOTIFICACIONES AUTOMÁTICAS
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public void notificarNuevaSolicitud(SolicitudPermiso solicitud) {
        String nombreCompleto = solicitud.getTitular().getPersona().getNombreCompleto();
        String tipoLabel      = solicitud.getTipoPermiso().name();

        // Persistir una notificación en BD para cada admin y supervisor activo
        List<Usuario> admins = usuarioDao.findActivosByRoles(List.of("ADMIN", "SUPERVISOR"));
        for (Usuario admin : admins) {
            NotificacionDto dto = NotificacionDto.builder()
                .tipo("NUEVA_SOLICITUD")
                .titulo("📋 Nueva solicitud de permiso")
                .mensaje(nombreCompleto + " solicita permiso: " + tipoLabel)
                .timestamp(NotificacionDto.ahora())
                .idSolicitud(solicitud.getIdSolicitudPermiso())
                .nombreTitular(nombreCompleto)
                .tipoPermiso(tipoLabel)
                .build();
            persistir(dto, admin, null, null, false);
            enviarWsPersonal(admin.getUsername(), dto);
        }

        // También broadcast al tópico admin para refresco inmediato del grid
        NotificacionDto broadcast = NotificacionDto.builder()
            .tipo("NUEVA_SOLICITUD")
            .titulo("📋 Nueva solicitud de permiso")
            .mensaje(nombreCompleto + " solicita permiso: " + tipoLabel)
            .timestamp(NotificacionDto.ahora())
            .idSolicitud(solicitud.getIdSolicitudPermiso())
            .nombreTitular(nombreCompleto)
            .tipoPermiso(tipoLabel)
            .build();
        messagingTemplate.convertAndSend("/topic/admin/permisos", broadcast);
        log.debug("WS → /topic/admin/permisos [NUEVA_SOLICITUD id={}, admins notificados={}]",
            solicitud.getIdSolicitudPermiso(), admins.size());
    }

    @Override
    @Transactional
    public void notificarPermisoModificado(SolicitudPermiso solicitud) {
        String nombreCompleto = solicitud.getTitular().getPersona().getNombreCompleto();
        String tipoLabel      = solicitud.getTipoPermiso().name();

        List<Usuario> admins = usuarioDao.findActivosByRoles(List.of("ADMIN", "SUPERVISOR"));
        for (Usuario admin : admins) {
            NotificacionDto dto = NotificacionDto.builder()
                .tipo("SOLICITUD_MODIFICADA")
                .titulo("📝 Solicitud corregida y reenviada")
                .mensaje(nombreCompleto + " corrigió su solicitud de permiso: " + tipoLabel)
                .timestamp(NotificacionDto.ahora())
                .idSolicitud(solicitud.getIdSolicitudPermiso())
                .nombreTitular(nombreCompleto)
                .tipoPermiso(tipoLabel)
                .build();
            persistir(dto, admin, null, null, false);
            enviarWsPersonal(admin.getUsername(), dto);
        }

        NotificacionDto broadcast = NotificacionDto.builder()
            .tipo("SOLICITUD_MODIFICADA")
            .titulo("📝 Solicitud corregida y reenviada")
            .mensaje(nombreCompleto + " corrigió su solicitud de permiso: " + tipoLabel)
            .timestamp(NotificacionDto.ahora())
            .idSolicitud(solicitud.getIdSolicitudPermiso())
            .nombreTitular(nombreCompleto)
            .tipoPermiso(tipoLabel)
            .build();
        messagingTemplate.convertAndSend("/topic/admin/permisos", broadcast);
        log.debug("WS → /topic/admin/permisos [SOLICITUD_MODIFICADA id={}, admins notificados={}]",
            solicitud.getIdSolicitudPermiso(), admins.size());
    }

    @Override
    @Transactional
    public void notificarObservacion(SolicitudPermiso solicitud, String observacion) {
        Long idPersona  = solicitud.getTitular().getPersona().getIdPersona();
        Long perId      = solicitud.getTitular().getPerId();
        String username = resolverUsername(idPersona, perId);
        if (username == null) {
            log.warn("WS: sin usuario para idPersona={} — omitiendo notificación de observación", idPersona);
            return;
        }

        NotificacionDto notif = NotificacionDto.builder()
            .tipo("PERMISO_OBSERVADO")
            .titulo("👁 Tu solicitud necesita correcciones")
            .mensaje("Tu solicitud de " + solicitud.getTipoPermiso().name()
                     + " fue devuelta para corrección."
                     + (observacion != null && !observacion.isBlank()
                         ? " Observación: " + observacion : ""))
            .timestamp(NotificacionDto.ahora())
            .idSolicitud(solicitud.getIdSolicitudPermiso())
            .tipoPermiso(solicitud.getTipoPermiso().name())
            .build();

        Usuario dest = usuarioDao.findByUsername(username).orElse(null);
        persistir(notif, dest, null, null, false);
        enviarWsPersonal(username, notif);
        enviarPush(username, notif.getTitulo(), notif.getMensaje(),
                   notif.getTipo(), PUSH_URL.getOrDefault(notif.getTipo(), "/app/home"), false);
    }

    @Override
    @Transactional
    public void notificarCambioEstado(SolicitudPermiso solicitud, String nuevoEstado) {
        Long idPersona = solicitud.getTitular().getPersona().getIdPersona();
        Long perId     = solicitud.getTitular().getPerId();
        String username = resolverUsername(idPersona, perId);
        if (username == null) {
            log.warn("WS: sin usuario para idPersona={} — omitiendo notificación de permiso", idPersona);
            return;
        }

        boolean aprobada = "APROBADA".equals(nuevoEstado);
        NotificacionDto notif = NotificacionDto.builder()
            .tipo(aprobada ? "PERMISO_APROBADO" : "PERMISO_RECHAZADO")
            .titulo(aprobada ? "✅ Permiso aprobado" : "❌ Permiso rechazado")
            .mensaje("Tu solicitud de " + solicitud.getTipoPermiso().name()
                     + " fue " + nuevoEstado.toLowerCase() + ".")
            .timestamp(NotificacionDto.ahora())
            .idSolicitud(solicitud.getIdSolicitudPermiso())
            .tipoPermiso(solicitud.getTipoPermiso().name())
            .build();

        Usuario dest = usuarioDao.findByUsername(username).orElse(null);
        persistir(notif, dest, null, null, false);
        enviarWsPersonal(username, notif);
        // Push: el permiso aprobado/rechazado debe llegar aunque la app esté cerrada.
        enviarPush(username, notif.getTitulo(), notif.getMensaje(),
                   notif.getTipo(), PUSH_URL.getOrDefault(notif.getTipo(), "/app/permisos"), false);
    }

    @Override
    @Transactional
    public void notificarAbandonoAlFuncionario(EventoAbandono evento) {
        if (evento.getSesion() == null) return;

        // Resolver username: titular tiene perId, interno solo idPersona
        String username = null;
        String nombreLugar = evento.getSesion().getLugar() != null
            ? evento.getSesion().getLugar().getNombre() : null;

        if (evento.getSesion().getTitular() != null) {
            var titular = evento.getSesion().getTitular();
            Long idPersona = titular.getPersona() != null ? titular.getPersona().getIdPersona() : null;
            username = resolverUsername(idPersona, titular.getPerId());
        } else if (evento.getSesion().getInterno() != null) {
            var interno = evento.getSesion().getInterno();
            Long idPersona = interno.getPersona() != null ? interno.getPersona().getIdPersona() : null;
            username = resolverUsername(idPersona, null);
        }

        if (username == null) {
            log.warn("notificarAbandonoAlFuncionario: sin usuario para evento id={}", evento.getIdEvento());
            return;
        }

        String mensajeLugar = nombreLugar != null ? " — " + nombreLugar : "";
        NotificacionDto notif = NotificacionDto.builder()
            .tipo("AUSENCIA_DETECTADA")
            .titulo("⚠️ Fuera de tu fuente laboral")
            .mensaje("Se detectó que estás fuera de tu área asignada" + mensajeLugar
                     + ". Por favor regresa a tu puesto de trabajo.")
            .timestamp(NotificacionDto.ahora())
            .build();

        Usuario dest = usuarioDao.findByUsername(username).orElse(null);
        persistir(notif, dest, null, null, false);
        enviarWsPersonal(username, notif);
        enviarPush(username, notif.getTitulo(), notif.getMensaje(), notif.getTipo(), "/app/home", true);
        log.info("Notificación de abandono enviada al funcionario: {}", username);
    }

    @Override
    @Transactional
    public void notificarAusenciaPorSenalAlFuncionario(EventoAbandono evento) {
        if (evento.getSesion() == null) return;

        String username = null;
        String nombreLugar = evento.getSesion().getLugar() != null
            ? evento.getSesion().getLugar().getNombre() : null;

        if (evento.getSesion().getTitular() != null) {
            var titular = evento.getSesion().getTitular();
            Long idPersona = titular.getPersona() != null ? titular.getPersona().getIdPersona() : null;
            username = resolverUsername(idPersona, titular.getPerId());
        } else if (evento.getSesion().getInterno() != null) {
            var interno = evento.getSesion().getInterno();
            Long idPersona = interno.getPersona() != null ? interno.getPersona().getIdPersona() : null;
            username = resolverUsername(idPersona, null);
        }

        if (username == null) {
            log.warn("notificarAusenciaPorSenalAlFuncionario: sin usuario para evento id={}", evento.getIdEvento());
            return;
        }

        // Pérdida de señal: NO afirmamos "estás fuera de zona" (no se sabe la posición).
        String mensajeLugar = nombreLugar != null ? " (" + nombreLugar + ")" : "";
        NotificacionDto notif = NotificacionDto.builder()
            .tipo("AUSENCIA_DETECTADA")
            .titulo("⚠️ No recibimos tu ubicación")
            .mensaje("Se registró una ausencia en tu turno" + mensajeLugar
                     + ". Abre SISU y déjala minimizada para reanudar.")
            .timestamp(NotificacionDto.ahora())
            .build();

        Usuario dest = usuarioDao.findByUsername(username).orElse(null);
        persistir(notif, dest, null, null, false);
        enviarWsPersonal(username, notif);
        enviarPush(username, notif.getTitulo(), notif.getMensaje(), notif.getTipo(), "/app/home", true);
        log.info("Notificación de ausencia por señal enviada al funcionario: {}", username);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  CANAL DE SUPERVISIÓN — campana del admin + grupo de Telegram
    // ═══════════════════════════════════════════════════════════════════════
    //
    // Punto único de salida hacia el admin. Antes convivían dos detectores que
    // avisaban lo mismo (AusenciaServiceImpl y MonitoreoSchedulerService) y el
    // primero llamaba "no está en su área asignada" a CUALQUIER causa, incluida
    // la simple pérdida de señal, repitiéndolo cada 15 minutos. Ahora el aviso
    // nace del evento ya clasificado y sale UNA sola vez.

    @Override
    @Transactional
    public void notificarIncidenciaAlAdmin(EventoAbandono evento) {
        if (evento == null || evento.getSesion() == null) return;
        TipoIncidencia tipo = evento.getTipoEfectivo();
        String titulo  = tituloAdminDe(tipo);
        String mensaje = mensajeAdminDe(evento);

        difundirAAdmins("INCIDENCIA_" + tipo.name(), titulo, mensaje);
        // Refresco inmediato del panel de monitoreo (además del mapa de alertas).
        messagingTemplate.convertAndSend("/topic/admin/ausencias", NotificacionDto.builder()
            .tipo("INCIDENCIA_" + tipo.name()).titulo(titulo).mensaje(mensaje)
            .timestamp(NotificacionDto.ahora()).build());
        enviarTelegramIncidencia(evento);
        log.info("Incidencia {} notificada al admin: evento={}", tipo, evento.getIdEvento());
    }

    @Override
    public void notificarRetornoZonaAlAdmin(EventoAbandono evento) {
        if (evento == null || evento.getSesion() == null) return;
        if (!primerEnvioTelegram("RETORNO#" + evento.getIdEvento())) return;
        SesionMonitoreo sesion = evento.getSesion();
        String contexto = contextoSesion(sesion);
        publicarTelegram(TipoMensajeTelegram.RETORNO_ZONA,
            evento.getNombrePersonal(), contexto,
            LocalDateTime.now().format(TG_HORA)
                + " · Estuvo " + evento.getTiempoFueraMinutos() + " min fuera", null,
            "Volvió a su área asignada y su presencia quedó registrada.");
    }

    @Override
    public void notificarSenalPerdidaAlAdmin(SesionMonitoreo sesion, long minutosSinSenal) {
        if (sesion == null) return;
        // Una vez por corte: la clave se olvida en cuanto la señal vuelve, de modo
        // que un segundo corte en el mismo turno sí vuelve a avisar.
        if (!primerEnvioTelegram(claveSenal(sesion))) return;
        String contexto = contextoSesion(sesion);
        String ultima = sesion.getUltimaActualizacion() != null
            ? sesion.getUltimaActualizacion().format(TG_HORA) : "sin registro";
        publicarTelegram(TipoMensajeTelegram.SENAL_PERDIDA,
            nombreDeSesion(sesion), contexto,
            "Última señal: " + ultima + " (hace " + minutosSinSenal + " min)",
            lineaDispositivo(sesion),
            "Dejó de emitir ubicación: GPS desactivado, sin internet o app cerrada.\n"
                + primeraLetraMayus(ultimaPosicionConocida(sesion))
                + ". Se le envió aviso para reactivarla.");
    }

    @Override
    public void notificarSenalRecuperadaAlAdmin(SesionMonitoreo sesion) {
        if (sesion == null) return;
        // Solo se avisa la recuperación si antes se avisó la pérdida.
        if (telegramEnviados.contains(claveSenal(sesion))) {
            olvidarEnvioTelegram(claveSenal(sesion));
            String contexto = contextoSesion(sesion);
            publicarTelegram(TipoMensajeTelegram.SENAL_RECUPERADA,
                nombreDeSesion(sesion), contexto,
                LocalDateTime.now().format(TG_HORA),
                lineaDispositivo(sesion),
                "Volvió a emitir ubicación con normalidad.");
        }
        olvidarEnvioTelegram(claveGps(sesion));
    }

    @Override
    public void notificarGpsNoActivadoAlAdmin(SesionMonitoreo sesion) {
        if (sesion == null) return;
        if (!primerEnvioTelegram(claveGps(sesion))) return;
        String contexto = contextoSesion(sesion);
        publicarTelegram(TipoMensajeTelegram.GPS_NO_ACTIVADO,
            nombreDeSesion(sesion), contexto,
            "Turno iniciado: " + sesion.getTimestampInicio().format(TG_HORA), null,
            "Su turno comenzó y todavía no envió ninguna ubicación."
                + "\nSe le pidió activar el GPS; si no lo hace se registrará falta.");
    }

    private static String claveSenal(SesionMonitoreo sesion) {
        return "SENAL#" + sesion.getIdSesion();
    }

    private static String claveGps(SesionMonitoreo sesion) {
        return "GPSOFF#" + sesion.getIdSesion();
    }

    private static String primeraLetraMayus(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Override
    @Transactional
    public void notificarLogoutEnTurnoAlAdmin(SesionMonitoreo sesion) {
        if (sesion == null) return;

        String username = null;
        String nombreLugar = sesion.getLugar() != null ? sesion.getLugar().getNombre() : null;
        if (sesion.getTitular() != null) {
            var titular = sesion.getTitular();
            Long idPersona = titular.getPersona() != null ? titular.getPersona().getIdPersona() : null;
            username = resolverUsername(idPersona, titular.getPerId());
        } else if (sesion.getInterno() != null) {
            var interno = sesion.getInterno();
            Long idPersona = interno.getPersona() != null ? interno.getPersona().getIdPersona() : null;
            username = resolverUsername(idPersona, null);
        }

        Usuario funcionario = username != null ? usuarioDao.findByUsername(username).orElse(null) : null;
        String nombre = funcionario != null
            ? Stream.of(funcionario.getNombre(), funcionario.getApellido())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" "))
            : "Un funcionario";
        if (nombre.isBlank()) nombre = "Un funcionario";

        String mensajeAdmin = nombre + " cerró sesión durante su turno"
            + (nombreLugar != null ? " — " + nombreLugar : "")
            + ". Se evaluará ausencia si no regresa.";

        List<Usuario> admins = usuarioDao.findActivosByRoles(List.of("ADMIN", "SUPERVISOR"));
        for (Usuario admin : admins) {
            NotificacionDto dto = NotificacionDto.builder()
                .tipo("AUSENCIA_DETECTADA")
                .titulo("🚪 Cerró sesión en turno")
                .mensaje(mensajeAdmin)
                .timestamp(NotificacionDto.ahora())
                .build();
            persistir(dto, admin, null, null, false);
            enviarWsPersonal(admin.getUsername(), dto);
        }
        // Un aviso por cierre de sesión. Si además deja de reportar, el abandono
        // por LOGOUT_EN_TURNO llega después con su propio mensaje.
        if (primerEnvioTelegram("LOGOUT#" + sesion.getIdSesion())) {
            String contexto = contextoSesion(sesion);
            publicarTelegram(TipoMensajeTelegram.LOGOUT_EN_TURNO,
                nombre, contexto,
                LocalDateTime.now().format(TG_HORA), null,
                "Cerró sesión en la app durante su turno. "
                    + "Se evaluará ausencia si no vuelve a reportar.");
        }
        log.info("Logout en turno notificado a {} admin(s): usuario={}", admins.size(), username);
    }

    @Override
    @Transactional
    public void notificarNuevaJustificacionAlAdmin(EventoAbandono evento) {
        if (evento == null || evento.getSesion() == null) return;

        String nombre      = evento.getNombrePersonal();
        String tipoLabel   = evento.getTipoEfectivo().getLabel();
        String nombreLugar = evento.getSesion().getLugar() != null
            ? evento.getSesion().getLugar().getNombre() : null;

        String mensaje = nombre + " envió una justificación de " + tipoLabel.toLowerCase()
            + (nombreLugar != null ? " — " + nombreLugar : "") + ". Pendiente de revisión.";

        List<Usuario> admins = usuarioDao.findActivosByRoles(List.of("ADMIN", "SUPERVISOR"));
        for (Usuario admin : admins) {
            NotificacionDto dto = NotificacionDto.builder()
                .tipo("JUSTIFICACION_PENDIENTE")
                .titulo("📝 Justificación por revisar")
                .mensaje(mensaje)
                .timestamp(NotificacionDto.ahora())
                .build();
            persistir(dto, admin, null, null, false);
            enviarWsPersonal(admin.getUsername(), dto);
        }
        // Broadcast al canal admin de monitoreo para refresco inmediato del panel.
        messagingTemplate.convertAndSend("/topic/admin/alertas-monitoreo",
            NotificacionDto.builder()
                .tipo("JUSTIFICACION_PENDIENTE")
                .titulo("📝 Justificación por revisar")
                .mensaje(mensaje)
                .timestamp(NotificacionDto.ahora())
                .build());
        log.info("Nueva justificación notificada a {} admin(s): evento={}",
            admins.size(), evento.getIdEvento());
    }

    @Override
    @Transactional
    public void notificarJustificacionRevisadaAlFuncionario(EventoAbandono evento, boolean aprobada) {
        if (evento == null || evento.getSesion() == null) return;

        String username = null;
        if (evento.getSesion().getTitular() != null) {
            var titular = evento.getSesion().getTitular();
            Long idPersona = titular.getPersona() != null ? titular.getPersona().getIdPersona() : null;
            username = resolverUsername(idPersona, titular.getPerId());
        } else if (evento.getSesion().getInterno() != null) {
            var interno = evento.getSesion().getInterno();
            Long idPersona = interno.getPersona() != null ? interno.getPersona().getIdPersona() : null;
            username = resolverUsername(idPersona, null);
        }
        if (username == null) {
            log.warn("notificarJustificacionRevisadaAlFuncionario: sin usuario para evento id={}",
                evento.getIdEvento());
            return;
        }

        String tipoLabel = evento.getTipoEfectivo().getLabel();
        String obs       = evento.getObservacionRevision();
        NotificacionDto notif = NotificacionDto.builder()
            .tipo(aprobada ? "JUSTIFICACION_APROBADA" : "JUSTIFICACION_RECHAZADA")
            .titulo(aprobada ? "✅ Justificación aprobada" : "❌ Justificación rechazada")
            .mensaje("Tu justificación de " + tipoLabel.toLowerCase() + " fue "
                + (aprobada ? "aprobada." : "rechazada.")
                + (obs != null && !obs.isBlank() ? " Observación: " + obs : ""))
            .timestamp(NotificacionDto.ahora())
            .build();

        Usuario dest = usuarioDao.findByUsername(username).orElse(null);
        persistir(notif, dest, null, null, false);
        enviarWsPersonal(username, notif);
        // Categoría "gestión": información que el usuario espera, llega aunque la app esté cerrada.
        enviarPush(username, notif.getTitulo(), notif.getMensaje(), notif.getTipo(), "/app/faltas", false);
        log.info("Justificación revisada notificada al funcionario {}: aprobada={}", username, aprobada);
    }

    @Override
    @Transactional
    public void notificarIncidenciaAlFuncionario(EventoAbandono evento) {
        if (evento == null || evento.getSesion() == null) return;

        String username = null;
        if (evento.getSesion().getTitular() != null) {
            var titular = evento.getSesion().getTitular();
            Long idPersona = titular.getPersona() != null ? titular.getPersona().getIdPersona() : null;
            username = resolverUsername(idPersona, titular.getPerId());
        } else if (evento.getSesion().getInterno() != null) {
            var interno = evento.getSesion().getInterno();
            Long idPersona = interno.getPersona() != null ? interno.getPersona().getIdPersona() : null;
            username = resolverUsername(idPersona, null);
        }
        if (username == null) {
            log.warn("notificarIncidenciaAlFuncionario: sin usuario para evento id={}", evento.getIdEvento());
            return;
        }

        TipoIncidencia tipo = evento.getTipoEfectivo();
        Integer min = evento.getMinutosIncidencia();
        String titulo;
        String mensaje;
        switch (tipo) {
            case ATRASO -> {
                titulo  = "⏰ Atraso registrado";
                mensaje = "Se registró un atraso" + (min != null ? " de " + min + " min" : "")
                        + " al iniciar tu turno. Puedes justificarlo desde la app.";
            }
            case FALTA -> {
                titulo  = "🚫 Falta registrada";
                mensaje = "No recibimos tu ubicación durante el turno y se registró una falta. "
                        + "Si fue un error, puedes justificarla desde la app.";
            }
            case SALIDA_TEMPRANA -> {
                titulo  = "↩️ Salida temprana";
                mensaje = "Se registró una salida temprana de tu turno" + (min != null ? " (" + min + " min antes)" : "")
                        + ". Puedes justificarla desde la app.";
            }
            default -> {
                titulo  = "⚠️ Incidencia registrada";
                mensaje = evento.getObservaciones() != null ? evento.getObservaciones()
                        : "Se registró una incidencia en tu turno.";
            }
        }

        NotificacionDto notif = NotificacionDto.builder()
            .tipo("INCIDENCIA_REGISTRADA")
            .titulo(titulo)
            .mensaje(mensaje)
            .timestamp(NotificacionDto.ahora())
            .build();

        Usuario dest = usuarioDao.findByUsername(username).orElse(null);
        persistir(notif, dest, null, null, false);
        enviarWsPersonal(username, notif);
        enviarPush(username, notif.getTitulo(), notif.getMensaje(), notif.getTipo(), "/app/faltas", true);
        log.info("Incidencia {} notificada al funcionario {}", tipo, username);
    }

    /**
     * @deprecated Aviso genérico de ausencia: no sabe la causa, así que llamaba
     *     "no está en su área asignada" tanto a una salida real como a un GPS
     *     apagado. Sustituido por {@link #notificarIncidenciaAlAdmin}, que parte
     *     del evento ya clasificado. Se conserva por compatibilidad y ya no
     *     publica en Telegram.
     */
    @Deprecated
    @Override
    @Transactional
    public void notificarAusencia(Usuario usuario, Jornada jornada) {
        String nombre = Stream.of(usuario.getNombre(), usuario.getApellido())
            .filter(s -> s != null && !s.isBlank())
            .collect(Collectors.joining(" "));

        String mensajeAdmin = nombre + " no está en su área asignada"
            + (jornada != null ? " — " + jornada.getNombre() : "");

        List<Usuario> admins = usuarioDao.findActivosByRoles(List.of("ADMIN", "SUPERVISOR"));
        for (Usuario admin : admins) {
            NotificacionDto dto = NotificacionDto.builder()
                .tipo("AUSENCIA_DETECTADA")
                .titulo("⚠️ Ausencia detectada")
                .mensaje(mensajeAdmin)
                .timestamp(NotificacionDto.ahora())
                .build();
            persistir(dto, admin, null, null, false);
            enviarWsPersonal(admin.getUsername(), dto);
        }

        // Broadcast adicional al tópico para refresco inmediato del panel de monitoreo
        NotificacionDto broadcast = NotificacionDto.builder()
            .tipo("AUSENCIA_DETECTADA")
            .titulo("⚠️ Ausencia detectada")
            .mensaje(mensajeAdmin)
            .timestamp(NotificacionDto.ahora())
            .build();
        messagingTemplate.convertAndSend("/topic/admin/ausencias", broadcast);
        log.info("Ausencia notificada y persistida para {} admin(s): {}", admins.size(), usuario.getUsername());
    }

    @Override
    @Transactional
    public void notificarSalidaZonaAlFuncionario(EventoAbandono evento) {
        if (evento.getSesion() == null) return;

        String username = null;
        String nombreLugar = evento.getSesion().getLugar() != null
            ? evento.getSesion().getLugar().getNombre() : null;

        if (evento.getSesion().getTitular() != null) {
            var titular = evento.getSesion().getTitular();
            Long idPersona = titular.getPersona() != null ? titular.getPersona().getIdPersona() : null;
            username = resolverUsername(idPersona, titular.getPerId());
        } else if (evento.getSesion().getInterno() != null) {
            var interno = evento.getSesion().getInterno();
            Long idPersona = interno.getPersona() != null ? interno.getPersona().getIdPersona() : null;
            username = resolverUsername(idPersona, null);
        }

        if (username == null) {
            log.warn("notificarSalidaZonaAlFuncionario: sin usuario para evento id={}", evento.getIdEvento());
            return;
        }

        String dest = nombreLugar != null ? " de " + nombreLugar : " de tu área de trabajo";
        NotificacionDto notif = NotificacionDto.builder()
            .tipo("AUSENCIA_DETECTADA")
            .titulo("⚠️ Saliste de tu zona laboral")
            .mensaje("Saliste" + dest + ". Por favor regresa a tu puesto.")
            .timestamp(NotificacionDto.ahora())
            .build();

        Usuario destinatario = usuarioDao.findByUsername(username).orElse(null);
        persistir(notif, destinatario, null, null, false);
        enviarWsPersonal(username, notif);
        enviarPush(username, notif.getTitulo(), notif.getMensaje(), notif.getTipo(), "/app/home", true);
        log.info("Notificación de salida de zona enviada al funcionario: {}", username);
    }

    @Override
    @Transactional
    public void notificarRetornoZonaAlFuncionario(EventoAbandono evento) {
        if (evento.getSesion() == null) return;

        String username = null;
        String nombreLugar = evento.getSesion().getLugar() != null
            ? evento.getSesion().getLugar().getNombre() : null;

        if (evento.getSesion().getTitular() != null) {
            var titular = evento.getSesion().getTitular();
            Long idPersona = titular.getPersona() != null ? titular.getPersona().getIdPersona() : null;
            username = resolverUsername(idPersona, titular.getPerId());
        } else if (evento.getSesion().getInterno() != null) {
            var interno = evento.getSesion().getInterno();
            Long idPersona = interno.getPersona() != null ? interno.getPersona().getIdPersona() : null;
            username = resolverUsername(idPersona, null);
        }

        if (username == null) {
            log.warn("notificarRetornoZonaAlFuncionario: sin usuario para evento id={}", evento.getIdEvento());
            return;
        }

        String dest = nombreLugar != null ? " a " + nombreLugar : " a tu área de trabajo";
        NotificacionDto notif = NotificacionDto.builder()
            .tipo("RETORNO_ZONA")
            .titulo("✅ Volviste a tu zona")
            .mensaje("Regresaste" + dest + ". Tu presencia quedó registrada.")
            .timestamp(NotificacionDto.ahora())
            .build();

        Usuario destinatario = usuarioDao.findByUsername(username).orElse(null);
        persistir(notif, destinatario, null, null, false);
        enviarWsPersonal(username, notif);
        enviarPush(username, notif.getTitulo(), notif.getMensaje(), notif.getTipo(), "/app/home", false);
        log.info("Notificación de retorno a zona enviada al funcionario: {}", username);
    }

    @Override
    @Transactional
    public void notificarGpsDenegadoAlFuncionario(SesionMonitoreo sesion) {
        if (sesion == null) return;

        String username    = null;
        String nombreLugar = sesion.getLugar() != null ? sesion.getLugar().getNombre() : null;

        if (sesion.getTitular() != null) {
            var titular = sesion.getTitular();
            Long idPersona = titular.getPersona() != null ? titular.getPersona().getIdPersona() : null;
            username = resolverUsername(idPersona, titular.getPerId());
        } else if (sesion.getInterno() != null) {
            var interno = sesion.getInterno();
            Long idPersona = interno.getPersona() != null ? interno.getPersona().getIdPersona() : null;
            username = resolverUsername(idPersona, null);
        }

        if (username == null) {
            log.warn("notificarGpsDenegadoAlFuncionario: sin usuario para sesión id={}", sesion.getIdSesion());
            return;
        }

        NotificacionDto notif = NotificacionDto.builder()
            .tipo("GPS_DENEGADO")
            .titulo("📍 Reactiva tu ubicación")
            .mensaje("Dejamos de recibir tu GPS. Abre SISU y déjala minimizada"
                     + " (no la cierres) o contará como ausencia.")
            .timestamp(NotificacionDto.ahora())
            .build();

        Usuario dest = usuarioDao.findByUsername(username).orElse(null);
        persistir(notif, dest, null, null, false);
        enviarWsPersonal(username, notif);
        enviarPush(username, notif.getTitulo(), notif.getMensaje(), notif.getTipo(), "/app/home", true);
        log.info("Notificación GPS denegado enviada al funcionario: {}", username);
    }

    @Override
    @Transactional
    public void notificarRecordatorioTurno(SesionMonitoreo sesion) {
        if (sesion == null) return;

        String username    = null;
        String nombreLugar = sesion.getLugar() != null ? sesion.getLugar().getNombre() : null;

        if (sesion.getTitular() != null) {
            var titular = sesion.getTitular();
            Long idPersona = titular.getPersona() != null ? titular.getPersona().getIdPersona() : null;
            username = resolverUsername(idPersona, titular.getPerId());
        } else if (sesion.getInterno() != null) {
            var interno = sesion.getInterno();
            Long idPersona = interno.getPersona() != null ? interno.getPersona().getIdPersona() : null;
            username = resolverUsername(idPersona, null);
        }

        if (username == null) {
            log.warn("notificarRecordatorioTurno: sin usuario para sesión id={}", sesion.getIdSesion());
            return;
        }

        String lugar = nombreLugar != null ? " en " + nombreLugar : "";
        NotificacionDto notif = NotificacionDto.builder()
            .tipo("RECORDATORIO_TURNO")
            .titulo("📍 Hoy tienes turno")
            .mensaje("Tu turno" + lugar + " ha comenzado. Activa tu ubicación en la app"
                     + " para registrar tu presencia.")
            .timestamp(NotificacionDto.ahora())
            .build();

        Usuario dest = usuarioDao.findByUsername(username).orElse(null);
        persistir(notif, dest, null, null, false);
        enviarWsPersonal(username, notif);
        // Push urgente: debe llegar aunque la app esté cerrada o el teléfono bloqueado.
        enviarPush(username, notif.getTitulo(), notif.getMensaje(), notif.getTipo(), "/app/home", true);
        log.info("Recordatorio de turno enviado al funcionario: {}", username);
    }

    @Override
    @Transactional
    public void notificarFinTurno(SesionMonitoreo sesion) {
        if (sesion == null) return;

        String username    = resolverUsernameDeSesion(sesion);
        String nombreLugar = sesion.getLugar() != null ? sesion.getLugar().getNombre() : null;

        if (username == null) {
            log.warn("notificarFinTurno: sin usuario para sesión id={}", sesion.getIdSesion());
            return;
        }

        String lugar = nombreLugar != null ? " en " + nombreLugar : "";
        NotificacionDto notif = NotificacionDto.builder()
            .tipo("FIN_TURNO")
            .titulo("✅ Turno finalizado")
            .mensaje("Tu turno" + lugar + " ha terminado. El registro de ubicación se"
                     + " detuvo y ya no se controlará tu presencia hasta tu próximo turno.")
            .timestamp(NotificacionDto.ahora())
            .build();

        Usuario dest = usuarioDao.findByUsername(username).orElse(null);
        persistir(notif, dest, null, null, false);
        enviarWsPersonal(username, notif);
        // Push NO urgente: es informativo y suele llegar al salir del trabajo, así que
        // no debe sonar como una alerta de control.
        enviarPush(username, notif.getTitulo(), notif.getMensaje(), notif.getTipo(), "/app/home", false);
        log.info("Aviso de fin de turno enviado al funcionario: {}", username);
    }

    /** Resuelve el username del titular o interno dueño de la sesión de monitoreo. */
    private String resolverUsernameDeSesion(SesionMonitoreo sesion) {
        if (sesion.getTitular() != null) {
            var titular = sesion.getTitular();
            Long idPersona = titular.getPersona() != null ? titular.getPersona().getIdPersona() : null;
            return resolverUsername(idPersona, titular.getPerId());
        }
        if (sesion.getInterno() != null) {
            var interno = sesion.getInterno();
            Long idPersona = interno.getPersona() != null ? interno.getPersona().getIdPersona() : null;
            return resolverUsername(idPersona, null);
        }
        return null;
    }

    @Override
    @Transactional
    public void notificarCambioJornada(Jornada jornada) {
        if (jornada == null) return;

        // Un mismo usuario no debería recibir dos avisos si figura por más de una vía.
        java.util.Set<String> destinatarios = new java.util.LinkedHashSet<>();

        titularJornadaDao.findByJornada_IdJornada(jornada.getIdJornada()).forEach(tj -> {
            var titular = tj.getTitular();
            if (titular == null) return;
            Long idPersona = titular.getPersona() != null ? titular.getPersona().getIdPersona() : null;
            String u = resolverUsername(idPersona, titular.getPerId());
            if (u != null) destinatarios.add(u);
        });

        internoJornadaDao.findByJornada_IdJornada(jornada.getIdJornada()).forEach(ij -> {
            var interno = ij.getInterno();
            if (interno == null) return;
            Long idPersona = interno.getPersona() != null ? interno.getPersona().getIdPersona() : null;
            String u = resolverUsername(idPersona, null);
            if (u != null) destinatarios.add(u);
        });

        if (destinatarios.isEmpty()) {
            log.info("notificarCambioJornada: jornada {} sin personal asignado", jornada.getIdJornada());
            return;
        }

        String horario = jornada.getHoraInicio() + " a " + jornada.getHoraFin();
        for (String username : destinatarios) {
            NotificacionDto notif = NotificacionDto.builder()
                .tipo("JORNADA_ACTUALIZADA")
                .titulo("🕒 Tu turno cambió")
                .mensaje("Se actualizó el horario de " + jornada.getNombre()
                         + ". Nuevo horario: " + horario + ".")
                .timestamp(NotificacionDto.ahora())
                .idJornada(jornada.getIdJornada())
                .build();

            Usuario dest = usuarioDao.findByUsername(username).orElse(null);
            persistir(notif, dest, null, null, false);
            enviarWsPersonal(username, notif);
            // Push urgente: el usuario puede estar por presentarse a un horario que ya
            // no existe, así que debe llegar aunque tenga la app cerrada.
            enviarPush(username, notif.getTitulo(), notif.getMensaje(), notif.getTipo(), "/app/turnos", true);
        }
        log.info("Cambio de jornada {} notificado a {} funcionario(s)",
            jornada.getIdJornada(), destinatarios.size());
    }

    @Override
    @Transactional
    public void notificarAsignacionJornada(TitularJornada tj) {
        Long idPersona = tj.getTitular().getPersona().getIdPersona();
        String username = resolverUsername(idPersona, tj.getTitular().getPerId());
        if (username == null) {
            log.warn("WS: sin usuario para titular idPersona={} perId={} — omitiendo",
                idPersona, tj.getTitular().getPerId());
            return;
        }

        Jornada j = tj.getJornada();
        NotificacionDto notif = NotificacionDto.builder()
            .tipo("JORNADA_ASIGNADA")
            .titulo("📋 Jornada asignada")
            .mensaje("Fuiste asignado a la jornada: " + j.getNombre()
                     + " (" + j.getFechaInicio() + ")")
            .timestamp(NotificacionDto.ahora())
            .idJornada(j.getIdJornada())
            .build();

        Usuario dest = usuarioDao.findByUsername(username).orElse(null);
        persistir(notif, dest, null, null, false);
        enviarWsPersonal(username, notif);
        enviarPush(username, notif.getTitulo(), notif.getMensaje(), notif.getTipo(), "/app/turnos", false);
    }

    @Override
    @Transactional
    public void notificarAsignacionJornada(InternoJornada ij) {
        Long idPersona = ij.getInterno().getPersona().getIdPersona();
        String username = resolverUsername(idPersona, null);
        if (username == null) {
            log.warn("WS: sin usuario para interno idPersona={} — omitiendo", idPersona);
            return;
        }

        Jornada j = ij.getJornada();
        NotificacionDto notif = NotificacionDto.builder()
            .tipo("JORNADA_ASIGNADA")
            .titulo("📋 Jornada asignada")
            .mensaje("Fuiste asignado a la jornada: " + j.getNombre()
                     + " (" + j.getFechaInicio() + ")")
            .timestamp(NotificacionDto.ahora())
            .idJornada(j.getIdJornada())
            .build();

        Usuario dest = usuarioDao.findByUsername(username).orElse(null);
        persistir(notif, dest, null, null, false);
        enviarWsPersonal(username, notif);
        enviarPush(username, notif.getTitulo(), notif.getMensaje(), notif.getTipo(), "/app/turnos", false);
    }

    @Override
    @Transactional
    public void notificarHorarioAsignado(HorarioInterno horario) {
        Long idPersona = horario.getInternoJornada().getInterno().getPersona().getIdPersona();
        String username = resolverUsername(idPersona, null);
        if (username == null) {
            log.warn("WS: sin usuario para interno idPersona={} — omitiendo notif horario", idPersona);
            return;
        }
        Jornada j        = horario.getInternoJornada().getJornada();
        int totalDias    = horario.getDias().size();
        NotificacionDto notif = NotificacionDto.builder()
            .tipo("HORARIO_ASIGNADO")
            .titulo("🗓 Horario asignado")
            .mensaje("Se configuraron " + totalDias + " día(s) de asistencia para: "
                     + (j.getNombre() != null ? j.getNombre() : "la jornada"))
            .timestamp(NotificacionDto.ahora())
            .idJornada(j.getIdJornada())
            .build();
        Usuario dest = usuarioDao.findByUsername(username).orElse(null);
        persistir(notif, dest, null, null, false);
        enviarWsPersonal(username, notif);
        enviarPush(username, notif.getTitulo(), notif.getMensaje(), notif.getTipo(), "/app/turnos", false);
    }

    @Override
    @Transactional
    public void notificarAsignacionActividad(ActividadJornada aj) {
        Long idPersona;
        Long idJornada;
        String nombreJornada;
        Long titularPerId = null;

        if (aj.esDeInterno()) {
            InternoJornada ij = aj.getInternoJornada();
            idPersona     = ij.getInterno().getPersona().getIdPersona();
            idJornada     = ij.getJornada().getIdJornada();
            nombreJornada = ij.getJornada().getNombre();
        } else {
            TitularJornada tj = aj.getTitularJornada();
            idPersona     = tj.getTitular().getPersona().getIdPersona();
            idJornada     = tj.getJornada().getIdJornada();
            nombreJornada = tj.getJornada().getNombre();
            titularPerId  = tj.getTitular().getPerId();
        }

        String username = resolverUsername(idPersona, titularPerId);
        if (username == null) {
            log.warn("WS: sin usuario para idPersona={} perId={} — omitiendo notif actividad",
                idPersona, titularPerId);
            return;
        }

        NotificacionDto notif = NotificacionDto.builder()
            .tipo("ACTIVIDAD_ASIGNADA")
            .titulo("✅ Nueva actividad asignada")
            .mensaje("Se te asignó: " + aj.getActividad().getNombre()
                     + " — " + nombreJornada)
            .timestamp(NotificacionDto.ahora())
            .idJornada(idJornada)
            .build();

        Usuario dest = usuarioDao.findByUsername(username).orElse(null);
        persistir(notif, dest, null, aj.getIdActividadJornada(), false);
        enviarWsPersonal(username, notif);
        enviarPush(username, notif.getTitulo(), notif.getMensaje(), notif.getTipo(), "/app/actividades", false);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  API PÚBLICA
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public Page<NotificacionResponse> getMisNotificaciones(String username, Pageable pageable) {
        Usuario usuario = usuarioDao.findByUsername(username)
            .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + username));
        return notificacionDao
            .findMisNotificaciones(usuario.getIdUsuario(), pageable)
            .map(NotificacionResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public long getCountNoLeidas(String username) {
        return usuarioDao.findByUsername(username)
            .map(u -> notificacionDao.countNoLeidasPersonales(u.getIdUsuario()))
            .orElse(0L);
    }

    @Override
    @Transactional
    public NotificacionResponse marcarLeida(Long idNotificacion, String username) {
        Notificacion n = notificacionDao.findById(idNotificacion)
            .orElseThrow(() -> new EntityNotFoundException(
                "Notificación no encontrada: " + idNotificacion));

        // Solo el destinatario puede marcar su notificación personal
        if (!n.isEsGlobal()) {
            if (n.getDestinatario() == null ||
                !n.getDestinatario().getUsername().equals(username))
                throw new AccessDeniedException("No tienes permiso para esta notificación");
        }

        if (!n.isLeida()) {
            n.setLeida(true);
            n.setFechaLectura(LocalDateTime.now());
            n = notificacionDao.save(n);
        }
        return NotificacionResponse.from(n);
    }

    @Override
    @Transactional
    public int marcarTodasLeidas(String username) {
        Usuario usuario = usuarioDao.findByUsername(username)
            .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + username));
        return notificacionDao.marcarTodasLeidasUsuario(usuario.getIdUsuario());
    }

    @Override
    @Transactional
    public NotificacionResponse enviarPersonalizado(EnviarNotificacionRequest request,
                                                    String usernameRemitente) {
        Usuario remitente = usuarioDao.findByUsername(usernameRemitente).orElse(null);
        boolean esGlobal  = request.getUsernameDestinatario() == null
                         || request.getUsernameDestinatario().isBlank();

        NotificacionDto dto = NotificacionDto.builder()
            .tipo(request.getTipo().isBlank() ? "CUSTOM" : request.getTipo())
            .titulo(request.getTitulo())
            .mensaje(request.getMensaje())
            .timestamp(NotificacionDto.ahora())
            .idJornada(request.getIdJornada())
            .idSolicitud(request.getIdSolicitud())
            .build();

        if (esGlobal) {
            // Broadcast a todos los usuarios conectados
            persistir(dto, null, remitente, request.getIdActividadJornada(), true);
            messagingTemplate.convertAndSend("/topic/notificaciones/broadcast", dto);
            log.info("Broadcast enviado por {}: {}", usernameRemitente, dto.getTitulo());
        } else {
            Usuario dest = usuarioDao.findByUsername(request.getUsernameDestinatario())
                .orElseThrow(() -> new EntityNotFoundException(
                    "Usuario destinatario no encontrado: " + request.getUsernameDestinatario()));
            persistir(dto, dest, remitente, request.getIdActividadJornada(), false);
            enviarWsPersonal(dest.getUsername(), dto);
            // Push: la notificación manual debe llegar aunque el usuario esté
            // fuera de la app (WS caído). Antes solo se enviaba por WS → se perdía.
            enviarPush(dest.getUsername(), dto.getTitulo(), dto.getMensaje(),
                       dto.getTipo(), PUSH_URL.getOrDefault(dto.getTipo(), "/app/notificaciones"), false);
        }

        // Recuperar la entidad guardada para retornar con todos los datos
        return notificacionDao.findById(dto.getIdNotificacion())
            .map(NotificacionResponse::from)
            .orElseThrow();
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificacionResponse> getNotificacionesDePersonal(
        String tipo, Long id, int limit
    ) {
        final Long idPersona;
        final Long perId;

        if ("INTERNO".equalsIgnoreCase(tipo)) {
            var interno = internoDao.findById(id).orElse(null);
            if (interno == null || interno.getPersona() == null) return List.of();
            idPersona = interno.getPersona().getIdPersona();
            perId     = null;
        } else {
            var titular = titularDao.findById(id).orElse(null);
            if (titular == null || titular.getPersona() == null) return List.of();
            idPersona = titular.getPersona().getIdPersona();
            // Los titulares importados de Londra pueden tener el usuario ligado por
            // perId en vez de por la persona local.
            perId     = titular.getPerId();
        }

        Usuario destinatario = usuarioDao.findByPersonaIdPersona(idPersona)
            .or(() -> perIdFinal(perId))
            .orElse(null);
        if (destinatario == null) return List.of();

        return notificacionDao
            .findEnviadasAUsuario(destinatario.getIdUsuario(),
                                  PageRequest.of(0, Math.max(1, limit)))
            .stream()
            .map(NotificacionResponse::from)
            .toList();
    }

    /** Fallback de resolución por perId de Londra; vacío si no hay perId. */
    private Optional<Usuario> perIdFinal(Long perId) {
        return perId == null ? Optional.empty() : usuarioDao.findByPerIdAndActivoTrue(perId);
    }
}
