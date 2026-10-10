package com.uap.controlMedico.model.service.monitoreo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uap.controlMedico.model.dao.ConfiguracionMonitoreoDao;
import com.uap.controlMedico.model.dao.EventoAbandonoDao;
import com.uap.controlMedico.model.dao.InternoJornadaDao;
import com.uap.controlMedico.model.dao.SesionMonitoreoDao;
import com.uap.controlMedico.model.dao.TitularJornadaDao;
import com.uap.controlMedico.model.dto.monitoreo.AlertaMonitoreoDto;
import com.uap.controlMedico.model.entity.ConfiguracionMonitoreo;
import com.uap.controlMedico.model.entity.EventoAbandono;
import com.uap.controlMedico.model.entity.InternoJornada;
import com.uap.controlMedico.model.entity.Jornada;
import com.uap.controlMedico.model.entity.Lugar;
import com.uap.controlMedico.model.entity.SesionMonitoreo;
import com.uap.controlMedico.model.entity.TitularJornada;
import com.uap.controlMedico.model.enums.EstadoAsignacion;
import com.uap.controlMedico.model.enums.EstadoEvento;
import com.uap.controlMedico.model.enums.EstadoJornada;
import com.uap.controlMedico.model.enums.EstadoJustificacion;
import com.uap.controlMedico.model.enums.EstadoZona;
import com.uap.controlMedico.model.enums.TipoIncidencia;
import com.uap.controlMedico.model.service.notificacion.NotificacionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class MonitoreoSchedulerService {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final TitularJornadaDao        titularJornadaDao;
    private final InternoJornadaDao        internoJornadaDao;
    private final SesionMonitoreoDao       sesionDao;
    private final EventoAbandonoDao        eventoDao;
    private final ConfiguracionMonitoreoDao configDao;
    private final SimpMessagingTemplate    messaging;
    private final NotificacionService      notificacionService;

    /**
     * IDs de sesiones a las que ya avisamos "sin señal" — evita repetir el aviso
     * cada 30s. Se vacía cuando la sesión vuelve a reportar o se cierra.
     */
    private final java.util.Set<Long> sinSenalNotificados =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * IDs de sesiones a las que ya avisamos "activá el GPS" (nunca reportaron).
     * La ventana de aviso (5–14 min desde el inicio) es más ancha que el periodo
     * del job (5 min), así que sin esto la misma sesión se avisaba dos veces.
     */
    private final java.util.Set<Long> gpsPendienteNotificados =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Momento de arranque del backend. Sirve como periodo de "calentamiento": tras
     * un reinicio, las sesiones activas conservan la ultimaActualizacion de ANTES
     * del reinicio; sin esta gracia, los avisos de "activá tu GPS" / abandono se
     * dispararían al instante aunque el usuario tenga el GPS perfecto. Damos unos
     * minutos para que los dispositivos vuelvan a reportar antes de alarmar.
     */
    private final LocalDateTime arranque = LocalDateTime.now();
    private static final long WARMUP_SEGUNDOS = 180;

    private boolean enCalentamiento() {
        return java.time.Duration.between(arranque, LocalDateTime.now()).getSeconds() < WARMUP_SEGUNDOS;
    }

    /**
     * Cada minuto: crea SesionMonitoreo para personal que entra en turno
     * y cierra las de quienes ya terminaron.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void gestionarSesiones() {
        LocalDate hoy   = LocalDate.now();
        LocalTime ahora = LocalTime.now();

        // 1. Crear sesiones para titulares que están en turno ahora y no tienen sesión activa
        List<TitularJornada> titulares = titularJornadaDao.findAllInActiveJornadas();
        for (TitularJornada tj : titulares) {
            if (!tj.estaEnTurnoAhora(hoy, ahora)) continue;
            Long idTitular = tj.getTitular().getIdTitular();
            if (!sesionDao.existsByTitular_IdTitularAndActivaTrue(idTitular)) {
                Lugar lugar = tj.getLugar() != null ? tj.getLugar()
                    : (tj.getJornada().getLugar() != null ? tj.getJornada().getLugar() : null);
                SesionMonitoreo sesion = SesionMonitoreo.builder()
                    .titular(tj.getTitular())
                    .jornada(tj.getJornada())
                    .lugar(lugar)
                    .estadoZona(EstadoZona.DESCONOCIDO)
                    .activa(true)
                    .build();
                // Se consulta ANTES de guardar: si no, la sesión recién creada ya
                // cuenta como "sesión previa de hoy" y nunca se avisaría.
                boolean primeraDelDia = !sesionDao
                    .existsByTitular_IdTitularAndJornada_IdJornadaAndTimestampInicioBetween(
                        idTitular, tj.getJornada().getIdJornada(),
                        hoy.atStartOfDay(), hoy.plusDays(1).atStartOfDay());
                sesionDao.save(sesion);
                log.info("Sesión iniciada para titular id={}", idTitular);
                // Recordatorio de inicio de turno: avisa que hoy le toca y debe
                // activar su ubicación (llega por push aunque la app esté cerrada).
                // Solo en la PRIMERA sesión del día de este turno: las recreaciones
                // tras un cierre por falta o inactividad no son un turno nuevo.
                if (primeraDelDia) notificacionService.notificarRecordatorioTurno(sesion);
            }
        }

        // 2. Crear sesiones para internos en turno sin sesión activa
        List<InternoJornada> internos = internoJornadaDao.findAllInActiveJornadas();
        for (InternoJornada ij : internos) {
            if (!estaEnHorario(ij, hoy, ahora)) continue;
            Long idInterno = ij.getInterno().getIdInterno();
            if (!sesionDao.existsByInterno_IdInternoAndActivaTrue(idInterno)) {
                Lugar lugar = ij.getLugar() != null ? ij.getLugar()
                    : (ij.getJornada().getLugar() != null ? ij.getJornada().getLugar() : null);
                SesionMonitoreo sesion = SesionMonitoreo.builder()
                    .interno(ij.getInterno())
                    .jornada(ij.getJornada())
                    .lugar(lugar)
                    .estadoZona(EstadoZona.DESCONOCIDO)
                    .activa(true)
                    .build();
                boolean primeraDelDia = !sesionDao
                    .existsByInterno_IdInternoAndJornada_IdJornadaAndTimestampInicioBetween(
                        idInterno, ij.getJornada().getIdJornada(),
                        hoy.atStartOfDay(), hoy.plusDays(1).atStartOfDay());
                sesionDao.save(sesion);
                log.info("Sesión iniciada para interno id={}", idInterno);
                // Recordatorio de inicio de turno (push aunque la app esté cerrada),
                // solo en la primera sesión del día — ver el caso del titular arriba.
                if (primeraDelDia) notificacionService.notificarRecordatorioTurno(sesion);
            }
        }

        // 3. Cerrar sesiones cuyo turno ya terminó
        List<SesionMonitoreo> activas = sesionDao.findByActivaTrue();
        for (SesionMonitoreo sesion : activas) {
            boolean turnoActivo = false;
            if (sesion.getTitular() != null) {
                turnoActivo = titulares.stream()
                    .filter(tj -> tj.getTitular().getIdTitular()
                                    .equals(sesion.getTitular().getIdTitular()))
                    .anyMatch(tj -> tj.estaEnTurnoAhora(hoy, ahora));
                titulares.stream()
                    .filter(tj -> tj.getTitular().getIdTitular()
                                    .equals(sesion.getTitular().getIdTitular()))
                    .findFirst()
                    .ifPresent(tj -> sincronizarLugar(sesion, tj.getLugar(), tj.getJornada()));
            } else if (sesion.getInterno() != null) {
                turnoActivo = internos.stream()
                    .filter(ij -> ij.getInterno().getIdInterno()
                                    .equals(sesion.getInterno().getIdInterno()))
                    .anyMatch(ij -> estaEnHorario(ij, hoy, ahora));
                internos.stream()
                    .filter(ij -> ij.getInterno().getIdInterno()
                                    .equals(sesion.getInterno().getIdInterno()))
                    .findFirst()
                    .ifPresent(ij -> sincronizarLugar(sesion, ij.getLugar(), ij.getJornada()));
            }
            if (!turnoActivo) {
                cerrarSesion(sesion);
                // Pausar el monitoreo también deja turnoActivo en false, pero NO es un
                // fin de turno: avisar "turno finalizado" al pausar le diría a toda la
                // gente que su jornada terminó cuando en realidad sigue trabajando.
                boolean pausado = sesion.getJornada() != null
                    && sesion.getJornada().isMonitoreoPausado();
                if (pausado) {
                    log.info("Sesión id={} cerrada en silencio: monitoreo pausado en la jornada {}",
                        sesion.getIdSesion(), sesion.getJornada().getIdJornada());
                    continue;
                }
                // Único cierre que es un fin de turno NORMAL (el horario terminó). Los
                // otros caminos de cerrarSesion() son falta o inactividad y no deben
                // avisar "turno finalizado": ocultarían el problema al funcionario.
                try {
                    notificacionService.notificarFinTurno(sesion);
                } catch (Exception e) {
                    log.warn("No se pudo notificar el fin de turno de la sesión id={}: {}",
                        sesion.getIdSesion(), e.getMessage());
                }
            }
        }
    }

    /**
     * Re-apunta el lugar de una sesión abierta si la asignación cambió de sede.
     *
     * El lugar se copiaba al abrir la sesión y no se volvía a mirar, así que a quien
     * le cambiaban de sede con el turno en curso le seguían saliendo alertas citando
     * la sede anterior. Cubre a quien no está mandando GPS; el camino con GPS se
     * corrige en UbicacionServiceImpl al llegar el siguiente fix.
     */
    private void sincronizarLugar(SesionMonitoreo sesion, Lugar lugarAsignacion, Jornada jornada) {
        Lugar esperado = lugarAsignacion != null
            ? lugarAsignacion
            : (jornada != null ? jornada.getLugar() : null);

        Long idActual   = sesion.getLugar() != null ? sesion.getLugar().getIdLugar() : null;
        Long idEsperado = esperado != null ? esperado.getIdLugar() : null;
        if (Objects.equals(idActual, idEsperado)) return;

        log.info("Sesión {}: lugar re-apuntado {} → {}",
            sesion.getIdSesion(),
            sesion.getLugar() != null ? sesion.getLugar().getNombre() : "(sin lugar)",
            esperado != null ? esperado.getNombre() : "(sin lugar)");
        sesion.setLugar(esperado);
        sesionDao.save(sesion);
    }

    /**
     * Cada 5 minutos: cierra sesiones obsoletas y, cuando corresponde, registra FALTA.
     *  - Caso 1 (FALTA): nunca llegó un solo ping (DESCONOCIDO + ultimaActualizacion null)
     *    pasado el umbral de falta → la persona no se presentó. Se registra FALTA y se cierra.
     *  - Caso 2: la última señal lleva +30 min (cerró el browser sin logout) → solo cerrar.
     */
    @Scheduled(fixedDelay = 300_000)
    @Transactional
    public void limpiarSesionesObsoletas() {
        if (enCalentamiento()) return;   // tras reinicio, esperar a que re-reporten
        ConfiguracionMonitoreo config = getConfig();
        int umbralFaltaMin = config.getUmbralFaltaMin() != null ? config.getUmbralFaltaMin() : 20;
        LocalDateTime limiteFalta    = LocalDateTime.now().minusMinutes(umbralFaltaMin);
        // La sesión debe sobrevivir a AMBOS detectores: el de abandono de zona y el
        // de pérdida de señal. Cerrarla justo en el umbral de abandono era una
        // carrera contra detectarAbandonos, y cerrarla antes de tiempo hace que el
        // scheduler la recree, lo que reabre la puerta a incidencias duplicadas.
        int umbralAbandonoMin = config.getTimeoutAbandonoMinutos() != null
            ? config.getTimeoutAbandonoMinutos() : 20;
        int umbralSinSenalMin = config.getTimeoutSinSenalMinutos() != null
            ? config.getTimeoutSinSenalMinutos() : 15;
        int umbralCierreMin = Math.max(umbralAbandonoMin, umbralSinSenalMin) + 5;
        LocalDateTime limitesinSenal = LocalDateTime.now().minusMinutes(umbralCierreMin);

        List<SesionMonitoreo> activas = sesionDao.findByActivaTrue();
        for (SesionMonitoreo sesion : activas) {
            // Turno pausado: no se penaliza. gestionarSesiones cierra la sesión en su
            // próxima pasada; adelantarse acá la cerraría dejando una falta injusta.
            if (sesion.getJornada() != null && sesion.getJornada().isMonitoreoPausado()) continue;

            // Caso 1: nunca mandó un ping y pasó el umbral de falta → FALTA (no se presentó).
            // `ultimaActualizacion == null` asegura que NUNCA reportó (un fix "verificando"
            // zona ya tiene ultimaActualizacion y no cae aquí).
            if (sesion.getEstadoZona() == EstadoZona.DESCONOCIDO
                    && sesion.getUltimaActualizacion() == null
                    && sesion.getTimestampInicio().isBefore(limiteFalta)) {
                registrarFalta(sesion);
                cerrarSesion(sesion);
                continue;
            }

            // Caso 2: última señal hace +30 min (usuario cerró el browser sin logout) → cerrar.
            if (sesion.getUltimaActualizacion() != null
                    && sesion.getUltimaActualizacion().isBefore(limitesinSenal)) {
                log.info("Sesión sin señal reciente cerrada por inactividad: id={}, última={}",
                    sesion.getIdSesion(), sesion.getUltimaActualizacion());
                cerrarSesion(sesion);
            }
        }
    }

    /**
     * Registra una FALTA: la persona no reportó ubicación durante el inicio del turno.
     *
     * Una sola por persona, jornada y día. La falta es un hecho del TURNO, no de la
     * sesión: como la sesión se cierra acá y el scheduler la vuelve a abrir mientras
     * el turno siga corriendo, sin este corte cada ciclo de ~30 min dejaba otra falta
     * (en producción, 24 faltas de la misma persona en un solo turno).
     */
    private void registrarFalta(SesionMonitoreo sesion) {
        Long idJornada = sesion.getJornada() != null ? sesion.getJornada().getIdJornada() : null;
        Long idTitular = sesion.getTitular() != null ? sesion.getTitular().getIdTitular() : null;
        Long idInterno = sesion.getInterno() != null ? sesion.getInterno().getIdInterno() : null;

        // Sin jornada o sin dueño no hay con qué agrupar: se registra, como antes.
        if (idJornada != null && (idTitular != null || idInterno != null)) {
            LocalDate hoy = LocalDate.now();
            long yaHay = eventoDao.contarIncidenciasEnJornada(
                TipoIncidencia.FALTA, idJornada, idTitular, idInterno,
                hoy.atStartOfDay(), hoy.plusDays(1).atStartOfDay());
            if (yaHay > 0) {
                log.debug("Falta ya registrada hoy para la sesión id={}; solo se cierra",
                    sesion.getIdSesion());
                return;
            }
        }

        EventoAbandono falta = EventoAbandono.builder()
            .sesion(sesion)
            .timestampSalida(sesion.getTimestampInicio())
            .timestampAbandono(LocalDateTime.now())
            .estado(EstadoEvento.ABANDONO_CONFIRMADO)   // estado genérico "confirmado"
            .tipo(TipoIncidencia.FALTA)
            .estadoJustificacion(EstadoJustificacion.PENDIENTE)
            .notificadoAdmin(true)
            .resuelto(true)   // hecho puntual; la sesión se cierra a continuación
            .observaciones("No se presentó: sin reporte de ubicación durante el turno")
            .build();
        falta = eventoDao.save(falta);
        notificarAdmin(falta, "FALTA");
        notificacionService.notificarIncidenciaAlAdmin(falta);
        // Privacidad: si cerró sesión durante el turno, no le notificamos (ya se fue).
        if (!sesion.isCerradaPorLogout()) {
            notificacionService.notificarIncidenciaAlFuncionario(falta);
        }
        log.warn("Falta registrada (no se presentó): sesión id={}", sesion.getIdSesion());
    }

    /**
     * Cada 5 minutos: avisa a usuarios cuya sesión lleva 5–14 min en estado DESCONOCIDO
     * (GPS no habilitado) para que tengan oportunidad de activarlo antes de ser marcados.
     */
    @Scheduled(fixedDelay = 300_000, initialDelay = 120_000)
    @Transactional
    public void alertarGpsPendiente() {
        if (enCalentamiento()) return;   // tras reinicio, esperar a que re-reporten
        LocalDateTime ahora     = LocalDateTime.now();
        LocalDateTime limiteMin = ahora.minusMinutes(14);
        LocalDateTime limiteMax = ahora.minusMinutes(5);

        // Nadie recibe avisos fuera de SU horario. Este job miraba solo las sesiones
        // activas, y una sesión que sobrevive un minuto al fin del turno (o quedó
        // abierta) le pedía el GPS a alguien que ya no estaba obligado a darlo.
        EnTurno enTurno = calcularEnTurno(ahora);

        List<SesionMonitoreo> activas = sesionDao.findByActivaTrue();
        for (SesionMonitoreo sesion : activas) {
            if (!enTurno.contiene(sesion)) {
                gpsPendienteNotificados.remove(sesion.getIdSesion());
                continue;
            }
            // Solo a quienes NUNCA mandaron GPS. Un fix recibido pero aún "verificando"
            // zona (DESCONOCIDO transitorio) ya tiene ultimaActualizacion y NO debe
            // recibir "activá tu GPS".
            if (sesion.getEstadoZona() != EstadoZona.DESCONOCIDO
                    || sesion.getUltimaActualizacion() != null) continue;
            LocalDateTime inicio = sesion.getTimestampInicio();
            if (inicio.isAfter(limiteMax) || inicio.isBefore(limiteMin)) continue;

            // Una sola vez por sesión: el job corre cada 5 min y la ventana de aviso
            // dura 9 (5–14), así que la misma sesión caía dos veces y el funcionario
            // recibía el mismo "reactiva tu ubicación" con 5 minutos de diferencia.
            if (!gpsPendienteNotificados.add(sesion.getIdSesion())) continue;

            // Privacidad: no avisamos al funcionario que cerró sesión; sí al admin.
            if (!sesion.isCerradaPorLogout()) {
                notificacionService.notificarGpsDenegadoAlFuncionario(sesion);
            }
            notificarAdminGpsDenegado(sesion);
            notificacionService.notificarGpsNoActivadoAlAdmin(sesion);
            log.info("Alerta GPS pendiente enviada: sesión id={}", sesion.getIdSesion());
        }
        // Soltar las sesiones ya cerradas para que el set no crezca sin límite.
        gpsPendienteNotificados.removeIf(
            id -> activas.stream().noneMatch(s -> s.getIdSesion().equals(id)));
    }

    /**
     * Cada minuto: confirma abandonos cuando se supera el timeout (timeoutAbandonoMinutos,
     * configurable). Antes corría cada 5 min, lo que retrasaba la confirmación.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void detectarAbandonos() {
        if (enCalentamiento()) return;   // tras reinicio, no confirmar con datos viejos
        ConfiguracionMonitoreo config = getConfig();
        LocalDateTime limite = LocalDateTime.now()
            .minusMinutes(config.getTimeoutAbandonoMinutos());

        List<EventoAbandono> pendientes = eventoDao.findParaConfirmarAbandono(
            EstadoEvento.FUERA_ZONA, limite);

        for (EventoAbandono evt : pendientes) {
            evt.setTimestampAbandono(LocalDateTime.now());
            evt.setEstado(EstadoEvento.ABANDONO_CONFIRMADO);
            // Abandono confirmado → queda pendiente de justificación por el funcionario.
            evt.setEstadoJustificacion(EstadoJustificacion.PENDIENTE);
            eventoDao.save(evt);
            notificarAdmin(evt, "ABANDONO");
            notificacionService.notificarIncidenciaAlAdmin(evt);
            // Privacidad: si el funcionario cerró sesión, no le notificamos (ya se
            // fue). El abandono queda registrado y el admin sí se entera.
            if (!evt.getSesion().isCerradaPorLogout()) {
                notificacionService.notificarAbandonoAlFuncionario(evt);
            }
            log.warn("Abandono confirmado: sesión={}, tiempo={}min",
                evt.getSesion().getIdSesion(), evt.getTiempoFueraMinutos());
        }
    }

    /**
     * Cada 30 s: detecta sesiones que YA estaban reportando GPS y dejaron de
     * hacerlo (GPS apagado, sin internet, modo avión). Es el caso que faltaba:
     * los otros jobs solo cubrían "nunca envió GPS" (DESCONOCIDO).
     *
     * Dos etapas:
     *  1) AVISO (push "activá tu ubicación" + alerta admin) en cuanto la señal lleva
     *     más de `umbralSeg` sin actualizarse. Se manda UNA sola vez por sesión.
     *  2) ABANDONO: si la pérdida supera `umbralAbandonoSeg` (siempre posterior al
     *     aviso) y no hay ya un evento abierto, se crea un EventoAbandono CONFIRMADO
     *     y se notifica. Al recuperar el GPS dentro de zona, UbicacionServiceImpl lo
     *     resuelve como RETORNADO (cierre robusto independiente del caché).
     *
     * Umbral adaptativo: 2× el intervalo configurado, con piso de 90 s. Ese piso es clave:
     * la cadencia REAL de envío suele exceder el intervalo configurado (en navegador
     * el browser estrangula timers en background y getCurrentPosition por red es
     * lento → cadencia ~110 s aunque el intervalo sea 30 s). Con un piso muy bajo
     * saltaba un falso "sin señal" en el hueco entre pings. El push le llega al
     * funcionario apenas recupere internet; el aviso inmediato en su teléfono lo da
     * la propia app (composable useAvisoSenal), que no depende de la red.
     *
     * Con los valores por defecto (intervaloGpsActivoSeg=120, timeoutAbandonoMinutos=30)
     * el aviso sale a los 4 min de silencio y la ausencia se confirma a los 30 min.
     */
    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    @Transactional
    public void detectarSinSenal() {
        if (enCalentamiento()) return;   // tras reinicio, esperar a que re-reporten
        ConfiguracionMonitoreo config = getConfig();
        long umbralSeg = Math.max(90L, Math.round(config.getIntervaloGpsActivoSeg() * 2.0));
        // El abandono por pérdida de señal SIEMPRE va después del aviso (umbral+60s
        // como piso) y respeta su propio timeout, más corto que el de abandono de
        // zona: aquí no se sabe dónde está la persona, así que esperar lo mismo que
        // cuando sí se la ve fuera de la geocerca solo alarga la ceguera.
        int sinSenalMin = config.getTimeoutSinSenalMinutos() != null
            ? config.getTimeoutSinSenalMinutos() : 15;
        long umbralAbandonoSeg = Math.max(umbralSeg + 60L, sinSenalMin * 60L);
        LocalDateTime ahora          = LocalDateTime.now();
        LocalDateTime limiteAviso    = ahora.minusSeconds(umbralSeg);
        LocalDateTime limiteAbandono = ahora.minusSeconds(umbralAbandonoSeg);

        // Conjunto de personas que están EN TURNO ahora mismo. Solo a ellas les
        // corresponde el monitoreo: sin este filtro, una sesión vieja sin cerrar (o
        // creada indebidamente) disparaba "fuera de zona / abandono" a alguien que
        // hoy NO tiene turno. Mismo criterio que gestionarSesiones.
        EnTurno enTurno = calcularEnTurno(ahora);

        List<SesionMonitoreo> activas = sesionDao.findByActivaTrue();
        for (SesionMonitoreo sesion : activas) {
            // ¿El dueño de la sesión está en turno AHORA? Si no, no avisar: es una
            // sesión obsoleta que gestionarSesiones cerrará en su próxima pasada.
            if (!enTurno.contiene(sesion)) {
                sinSenalNotificados.remove(sesion.getIdSesion());
                continue;
            }

            // Solo sesiones que estaban reportando (DESCONOCIDO lo cubre otro job).
            boolean estabaReportando = sesion.getEstadoZona() != EstadoZona.DESCONOCIDO
                && sesion.getUltimaActualizacion() != null;
            if (!estabaReportando) {
                sinSenalNotificados.remove(sesion.getIdSesion());
                continue;
            }

            LocalDateTime ultima = sesion.getUltimaActualizacion();
            if (!ultima.isBefore(limiteAviso)) {
                // Reportó hace poco → señal OK → permitir re-notificar a futuro.
                // Si se había avisado el corte, se cierra con un "señal recuperada"
                // (el propio método comprueba que hubiera un aviso abierto).
                if (sinSenalNotificados.remove(sesion.getIdSesion())) {
                    notificacionService.notificarSenalRecuperadaAlAdmin(sesion);
                }
                continue;
            }

            boolean porLogout = sesion.isCerradaPorLogout();

            // 1) AVISO — una sola vez por sesión mientras dure la pérdida. Si el
            //    funcionario CERRÓ SESIÓN, no le avisamos (privacidad: ya se fue);
            //    de eso solo se entera el admin.
            if (sinSenalNotificados.add(sesion.getIdSesion())) {
                if (!porLogout) {
                    notificacionService.notificarGpsDenegadoAlFuncionario(sesion);
                }
                notificarAdminGpsDenegado(sesion);
                notificacionService.notificarSenalPerdidaAlAdmin(sesion,
                    java.time.Duration.between(ultima, ahora).toMinutes());
                log.info("Sin señal GPS detectada: sesión id={}, última={}, porLogout={}",
                    sesion.getIdSesion(), ultima, porLogout);
            }

            // 2) ABANDONO — solo si la pérdida supera el umbral y NO hay ya un
            //    evento abierto (p.ej. de salida de zona) para no duplicar.
            boolean superaAbandono = ultima.isBefore(limiteAbandono);
            boolean sinEventoAbierto = eventoDao
                .findFirstBySesion_IdSesionAndResueltoFalseOrderByIdEventoDesc(sesion.getIdSesion())
                .isEmpty();
            if (superaAbandono && sinEventoAbierto) {
                // Caso #6: si la señal se perdió con la batería casi agotada, la
                // ausencia es una INCIDENCIA NO PUNIBLE (no se sabe si fue voluntario):
                // se registra para el admin pero no queda pendiente de justificar.
                final int bateriaCriticaPct = 10;
                Integer bat = sesion.getUltimaBateria();
                boolean bateriaCasiAgotada = !porLogout && bat != null && bat <= bateriaCriticaPct;

                EventoAbandono evt = EventoAbandono.builder()
                    .sesion(sesion)
                    .timestampSalida(ultima)                 // la pérdida empezó en la última señal
                    .timestampAbandono(ahora)
                    .distanciaAlSalirMetros(sesion.getUltimaDistanciaMetros())
                    .estado(EstadoEvento.ABANDONO_CONFIRMADO)
                    .tipo(porLogout ? TipoIncidencia.LOGOUT_EN_TURNO : TipoIncidencia.AUSENCIA_SENAL)
                    .estadoJustificacion(bateriaCasiAgotada
                        ? EstadoJustificacion.NO_REQUERIDA
                        : EstadoJustificacion.PENDIENTE)
                    .notificadoAdmin(true)
                    .observaciones(porLogout
                        ? "Ausencia: cerró sesión durante el turno"
                        : (bateriaCasiAgotada
                            ? "Posible batería agotada (" + bat + "%) — sin penalización"
                            : "Abandono por pérdida de señal GPS"))
                    .build();
                evt = eventoDao.save(evt);
                notificarAdmin(evt, "ABANDONO");
                notificacionService.notificarIncidenciaAlAdmin(evt);
                // Pérdida de señal → mensaje correcto (NO afirma "fuera de zona").
                // Si cerró sesión, no se le notifica al funcionario (privacidad).
                if (!porLogout) {
                    notificacionService.notificarAusenciaPorSenalAlFuncionario(evt);
                }
                log.warn("Abandono por pérdida de señal: sesión={}, sin señal desde={}, porLogout={}",
                    sesion.getIdSesion(), ultima, porLogout);
            }
        }
        // Limpiar IDs de sesiones que ya no están activas.
        sinSenalNotificados.removeIf(
            id -> activas.stream().noneMatch(s -> s.getIdSesion().equals(id)));
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Quiénes están en turno AHORA, según el horario propio de cada asignación.
     *
     * Punto único para "¿a esta persona le corresponde estar reportando?". Los
     * jobs que avisan al funcionario deben preguntarlo antes de molestarlo: el
     * horario que manda es el de su asignación (que puede llevar override), no el
     * general de la jornada ni la mera existencia de una sesión abierta.
     */
    private EnTurno calcularEnTurno(LocalDateTime ahora) {
        LocalDate hoy   = ahora.toLocalDate();
        LocalTime horaA = ahora.toLocalTime();
        return new EnTurno(
            titularJornadaDao.findAllInActiveJornadas().stream()
                .filter(tj -> tj.estaEnTurnoAhora(hoy, horaA))
                .map(tj -> tj.getTitular().getIdTitular())
                .collect(java.util.stream.Collectors.toSet()),
            internoJornadaDao.findAllInActiveJornadas().stream()
                .filter(ij -> estaEnHorario(ij, hoy, horaA))
                .map(ij -> ij.getInterno().getIdInterno())
                .collect(java.util.stream.Collectors.toSet()));
    }

    private record EnTurno(java.util.Set<Long> titulares, java.util.Set<Long> internos) {
        boolean contiene(SesionMonitoreo s) {
            return (s.getTitular() != null && titulares.contains(s.getTitular().getIdTitular()))
                || (s.getInterno() != null && internos.contains(s.getInterno().getIdInterno()));
        }
    }

    private void cerrarSesion(SesionMonitoreo sesion) {
        sesion.setActiva(false);
        sesion.setTimestampFin(LocalDateTime.now());
        // Resolver TODOS los eventos abiertos (puede haber duplicados por concurrencia)
        eventoDao.findBySesion_IdSesionAndResueltoFalse(sesion.getIdSesion()).forEach(evt -> {
            evt.setResuelto(true);
            evt.setTimestampRetorno(LocalDateTime.now());
            evt.setEstado(EstadoEvento.RESUELTO);
            evt.setObservaciones("Sesión cerrada automáticamente al finalizar turno");
            eventoDao.save(evt);
        });
        sesionDao.save(sesion);
        log.info("Sesión cerrada: id={}", sesion.getIdSesion());
    }

    private boolean estaEnHorario(InternoJornada ij, LocalDate hoy, LocalTime ahora) {
        if (ij.getEstado() == EstadoAsignacion.CANCELADO
                || ij.getEstado() == EstadoAsignacion.COMPLETADO) return false;
        var jornada = ij.getJornada();
        if (jornada == null || !jornada.monitoreoHabilitado()) return false;
        if (hoy.isBefore(jornada.getFechaInicio()) || hoy.isAfter(jornada.getFechaFin())) return false;
        // Delega a Jornada: maneja CONTINUA, PARTIDA (mañana+tarde) y nocturno
        return jornada.estaEnHorarioAhora(ahora);
    }

    private void notificarAdminGpsDenegado(SesionMonitoreo sesion) {
        try {
            String nombre = sesion.getTitular() != null
                ? sesion.getTitular().getPersona().getNombreCompleto()
                : (sesion.getInterno() != null
                    ? sesion.getInterno().getPersona().getNombreCompleto()
                    : "Desconocido");
            AlertaMonitoreoDto alerta = AlertaMonitoreoDto.builder()
                .tipo("GPS_DENEGADO")
                .idEvento(0L)
                .idSesion(sesion.getIdSesion())
                .nombrePersonal(nombre)
                .nombreLugar(sesion.getLugar() != null ? sesion.getLugar().getNombre() : null)
                .estado(null)
                .tiempoFueraMinutos(0L)
                .timestamp(LocalDateTime.now().format(DT_FMT))
                .build();
            messaging.convertAndSend("/topic/admin/alertas-monitoreo", alerta);
        } catch (Exception e) {
            log.warn("Error al notificar GPS denegado al admin: {}", e.getMessage());
        }
    }

    private void notificarAdmin(EventoAbandono evt, String tipo) {
        try {
            AlertaMonitoreoDto alerta = AlertaMonitoreoDto.builder()
                .tipo(tipo)
                .idEvento(evt.getIdEvento())
                .idSesion(evt.getSesion() != null ? evt.getSesion().getIdSesion() : null)
                .nombrePersonal(evt.getNombrePersonal())
                .nombreLugar(evt.getSesion() != null && evt.getSesion().getLugar() != null
                    ? evt.getSesion().getLugar().getNombre() : null)
                .estado(evt.getEstado())
                .tiempoFueraMinutos(evt.getTiempoFueraMinutos())
                .timestamp(LocalDateTime.now().format(DT_FMT))
                .build();
            messaging.convertAndSend("/topic/admin/alertas-monitoreo", alerta);
        } catch (Exception e) {
            log.warn("Error al notificar alerta: {}", e.getMessage());
        }
    }

    private ConfiguracionMonitoreo getConfig() {
        return configDao.findById(ConfiguracionMonitoreo.ID)
            .orElseGet(() -> configDao.save(
                ConfiguracionMonitoreo.builder().id(ConfiguracionMonitoreo.ID).build()));
    }
}
