package com.uap.controlMedico.model.service.monitoreo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uap.controlMedico.model.dao.ConfiguracionMonitoreoDao;
import com.uap.controlMedico.model.dao.EventoAbandonoDao;
import com.uap.controlMedico.model.dao.InternoJornadaDao;
import com.uap.controlMedico.model.dao.RegistroUbicacionDao;
import com.uap.controlMedico.model.dao.SesionMonitoreoDao;
import com.uap.controlMedico.model.dao.TitularJornadaDao;
import com.uap.controlMedico.model.dao.UsuarioDao;
import com.uap.controlMedico.model.dto.monitoreo.AlertaMonitoreoDto;
import com.uap.controlMedico.model.dto.monitoreo.LoteUbicacionResponse;
import com.uap.controlMedico.model.dto.monitoreo.UbicacionRequest;
import com.uap.controlMedico.model.dto.monitoreo.UbicacionResponse;
import com.uap.controlMedico.model.entity.ConfiguracionMonitoreo;
import com.uap.controlMedico.model.entity.EventoAbandono;
import com.uap.controlMedico.model.entity.InternoJornada;
import com.uap.controlMedico.model.entity.Jornada;
import com.uap.controlMedico.model.entity.Lugar;
import com.uap.controlMedico.model.entity.RegistroUbicacion;
import com.uap.controlMedico.model.entity.SesionMonitoreo;
import com.uap.controlMedico.model.entity.TitularJornada;
import com.uap.controlMedico.model.entity.Usuario;
import com.uap.controlMedico.model.enums.EstadoEvento;
import com.uap.controlMedico.model.enums.EstadoJustificacion;
import com.uap.controlMedico.model.enums.EstadoZona;
import com.uap.controlMedico.model.enums.TipoIncidencia;
import com.uap.controlMedico.model.enums.TipoRegistroUbicacion;
import com.uap.controlMedico.model.service.notificacion.NotificacionService;
import com.uap.controlMedico.shared.util.HaversineUtil;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class UbicacionServiceImpl implements UbicacionService {

    private static final int SNAPSHOT_INTERVALO_MINUTOS = 5;
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final UsuarioDao               usuarioDao;
    private final TitularJornadaDao        titularJornadaDao;
    private final InternoJornadaDao        internoJornadaDao;
    private final RegistroUbicacionDao     registroDao;
    private final SesionMonitoreoDao       sesionDao;
    private final EventoAbandonoDao        eventoDao;
    private final ConfiguracionMonitoreoDao configDao;
    private final SimpMessagingTemplate    messaging;
    private final HaversineUtil            haversine;
    private final NotificacionService      notificacionService;

    /** Cache: estado actual de zona por idUsuario → evita query BD en cada GPS ping. */
    private final ConcurrentHashMap<Long, EstadoZonaCache> estadoCache = new ConcurrentHashMap<>();

    /** Registro en cache del estado de zona por usuario. */
    private record EstadoZonaCache(
        EstadoZona     zona,
        LocalDateTime  timestampUltimaTransicion,
        LocalDateTime  timestampUltimoSnapshot,
        Long           idEventoAbierto,
        // Cuándo se vio por primera vez un fix FUERA estando confirmado DENTRO.
        // Si null, no hay salida en verificación. Sostiene el debounce de salida.
        LocalDateTime  candidatoFueraDesde
    ) {}

    @Override
    @Transactional
    public UbicacionResponse registrarUbicacion(String username, UbicacionRequest request) {
        Usuario usuario = usuarioDao.findByUsername(username)
            .orElseThrow(() -> new EntityNotFoundException("Usuario: " + username));

        ConfiguracionMonitoreo config = getConfig();

        // ── Resolver jornada y lugar ───────────────────────────────────────
        Jornada jornada = null;
        Lugar   lugar   = null;
        SesionMonitoreo sesion = null;
        String  tipoPersonal = null;
        Long    idPersonal   = null;

        // Guardamos las entidades de asignación para poder crear sesión on-demand
        TitularJornada tjResolved = null;
        InternoJornada ijResolved = null;

        Long perId = usuario.getPerId();
        if (perId != null) {
            List<TitularJornada> tjs = titularJornadaDao.findActivasByPersona(perId);
            if (!tjs.isEmpty()) {
                TitularJornada tj = tjs.get(0);
                // Verificar que esté en turno ahora (considera ROL TURNO)
                if (tj.estaEnTurnoAhora(LocalDate.now(), LocalTime.now())) {
                    jornada      = tj.getJornada();
                    lugar        = tj.getLugar() != null ? tj.getLugar()
                                 : (jornada.getLugar() != null ? jornada.getLugar() : null);
                    tipoPersonal = "TITULAR";
                    idPersonal   = tj.getTitular().getIdTitular();
                    tjResolved   = tj;
                    sesion = sesionDao.findFirstByTitular_IdTitularAndActivaTrueOrderByTimestampInicioDesc(idPersonal).orElse(null);
                }
            }

            if (jornada == null) {
                List<InternoJornada> ijs = internoJornadaDao.findActivasByPersona(perId);
                if (!ijs.isEmpty()) {
                    InternoJornada ij = ijs.get(0);
                    Jornada jInterno  = ij.getJornada();
                    // Igual que el titular: solo si el interno está EN HORARIO ahora.
                    // Sin esta guarda, un interno fuera de su horario (día sin turno)
                    // creaba sesión y disparaba notificaciones de salida/abandono.
                    LocalDate hoy = LocalDate.now();
                    boolean enHorario = jInterno != null
                        && jInterno.monitoreoHabilitado()
                        && !hoy.isBefore(jInterno.getFechaInicio())
                        && !hoy.isAfter(jInterno.getFechaFin())
                        && jInterno.estaEnHorarioAhora(LocalTime.now());
                    if (enHorario) {
                        jornada      = jInterno;
                        lugar        = ij.getLugar() != null ? ij.getLugar()
                                     : (jornada.getLugar() != null ? jornada.getLugar() : null);
                        tipoPersonal = "INTERNO";
                        idPersonal   = ij.getInterno().getIdInterno();
                        ijResolved   = ij;
                        sesion = sesionDao.findFirstByInterno_IdInternoAndActivaTrueOrderByTimestampInicioDesc(idPersonal).orElse(null);
                    }
                }
            }
        }

        // Sin turno activo → solo responder sin guardar
        if (jornada == null) {
            return UbicacionResponse.builder()
                .dentroRadio(false)
                .distanciaMetros(0)
                .mensaje("Sin turno activo en este momento")
                .estadoZona(EstadoZona.DESCONOCIDO)
                .tiempoFueraMinutos(0L)
                .intervaloProximoEnvioSeg(config.getIntervaloGpsActivoSeg())
                .mensajePersonal("No tienes turno activo. El monitoreo está inactivo.")
                .enTurnoActivo(false)
                .timestamp(LocalDateTime.now().format(DT_FMT))
                .build();
        }

        // ── Calcular geofence con histéresis ───────────────────────────────
        // GPS jitter (~5-20m en móvil) causa falsas transiciones DENTRO↔FUERA
        // si se usa un solo umbral. La histéresis usa radioAlertaExtraMetros
        // como banda de guarda: una vez DENTRO, necesitas salir MÁS ALLÁ del
        // radio + margen para ser considerado FUERA. Una vez FUERA, necesitas
        // volver DENTRO del radio base para ser considerado DENTRO.
        double  distMetros    = 0.0;
        int     radioMetros   = 0;
        String  nombreLugar   = null;
        int     hysteresisMetros = config.getRadioAlertaExtraMetros() != null
            ? config.getRadioAlertaExtraMetros() : 0;

        // Precisión del fix (incertidumbre en metros). Sin dato (APK viejo) → 0,
        // es decir, se comporta como antes (margen 0).
        double precision = request.getPrecision() != null ? request.getPrecision() : 0.0;

        // Señales de contexto (Fase 2). Opcionales: null si el cliente no las envía.
        Integer bateria      = request.getBateria();
        String  conectividad = request.getConectividad();
        boolean esMock       = Boolean.TRUE.equals(request.getEsMock());
        if (esMock) {
            log.warn("GPS [{}]: fix con ubicación SIMULADA (mock) — posible fraude.", username);
        }

        // Estado CONFIRMADO anterior + marca de "candidato a salida" (debounce).
        EstadoZonaCache cachePre = estadoCache.get(usuario.getIdUsuario());
        EstadoZona      prev     = cachePre != null ? cachePre.zona() : EstadoZona.DESCONOCIDO;
        LocalDateTime   candidatoFuera = cachePre != null ? cachePre.candidatoFueraDesde() : null;

        // (1)+(2) Medición CRUDA de la zona en este fix (histéresis + precisión).
        // Geocerca CONSCIENTE de la precisión: se considera FUERA sólo si la posición
        // está más allá del radio INCLUSO sumando la incertidumbre del GPS. Así un fix
        // impreciso cuya nube de error toca la zona recibe el beneficio de la duda
        // (DENTRO), pero uno claramente lejos marca FUERA aunque sea impreciso. El margen
        // perdonado se acota a precisionMaxConfiableMetros (configurable) para que un fix
        // con precisión monstruosa no vuelva indetectable una salida real.
        //
        // ANTES un fix impreciso se asumía DENTRO sin más (`prev != FUERA`) → un usuario
        // LEJOS aparecía "en zona" verde al iniciar el turno: ese era el bug de producción
        // ("3253 m al límite pero en verde").
        boolean medicionDentro;
        if (lugar != null && lugar.tieneGeofencing()) {
            distMetros  = haversine.calcularDistanciaMetros(
                request.getLatitud(), request.getLongitud(),
                lugar.getLatitudCentro().doubleValue(),
                lugar.getLongitudCentro().doubleValue());
            radioMetros = lugar.getRadioMetros();
            nombreLugar = lugar.getNombre();

            double margenIncertidumbre = Math.min(precision, config.getPrecisionMaxConfiableMetros().doubleValue());
            int umbralBase = (prev == EstadoZona.DENTRO)
                ? radioMetros + hysteresisMetros   // ya dentro: salir requiere más
                : radioMetros;                      // para entrar basta el radio base
            medicionDentro = distMetros <= (umbralBase + margenIncertidumbre);
        } else {
            // Sin geofencing configurado → asumir dentro
            medicionDentro = true;
        }
        EstadoZona zonaMedida = medicionDentro ? EstadoZona.DENTRO : EstadoZona.FUERA;

        // Fix sin valor informativo: cuando el teléfono no engancha satélites y cae a
        // posicionamiento por antena, reporta cientos de metros de incertidumbre. Como
        // el margen perdonado está topado en precisionMaxConfiableMetros, esa lectura
        // podía marcar FUERA a alguien sentado en su oficina. Un fix así no confirma NI
        // descarta una salida: se conserva el estado anterior sin correr el debounce.
        // Solo aplica en el sentido "hacia afuera": un fix impreciso que igual cae
        // DENTRO no hace daño, y uno ya confirmado FUERA no necesita re-confirmarse.
        boolean fixSinValor = zonaMedida == EstadoZona.FUERA
            && prev != EstadoZona.FUERA
            && precision > config.getPrecisionMaxConfiableMetros().doubleValue();
        if (fixSinValor) {
            log.debug("GPS [{}]: fix ignorado para decidir zona (precisión {}m > {}m, dist={}m)",
                username, precision, config.getPrecisionMaxConfiableMetros(), distMetros);
        }

        // (3) DEBOUNCE temporal de la SALIDA: una lectura FUERA solo se CONFIRMA si
        // se sostiene `confirmacionFueraSeg`. Mientras tanto se reporta DENTRO (no se
        // crea evento, no se notifica, el UI no parpadea). Un salto de GPS aislado
        // (multipath en interiores, ~100m con buena accuracy) nunca confirma salida.
        // El RETORNO a zona es inmediato (no se penaliza volver). Durante la
        // verificación se acelera el intervalo para confirmar/descartar rápido.
        long pisoConfirmacion = config.getConfirmacionFueraSeg() != null ? config.getConfirmacionFueraSeg().longValue() : 60L;
        long confirmacionFueraSeg = Math.max(
            pisoConfirmacion, 2L * config.getIntervaloGpsFueraZonaSeg());
        LocalDateTime ahora = LocalDateTime.now();
        boolean verificandoSalida = false;
        EstadoZona zonaActual;
        if (fixSinValor) {
            // Se sostiene el estado anterior y NO se toca candidatoFuera: una racha de
            // fixes basura no debe acumular tiempo hacia la confirmación de salida.
            zonaActual = prev;
            verificandoSalida = prev == EstadoZona.DESCONOCIDO;
        } else if (zonaMedida == EstadoZona.DENTRO) {
            zonaActual = EstadoZona.DENTRO;
            candidatoFuera = null;
        } else if (prev == EstadoZona.FUERA) {
            zonaActual = EstadoZona.FUERA;        // ya estaba confirmado fuera
            candidatoFuera = null;
        } else {
            // Medición FUERA viniendo de DENTRO o de DESCONOCIDO (recién inicia) →
            // verificar antes de confirmar (debounce).
            if (candidatoFuera == null) candidatoFuera = ahora;
            long segFuera = ChronoUnit.SECONDS.between(candidatoFuera, ahora);
            if (segFuera >= confirmacionFueraSeg) {
                zonaActual = EstadoZona.FUERA;    // salida SOSTENIDA → confirmar
                candidatoFuera = null;
            } else if (prev == EstadoZona.DENTRO) {
                // Veníamos confirmados DENTRO: optimista para no parpadear ante un
                // salto aislado de GPS (multipath en interiores).
                zonaActual = EstadoZona.DENTRO;
                verificandoSalida = true;
                log.debug("GPS [{}]: posible salida (dist={}m, {}/{}s) — verificando, no se confirma",
                    username, distMetros, segFuera, confirmacionFueraSeg);
            } else {
                // Veníamos de DESCONOCIDO (primer fix del turno y la persona está
                // lejos): NEUTRAL mientras se confirma. NO afirmar "en zona" verde sin
                // haber estado nunca dentro — ese era el "3253 m al límite pero en
                // verde". La UI lo muestra como "verificando ubicación".
                zonaActual = EstadoZona.DESCONOCIDO;
                verificandoSalida = true;
                log.debug("GPS [{}]: inicio lejos (dist={}m, {}/{}s) — verificando antes de confirmar FUERA",
                    username, distMetros, segFuera, confirmacionFueraSeg);
            }
        }
        boolean dentroRadio = (zonaActual == EstadoZona.DENTRO);

        // ── Crear sesión on-demand si no existe ───────────────────────────
        // Esto cubre dos casos: (a) el scheduler aún no corrió, (b) limpiarSesionesObsoletas
        // cerró la sesión DESCONOCIDO antes de que el usuario activara el GPS.
        if (sesion == null) {
            SesionMonitoreo.SesionMonitoreoBuilder builder = SesionMonitoreo.builder()
                .jornada(jornada)
                .lugar(lugar)
                .estadoZona(EstadoZona.DESCONOCIDO)
                .activa(true);
            if ("TITULAR".equals(tipoPersonal) && tjResolved != null) {
                builder.titular(tjResolved.getTitular());
            } else if ("INTERNO".equals(tipoPersonal) && ijResolved != null) {
                builder.interno(ijResolved.getInterno());
            }
            sesion = sesionDao.save(builder.build());
            log.info("Sesión GPS creada on-demand por ping: tipo={}, id={}", tipoPersonal, idPersonal);
        }

        // ── Lógica de cache y transiciones ────────────────────────────────
        EstadoZonaCache cache = estadoCache.get(usuario.getIdUsuario());
        EstadoZona     zonaAnterior  = cache != null ? cache.zona() : EstadoZona.DESCONOCIDO;
        Long           idEvtAbierto  = cache != null ? cache.idEventoAbierto() : null;
        LocalDateTime  tsUltimoSnap  = cache != null ? cache.timestampUltimoSnapshot() : null;

        boolean esTransicion = (zonaAnterior != zonaActual) && (zonaAnterior != EstadoZona.DESCONOCIDO);
        boolean guardarRegistro = esTransicion
            || tsUltimoSnap == null
            || ChronoUnit.MINUTES.between(tsUltimoSnap, LocalDateTime.now()) >= SNAPSHOT_INTERVALO_MINUTOS;

        TipoRegistroUbicacion tipoReg = esTransicion
            ? TipoRegistroUbicacion.TRANSICION_ZONA
            : TipoRegistroUbicacion.SNAPSHOT_PERIODICO;

        if (guardarRegistro && lugar != null) {
            registroDao.save(RegistroUbicacion.builder()
                .usuario(usuario)
                .jornada(jornada)
                .lugar(lugar)
                .latitud(BigDecimal.valueOf(request.getLatitud()))
                .longitud(BigDecimal.valueOf(request.getLongitud()))
                .dentroRadio(dentroRadio)
                .distanciaMetros(BigDecimal.valueOf(distMetros))
                .tipoRegistro(tipoReg)
                .bateria(bateria)
                .conectividad(conectividad)
                .esMock(request.getEsMock())
                .clientEventId(request.getClientEventId())
                .build());
        }

        // Actualizar posición en SesionMonitoreo
        if (sesion != null) {
            // El lugar se fijaba al abrir la sesión y no se volvía a mirar: si al
            // funcionario le cambiaban de sede a mitad de turno, el geofence ya
            // evaluaba contra la nueva pero la sesión seguía nombrando la vieja, y
            // las alertas del admin la citaban ("fuera de zona en SISU" para alguien
            // reasignado a RECTORADO). Se re-apunta en cuanto llega un fix.
            Long idLugarSesion = sesion.getLugar() != null ? sesion.getLugar().getIdLugar() : null;
            Long idLugarActual = lugar != null ? lugar.getIdLugar() : null;
            if (!java.util.Objects.equals(idLugarSesion, idLugarActual)) {
                log.info("Sesión {}: lugar re-apuntado {} → {}",
                    sesion.getIdSesion(),
                    sesion.getLugar() != null ? sesion.getLugar().getNombre() : "(sin lugar)",
                    lugar != null ? lugar.getNombre() : "(sin lugar)");
                sesion.setLugar(lugar);
            }

            sesion.setEstadoZona(zonaActual);
            sesion.setUltimaActualizacion(LocalDateTime.now());
            sesion.setUltimaLatitud(BigDecimal.valueOf(request.getLatitud()));
            sesion.setUltimaLongitud(BigDecimal.valueOf(request.getLongitud()));
            sesion.setUltimaDistanciaMetros(BigDecimal.valueOf(distMetros));
            if (bateria != null)      sesion.setUltimaBateria(bateria);
            if (conectividad != null) sesion.setUltimaConectividad(conectividad);
            // Llegó un ping → el usuario reanudó: ya no está "cerrada por logout".
            if (sesion.isCerradaPorLogout()) sesion.setCerradaPorLogout(false);

            // ── ATRASO: primera presencia (primer fix DENTRO) tarde vs inicio de turno ──
            // Solo con geocerca real: sin geofencing "DENTRO" es trivial y no representa
            // haber llegado al puesto.
            if (zonaActual == EstadoZona.DENTRO
                    && sesion.getTimestampPrimeraPresencia() == null
                    && lugar != null && lugar.tieneGeofencing()) {
                // Se consulta ANTES de marcar la presencia de ESTA sesión: si se
                // marcara primero, un auto-flush de Hibernate la incluiría en la
                // consulta y ningún atraso volvería a registrarse jamás.
                boolean yaLlegoHoy = llegadaYaRegistradaHoy(sesion, ahora);
                sesion.setTimestampPrimeraPresencia(ahora);
                LocalTime inicioEsperado = inicioEsperadoDe(tjResolved, jornada, ahora.toLocalTime());
                if (yaLlegoHoy) {
                    // Reconexión, no llegada: ya marcó presencia hoy en este turno.
                    // Medir el "atraso" de nuevo contra la hora de inicio daría un
                    // número que crece en cada reconexión (el bug de los 208 min).
                    log.debug("Reanudación tras corte: no se recalcula atraso (usuario={})", username);
                } else if (inicioEsperado != null) {
                    LocalDateTime inicioTurno = LocalDateTime.of(
                        sesion.getTimestampInicio().toLocalDate(), inicioEsperado);
                    long atrasoMin = ChronoUnit.MINUTES.between(inicioTurno, ahora);
                    int tolerancia = config.getToleranciaAtrasoMin() != null
                        ? config.getToleranciaAtrasoMin() : 10;
                    if (atrasoMin > tolerancia && !atrasoYaRegistradoHoy(sesion, ahora)) {
                        EventoAbandono atraso = EventoAbandono.builder()
                            .sesion(sesion)
                            .timestampSalida(inicioTurno)
                            .timestampAbandono(ahora)
                            .estado(EstadoEvento.ABANDONO_CONFIRMADO)  // estado genérico "confirmado"
                            .tipo(TipoIncidencia.ATRASO)
                            .estadoJustificacion(EstadoJustificacion.PENDIENTE)
                            .minutosIncidencia((int) atrasoMin)
                            .notificadoAdmin(true)
                            .resuelto(true)   // hecho puntual, no es un evento "abierto"
                            .observaciones("Atraso de " + atrasoMin + " min al iniciar el turno")
                            .build();
                        eventoDao.save(atraso);
                        notificarAdmin(atraso, "ATRASO");
                        notificacionService.notificarIncidenciaAlAdmin(atraso);
                        notificacionService.notificarIncidenciaAlFuncionario(atraso);
                        log.info("Atraso registrado: usuario={}, {} min", username, atrasoMin);
                    }
                }
            }

            sesionDao.save(sesion);
        }

        // ── Gestión de EventoAbandono por transición de zona ───────────────
        long tiempoFueraMin = 0L;
        boolean abandonoConfirmado = false;

        // Entrar a FUERA desde DENTRO *o desde DESCONOCIDO* (el usuario inicia su
        // turno ya fuera del área asignada): ambos casos generan el evento de salida.
        boolean entraEnFuera   = zonaActual == EstadoZona.FUERA
                              && zonaAnterior != EstadoZona.FUERA;
        boolean retornaADentro = zonaActual == EstadoZona.DENTRO
                              && zonaAnterior == EstadoZona.FUERA;

        if (entraEnFuera && idEvtAbierto == null && sesion != null) {
            // Defensa contra pings concurrentes: si el caché dice null pero ya
            // existe un evento abierto en BD para esta sesión, reutilizarlo en
            // lugar de crear un duplicado (evita IncorrectResultSize aguas abajo).
            Optional<EventoAbandono> yaAbierto =
                eventoDao.findFirstBySesion_IdSesionAndResueltoFalseOrderByIdEventoDesc(sesion.getIdSesion());
            if (yaAbierto.isPresent()) {
                idEvtAbierto = yaAbierto.get().getIdEvento();
            } else {
                EventoAbandono evt = EventoAbandono.builder()
                    .sesion(sesion)
                    .distanciaAlSalirMetros(BigDecimal.valueOf(distMetros))
                    .estado(EstadoEvento.FUERA_ZONA)
                    .build();
                evt = eventoDao.save(evt);
                idEvtAbierto = evt.getIdEvento();
                notificarAdmin(evt, "SALIDA_ZONA");
                // Al FUNCIONARIO no se le avisa esta salida: es PROVISIONAL. Recién
                // pasa a ABANDONO_CONFIRMADO si se sostiene timeoutAbandonoMinutos, y
                // ese camino (detectarAbandonos) sí le notifica. Avisar acá le mandaba
                // "saliste de tu zona" y, un minuto después, "volviste" a alguien
                // sentado en su oficina: deriva de GPS, no una salida real. El admin
                // sí lo ve en vivo, que es su trabajo.
                log.info("Salida de zona detectada: usuario={}, distancia={}m", username, distMetros);
            }
        } else if (retornaADentro && idEvtAbierto != null) {
            final Long evtIdCerrar = idEvtAbierto;
            eventoDao.findById(evtIdCerrar).ifPresent(evt -> {
                // ¿Se le llegó a avisar de esta ausencia? Solo si el evento alcanzó
                // ABANDONO_CONFIRMADO, que es el único estado que le manda push. Si
                // seguía provisional (FUERA_ZONA), nunca supo que "salió" y avisarle
                // el retorno sería contarle un problema que no existió.
                boolean leAvisamos = evt.getEstado() == EstadoEvento.ABANDONO_CONFIRMADO;
                evt.setTimestampRetorno(LocalDateTime.now());
                evt.setEstado(EstadoEvento.RETORNADO);
                evt.setResuelto(true);
                eventoDao.save(evt);
                notificarAdmin(evt, "RETORNO");
                // Mismo criterio para el grupo de supervisión: solo se cierra lo que
                // se avisó. Un retorno sin abandono confirmado sería ruido.
                if (leAvisamos) {
                    notificacionService.notificarRetornoZonaAlFuncionario(evt);
                    notificacionService.notificarRetornoZonaAlAdmin(evt);
                }
            });
            idEvtAbierto = null;
            log.info("Retorno a zona detectado: usuario={}", username);
        }

        // Robustez: si el usuario está DENTRO, resolver CUALQUIER evento que siga
        // abierto aunque el caché en memoria no lo conociera. Cubre el abandono que
        // el scheduler crea por pérdida de señal: al recuperar el GPS dentro de zona,
        // el evento se cierra como RETORNADO. El bloque retornaADentro de arriba ya
        // resolvió el evento del caché (queda resuelto=true), así que aquí solo caen
        // los que ese camino no conocía → sin doble notificación.
        if (zonaActual == EstadoZona.DENTRO && sesion != null) {
            eventoDao.findBySesion_IdSesionAndResueltoFalse(sesion.getIdSesion()).forEach(evt -> {
                // Mismo criterio que arriba: solo se avisa el retorno de lo que se le
                // avisó. Los eventos del scheduler (pérdida de señal) llegan ya como
                // ABANDONO_CONFIRMADO, así que esos sí cierran con push.
                boolean leAvisamos = evt.getEstado() == EstadoEvento.ABANDONO_CONFIRMADO;
                evt.setTimestampRetorno(LocalDateTime.now());
                evt.setEstado(EstadoEvento.RETORNADO);
                evt.setResuelto(true);
                eventoDao.save(evt);
                notificarAdmin(evt, "RETORNO");
                // Mismo criterio para el grupo de supervisión: solo se cierra lo que
                // se avisó. Un retorno sin abandono confirmado sería ruido.
                if (leAvisamos) {
                    notificacionService.notificarRetornoZonaAlFuncionario(evt);
                    notificacionService.notificarRetornoZonaAlAdmin(evt);
                }
                log.info("Evento abierto resuelto al retornar señal/zona: id={}", evt.getIdEvento());
            });
            idEvtAbierto = null;
        }

        // Calcular tiempo fuera y estado abandono
        if (idEvtAbierto != null) {
            Optional<EventoAbandono> evtOpt = eventoDao.findById(idEvtAbierto);
            if (evtOpt.isPresent()) {
                tiempoFueraMin = evtOpt.get().getTiempoFueraMinutos();
                abandonoConfirmado = evtOpt.get().getEstado() == EstadoEvento.ABANDONO_CONFIRMADO;
            }
        }

        // Actualizar cache
        LocalDateTime nuevoSnap = guardarRegistro ? LocalDateTime.now()
            : (tsUltimoSnap != null ? tsUltimoSnap : LocalDateTime.now());
        estadoCache.put(usuario.getIdUsuario(), new EstadoZonaCache(
            zonaActual, LocalDateTime.now(), nuevoSnap, idEvtAbierto, candidatoFuera));

        // Primera señal GPS de la sesión (DESCONOCIDO → conocido): avisar al admin
        // para que el panel de vigilancia muestre al usuario como "con GPS" al instante.
        // Las transiciones DENTRO↔FUERA ya notifican vía /topic/admin/alertas-monitoreo.
        if (zonaAnterior == EstadoZona.DESCONOCIDO) {
            notificarRefrescoPresencia();
        }

        // ── Calcular intervalo adaptativo ─────────────────────────────────
        // Durante la verificación de salida (debounce) usamos el intervalo corto
        // (fuera de zona) para confirmar o descartar la salida cuanto antes.
        int intervalo = (dentroRadio && !verificandoSalida)
            ? config.getIntervaloGpsActivoSeg()
            : config.getIntervaloGpsFueraZonaSeg();

        String mensajePersonal = generarMensajePersonal(
            zonaActual, tiempoFueraMin, abandonoConfirmado, nombreLugar);

        log.debug("GPS [{}]: {:.1f}m de {} — {} (intervalo={}s)",
            username, distMetros,
            nombreLugar != null ? nombreLugar : "sin lugar",
            zonaActual, intervalo);

        return UbicacionResponse.builder()
            .dentroRadio(dentroRadio)
            .distanciaMetros(distMetros)
            .nombreLugar(nombreLugar)
            .radioMetros(radioMetros)
            .estadoZona(zonaActual)
            .tiempoFueraMinutos(tiempoFueraMin)
            .intervaloProximoEnvioSeg(intervalo)
            .mensajePersonal(mensajePersonal)
            .abandonoConfirmado(abandonoConfirmado)
            .enTurnoActivo(true)
            .mensaje(lugar == null ? "Sin lugar asignado en la jornada"
                    : zonaActual == EstadoZona.DENTRO ? "Dentro del área asignada"
                    : zonaActual == EstadoZona.FUERA  ? "Fuera del área asignada ⚠️"
                                                      : "Verificando ubicación…")
            .timestamp(LocalDateTime.now().format(DT_FMT))
            .build();
    }

    // ── Store-and-forward: lote de ubicaciones bufferizadas ──────────────

    @Override
    @Transactional
    public LoteUbicacionResponse procesarLote(String username, List<UbicacionRequest> fixes) {
        int recibidos = fixes != null ? fixes.size() : 0;
        if (recibidos == 0) {
            return LoteUbicacionResponse.builder()
                .recibidos(0).procesados(0).omitidos(0).ultimoEstado(null).build();
        }

        // Reproducir en orden cronológico del dispositivo (los sin timestamp, al final).
        // Así un fix DENTRO posterior resuelve cualquier abandono provisional abierto
        // (reconciliación) mediante la lógica que ya existe en registrarUbicacion.
        List<UbicacionRequest> ordenados = new ArrayList<>(fixes);
        ordenados.sort(Comparator.comparing(f ->
            f.getTimestampDispositivoMs() != null ? f.getTimestampDispositivoMs() : Long.MAX_VALUE));

        Set<String> vistosEnLote = new HashSet<>();
        int procesados = 0;
        int omitidos   = 0;
        UbicacionResponse ultimo = null;

        for (UbicacionRequest fix : ordenados) {
            String id = fix.getClientEventId();
            // Idempotencia: saltar duplicados dentro del lote y los ya guardados antes.
            if (id != null && !id.isBlank()
                    && (!vistosEnLote.add(id) || registroDao.existsByClientEventId(id))) {
                omitidos++;
                continue;
            }
            ultimo = registrarUbicacion(username, fix);
            procesados++;
        }

        log.info("Lote de ubicaciones procesado: usuario={}, recibidos={}, procesados={}, omitidos={}",
            username, recibidos, procesados, omitidos);

        return LoteUbicacionResponse.builder()
            .recibidos(recibidos).procesados(procesados).omitidos(omitidos)
            .ultimoEstado(ultimo).build();
    }

    // ── Cierre de sesión al hacer logout ─────────────────────────────────

    @Override
    @Transactional
    public void cerrarSesionActiva(String username) {
        usuarioDao.findByUsername(username).ifPresent(usuario -> {
            Long perId = usuario.getPerId();
            if (perId == null) return;

            // Logout EN TURNO → NO cerrar la sesión GPS. Se deja activa para que el
            // scheduler de pérdida de señal (detectarSinSenal) registre el abandono
            // tras el timeout de gracia (config timeoutAbandonoMinutos, ~5 min). Así
            // cerrar sesión en pleno turno cuenta como falta si no vuelve a tiempo;
            // si re-inicia sesión y reanuda el GPS antes del umbral, la sesión
            // continúa y no se genera abandono. Fuera de turno: cierre limpio normal.
            if (estaEnTurnoActivoAhora(perId)) {
                // Marcar las sesiones como "cerradas por logout" (siguen activas en
                // gracia) y avisar a los admins. El scheduler NO le notificará al
                // funcionario (ya se fue: privacidad), pero registrará la ausencia
                // si no vuelve antes del timeout.
                marcarSesionesPorLogout(perId);
                log.info("Logout EN TURNO de {} → sesión GPS en gracia (cerrada_por_logout); "
                    + "admins notificados; el scheduler evaluará la ausencia.", username);
                return;
            }

            // Intentar por titular — cerrar TODAS las sesiones activas (limpia duplicados)
            titularJornadaDao.findActivasByPersona(perId).stream()
                .map(tj -> tj.getTitular().getIdTitular())
                .findFirst()
                .ifPresent(idTitular ->
                    sesionDao.findByTitular_IdTitularAndActivaTrue(idTitular).forEach(sesion -> {
                        cerrarSesionConObservacion(sesion, "Sesión cerrada por logout del usuario");
                        estadoCache.remove(usuario.getIdUsuario());
                        log.info("Sesión GPS cerrada por logout: titular={}, usuario={}", sesion.getIdSesion(), username);
                    }));

            // Intentar por interno — cerrar TODAS las sesiones activas
            internoJornadaDao.findActivasByPersona(perId).stream()
                .map(ij -> ij.getInterno().getIdInterno())
                .findFirst()
                .ifPresent(idInterno ->
                    sesionDao.findByInterno_IdInternoAndActivaTrue(idInterno).forEach(sesion -> {
                        cerrarSesionConObservacion(sesion, "Sesión cerrada por logout del usuario");
                        estadoCache.remove(usuario.getIdUsuario());
                        log.info("Sesión GPS cerrada por logout: interno={}, usuario={}", sesion.getIdSesion(), username);
                    }));
        });
    }

    /**
     * Hora a la que ESTA persona debía estar en su puesto.
     *
     * Un titular puede tener horario propio dentro de la jornada, y la jornada es
     * solo el valor por defecto. Medir el atraso contra el horario general le
     * imputaba 6 horas de retraso a quien entraba puntual a las 14:00 en un turno
     * cuyo horario general empieza a las 08:00. En jornada partida cuenta la
     * franja que le aplica en ese momento; los internos y las asignaciones sin
     * override caen al horario del turno.
     */
    private LocalTime inicioEsperadoDe(TitularJornada tj, Jornada jornada, LocalTime ahora) {
        if (tj != null) {
            LocalTime[] franja = tj.franjaVigente(ahora);
            if (franja != null && franja[0] != null) return franja[0];
        }
        return jornada != null ? jornada.getHoraInicio() : null;
    }

    /**
     * ¿La persona está en turno activo AHORA? Misma regla que registrarUbicacion:
     * titular por estaEnTurnoAhora (considera ROL TURNO/nocturno) e interno por
     * rango de fechas + estaEnHorarioAhora. Se usa en el logout para decidir si la
     * sesión GPS se mantiene (y el scheduler evalúe abandono) o se cierra limpio.
     */
    private boolean estaEnTurnoActivoAhora(Long perId) {
        if (perId == null) return false;
        LocalDate hoy   = LocalDate.now();
        LocalTime ahora = LocalTime.now();

        boolean titularEnTurno = titularJornadaDao.findActivasByPersona(perId).stream()
            .anyMatch(tj -> tj.estaEnTurnoAhora(hoy, ahora));
        if (titularEnTurno) return true;

        return internoJornadaDao.findActivasByPersona(perId).stream().anyMatch(ij -> {
            Jornada j = ij.getJornada();
            return j != null
                && j.monitoreoHabilitado()
                && !hoy.isBefore(j.getFechaInicio())
                && !hoy.isAfter(j.getFechaFin())
                && j.estaEnHorarioAhora(ahora);
        });
    }

    /**
     * Logout en turno: marca las sesiones activas del usuario como cerradas-por-logout
     * (siguen activas, en periodo de gracia) y avisa a los admins. No las cierra: el
     * scheduler decidirá la ausencia si no vuelve, sin molestar al funcionario.
     */
    private void marcarSesionesPorLogout(Long perId) {
        java.util.function.Consumer<SesionMonitoreo> marcar = sesion -> {
            sesion.setCerradaPorLogout(true);
            sesionDao.save(sesion);
            notificacionService.notificarLogoutEnTurnoAlAdmin(sesion);
        };
        titularJornadaDao.findActivasByPersona(perId).stream()
            .map(tj -> tj.getTitular().getIdTitular()).findFirst()
            .ifPresent(idT -> sesionDao.findByTitular_IdTitularAndActivaTrue(idT).forEach(marcar));
        internoJornadaDao.findActivasByPersona(perId).stream()
            .map(ij -> ij.getInterno().getIdInterno()).findFirst()
            .ifPresent(idI -> sesionDao.findByInterno_IdInternoAndActivaTrue(idI).forEach(marcar));
    }

    private void cerrarSesionConObservacion(SesionMonitoreo sesion, String obs) {
        sesion.setActiva(false);
        sesion.setTimestampFin(LocalDateTime.now());
        // Resolver TODOS los eventos abiertos (puede haber duplicados por concurrencia)
        eventoDao.findBySesion_IdSesionAndResueltoFalse(sesion.getIdSesion()).forEach(evt -> {
            evt.setResuelto(true);
            evt.setTimestampRetorno(LocalDateTime.now());
            evt.setEstado(EstadoEvento.RESUELTO);
            evt.setObservaciones(obs);
            eventoDao.save(evt);
        });
        sesionDao.save(sesion);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private ConfiguracionMonitoreo getConfig() {
        return configDao.findById(ConfiguracionMonitoreo.ID)
            .orElseGet(() -> {
                ConfiguracionMonitoreo c = ConfiguracionMonitoreo.builder().id(ConfiguracionMonitoreo.ID).build();
                return configDao.save(c);
            });
    }

    /**
     * ¿Esta persona ya marcó su llegada hoy en este turno (en cualquier sesión)?
     *
     * Distingue "llegó" de "se reconectó". Sin esta distinción, cada sesión nueva
     * —y el scheduler crea una cada vez que la anterior se cierra por inactividad—
     * volvía a medir el atraso contra la hora de inicio del turno, produciendo
     * cifras crecientes (208 min y subiendo) para alguien que había llegado a hora.
     */
    private boolean llegadaYaRegistradaHoy(SesionMonitoreo sesion, LocalDateTime ahora) {
        Long idJornada = sesion.getJornada() != null ? sesion.getJornada().getIdJornada() : null;
        if (idJornada == null) return false;
        Long idTitular = sesion.getTitular() != null ? sesion.getTitular().getIdTitular() : null;
        Long idInterno = sesion.getInterno() != null ? sesion.getInterno().getIdInterno() : null;
        if (idTitular == null && idInterno == null) return false;
        LocalDate hoy = ahora.toLocalDate();
        return sesionDao.yaRegistroLlegadaHoy(idJornada, idTitular, idInterno,
            hoy.atStartOfDay(), hoy.plusDays(1).atStartOfDay());
    }

    /** Un ATRASO por persona, turno y día — mismo criterio que ya usa la FALTA. */
    private boolean atrasoYaRegistradoHoy(SesionMonitoreo sesion, LocalDateTime ahora) {
        Long idJornada = sesion.getJornada() != null ? sesion.getJornada().getIdJornada() : null;
        if (idJornada == null) return false;
        Long idTitular = sesion.getTitular() != null ? sesion.getTitular().getIdTitular() : null;
        Long idInterno = sesion.getInterno() != null ? sesion.getInterno().getIdInterno() : null;
        if (idTitular == null && idInterno == null) return false;
        LocalDate hoy = ahora.toLocalDate();
        return eventoDao.contarIncidenciasEnJornada(TipoIncidencia.ATRASO, idJornada,
            idTitular, idInterno, hoy.atStartOfDay(), hoy.plusDays(1).atStartOfDay()) > 0;
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
            log.warn("No se pudo notificar alerta al admin: {}", e.getMessage());
        }
    }

    /**
     * Avisa a los admins (canal /topic/admin/presencia) que deben refrescar el
     * panel de vigilancia. El front incrementa presenciaSignal y recarga datos;
     * el payload es irrelevante. Se usa en la primera señal GPS de una sesión.
     */
    private void notificarRefrescoPresencia() {
        try {
            java.util.Map<String, String> payload = new java.util.HashMap<>();
            payload.put("tipo", "GPS_ACTIVO");
            payload.put("timestamp", LocalDateTime.now().format(DT_FMT));
            messaging.convertAndSend("/topic/admin/presencia", payload);
        } catch (Exception e) {
            log.warn("No se pudo notificar refresco de presencia: {}", e.getMessage());
        }
    }

    private String generarMensajePersonal(EstadoZona zona, long minutosFuera,
                                          boolean abandonoConfirmado, String lugar) {
        if (zona == EstadoZona.DESCONOCIDO) {
            return "Obteniendo tu ubicación…";
        }
        if (zona == EstadoZona.DENTRO) {
            return lugar != null ? "Estás en " + lugar : "Dentro del área de trabajo";
        }
        if (abandonoConfirmado) {
            return "Abandono registrado. Contacta a tu supervisor inmediatamente.";
        }
        if (minutosFuera > 0) {
            return "Llevas " + minutosFuera + " min fuera de " +
                (lugar != null ? lugar : "tu área de trabajo") + ". Por favor regresa.";
        }
        return "Has salido del área de trabajo asignada. Regresa a " +
            (lugar != null ? lugar : "tu puesto");
    }
}
