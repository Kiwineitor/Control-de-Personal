<script setup lang="ts">
import { computed, h, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import { useRouter } from 'vue-router'

import { loadLocaleMessages } from '@vben/locales'
import { preferences, updatePreferences } from '@vben/preferences'
import { useUserStore } from '@vben/stores'

import { App as CapacitorApp } from '@capacitor/app'
import { Capacitor } from '@capacitor/core'
import { NDropdown, useDialog, useNotification } from 'naive-ui'

import { message } from '#/adapter/naive';
import { crearBitacora } from '#/api/sisu/bitacora';
import { culminarActividadApp, cancelarActividadApp, subirEvidenciaApp } from '#/api/sisu/app';
import { vaciarColaOffline, contarPendientes, type OperacionPendiente } from '#/composables/offlineQueue';
import { registrarTokenFcm } from '#/composables/usePushNotifications';
import {
  asegurarConsentimiento,
  mostrarAvisoPrivacidad,
  useAvisoPrivacidad,
} from '#/composables/useAvisoPrivacidad';
import { detenerAvisoSenal, iniciarAvisoSenal } from '#/composables/useAvisoSenal';
import {
  detenerMonitoreoRed,
  iniciarMonitoreoRed,
  useEstadoRed,
} from '#/composables/useEstadoRed';
import { iniciarLogShipper } from '#/composables/useLogShipper';
import { useGeolocalizacion } from '#/composables/useGeolocalizacion';
import { usePlatformPermisos } from '#/composables/usePlatformPermisos';
import { useTurnoHoy } from '#/composables/useTurnoHoy';
import { useWebSocket } from '#/composables/useWebSocket';
import { useAuthStore } from '#/store';

const router    = useRouter()
const userStore = useUserStore()
const authStore = useAuthStore()
const route     = useRoute()

const { noLeidas, connect, disconnect, notifRtSignal, gpsStatus, conectado } = useWebSocket();
const { iniciarWatch, detenerWatch, actualizarIntervalo, watchActivo, errorGps, gpsActivo, abrirConfiguracionApp, flushNow,
        servicioUbicacionActivo, abrirAjustesUbicacion, vigilarServicioUbicacion, detenerVigilanciaUbicacion } = useGeolocalizacion();
const {
  estadoUbicacion, estadoNotificacion,
  verificarEstado, pedirUbicacion, pedirNotificaciones,
} = usePlatformPermisos();
const { tieneTurnoHoy, enHorarioTurno, faseTurnoHoy, cargado: turnoCargado, cargarTurnoHoy } = useTurnoHoy();

// Alias para el template (usado en el banner de push)
const permisoNotif = estadoNotificacion;

// ── GPS: adaptar intervalo según respuesta del servidor ────────────────────
watch(
  () => gpsStatus.value?.intervaloProximoEnvioSeg,
  (seg) => { if (seg) actualizarIntervalo(seg); },
);

// ── GPS denegado durante turno activo ─────────────────────────────────────
const enTurnoActivo   = ref(false);
const mostrarModalGps = ref(false);

/**
 * ¿La app está visible? Lo mantiene el listener de appStateChange.
 *
 * Necesario porque arrancar el servicio de ubicación desde segundo plano es un
 * crash duro en Android 12+. Se asume `true` al montar: si el layout se está
 * montando, la app está a la vista.
 */
const appEnPrimerPlano = ref(true);

// ── Estado de conectividad ─────────────────────────────────────────────────
// Lo aporta useEstadoRed: en nativo escucha el ConnectivityManager de Android, que
// avisa en el instante del corte. Antes se dependía de los eventos del WebView, que
// podían tardar minutos en reaccionar al apagar el WiFi.
const { enLinea } = useEstadoRed();
const offline = computed(() => !enLinea.value);
const pendientesOffline = ref(0);

// ── Ejecutor de cola offline ──────────────────────────────────────────────
async function ejecutarOperacion(op: OperacionPendiente): Promise<boolean> {
  try {
    switch (op.tipo) {
      case 'bitacora':
        await crearBitacora(op.body);
        return true;
      case 'culminar':
        await culminarActividadApp(op.idActividadJornada);
        return true;
      case 'cancelar':
        await cancelarActividadApp(op.idActividadJornada, op.body.observaciones);
        return true;
      case 'subir-evidencia': {
        const blob = new Blob([op.fileData], { type: op.fileType });
        const file = new File([blob], op.fileName, { type: op.fileType });
        await subirEvidenciaApp(op.idActividadJornada, file);
        return true;
      }
      default:
        return false;
    }
  } catch {
    return false;
  }
}

async function flushOffline() {
  const { ok, fail } = await vaciarColaOffline(ejecutarOperacion);
  if (ok > 0 || fail > 0) {
    console.info(`[OfflineQueue] Sincronizados ${ok} ok, ${fail} fallidos`);
  }
  pendientesOffline.value = await contarPendientes();
}

/**
 * Día libre CONFIRMADO: sólo es true cuando pudimos cargar los turnos del mes
 * (turnoCargado) Y hoy NO figura como día de turno. A prueba de fallos: si el
 * backend no responde o aún no cargó (turnoCargado=false), NO lo consideramos
 * día libre → el GPS sigue funcionando como siempre. La pausa de ubicación sólo
 * aplica cuando estamos SEGUROS de que hoy no le toca.
 */
const esDiaLibreConfirmado = computed(() => turnoCargado.value && !tieneTurnoHoy.value);

// Si confirmamos que HOY es día libre y el GPS estaba corriendo (p.ej. arrancó por
// el fail-safe mientras el backend no respondía y luego cargaron los turnos),
// detenerlo: en día libre no se rastrea ni se envían pings.
watch(esDiaLibreConfirmado, (libre) => {
  if (libre && watchActivo.value) void detenerWatch();
});

/**
 * Arranca el GPS salvo que sea un día libre confirmado. Ante cualquier duda
 * (turno hoy, o estado desconocido por backend caído) el GPS arranca. El envío
 * lo resuelve el sender canónico del composable (HTTP en nativo → background OK).
 */
async function arrancarGpsSiCorresponde() {
  if (esDiaLibreConfirmado.value) return;
  // Sólo DENTRO de la ventana del turno. Antes bastaba con que hoy fuera día de
  // turno, así que el GPS seguía corriendo a cualquier hora —incluso terminada la
  // jornada—, registrando ubicación fuera del horario laboral. Eso contradice la
  // promesa de privacidad del aviso: "solo dentro de tu horario de turno".
  // Si aún no sabemos la fase (sin datos), se mantiene el arranque por seguridad.
  if (faseTurnoHoy.value === 'antes' || faseTurnoHoy.value === 'despues') return;

  // NUNCA arrancar el servicio nativo con la app en segundo plano.
  //
  // addWatcher levanta un foreground service, y desde Android 12 hacerlo sin que
  // la app esté en primer plano lanza ForegroundServiceStartNotAllowedException,
  // que MATA el proceso — no es una excepción que se pueda atrapar en JavaScript.
  // Como este arranque también se dispara al llegar una notificación de cambio de
  // turno, la app se cerraba sola justo después del aviso. Si estamos en segundo
  // plano se pospone: reactivarEnForeground() lo reintenta al volver.
  if (Capacitor.isNativePlatform() && !appEnPrimerPlano.value) {
    console.info('[GPS] Arranque pospuesto: la app está en segundo plano.');
    return;
  }

  if (estadoUbicacion.value === 'granted' && !watchActivo.value) {
    await iniciarWatch();
  }
}

/**
 * El turno empieza o termina mientras la app está abierta.
 *
 * `faseTurnoHoy` se recalcula solo gracias al reloj reactivo de useTurnoHoy, así
 * que aquí sólo hay que actuar: arrancar el GPS al entrar en turno y detenerlo al
 * salir. Sin esto había que reiniciar la app para que se enterara.
 */
watch(faseTurnoHoy, async (fase, faseAnterior) => {
  if (fase === faseAnterior) return;

  if (fase === 'durante') {
    console.info('[turno] Comenzó el turno → activando seguimiento de ubicación.');
    await arrancarGpsSiCorresponde();
    return;
  }

  if (faseAnterior === 'durante' && watchActivo.value) {
    console.info('[turno] Terminó el turno → deteniendo el seguimiento de ubicación.');
    await detenerWatch();
    mostrarModalGps.value = false;
    enTurnoActivo.value   = false;
  }
});

/**
 * GPS denegado: mostramos la alerta de "riesgo de abandono" salvo que sea un
 * día libre confirmado (en día libre no alarmamos).
 */
async function verificarTurnoYAlertarGps() {
  await cargarTurnoHoy();
  // Sólo alarmamos si el turno está EN CURSO. Fuera de él —día libre, o antes/después
  // del horario— no hay presencia que registrar, así que avisar de "riesgo de
  // abandono" sería falso y además presionaría a dar ubicación fuera de horario.
  const enCurso = !esDiaLibreConfirmado.value
    && faseTurnoHoy.value !== 'antes'
    && faseTurnoHoy.value !== 'despues';

  enTurnoActivo.value   = enCurso;
  mostrarModalGps.value = enCurso;
}

/**
 * Ubicación del SISTEMA apagada estando de turno → bloqueo duro.
 *
 * Es un caso distinto del permiso denegado: aquí la app sí tiene permiso, pero el
 * teléfono tiene la ubicación apagada, así que no hay forma de registrar el turno.
 * Por eso este modal no ofrece "Ignorar" ni se cierra tocando fuera: la única
 * salida es encender la ubicación.
 */
const ubicacionBloqueada = computed(
  () => enTurnoActivo.value && !servicioUbicacionActivo.value,
);

// Vigilar los servicios de ubicación sólo mientras el GPS deba estar corriendo,
// es decir, durante el turno. Fuera de turno la ubicación es asunto del usuario.
watch(watchActivo, (activo) => {
  if (activo) vigilarServicioUbicacion();
  else detenerVigilanciaUbicacion();
}, { immediate: true });

// Aviso EN EL TELÉFONO cuando el turno deja de registrarse. Es local a propósito:
// si el corte es por falta de internet, ningún push del servidor puede llegar, y
// el funcionario se enteraría de la ausencia cuando ya está registrada.
iniciarAvisoSenal(() => enTurnoActivo.value);

// Al apagarse la ubicación en pleno turno hay que avisar aunque el modal de
// permiso no estuviera en pantalla: sin esto el usuario seguiría trabajando sin
// saber que dejó de registrarse y acumularía una ausencia.
watch(servicioUbicacionActivo, (activo) => {
  if (!activo) {
    console.warn('[GPS] Ubicación apagada durante el turno → bloqueando la app.');
    void verificarTurnoYAlertarGps();
  }
});

// ── Banner GPS ─────────────────────────────────────────────────────────────
const activandoBanner = ref(false);

async function activarDesdeBanner() {
  activandoBanner.value = true;
  await pedirUbicacion();
  // El sender canónico (HTTP en nativo) lo aplica iniciarWatch por defecto.
  // No arrancamos sólo si es día libre confirmado (en duda, sí arranca).
  if (!esDiaLibreConfirmado.value) {
    await iniciarWatch();
  }
  activandoBanner.value = false;
}

// ── Aviso de privacidad ────────────────────────────────────────────────────
const {
  visible:    avisoVisible,
  bloqueante: avisoBloqueante,
  aceptar:    aceptarAviso,
  cerrar:     cerrarAviso,
} = useAvisoPrivacidad();

// ── Banner Push ────────────────────────────────────────────────────────────
const activandoPush = ref(false);
/** El texto de ayuda y el botón de Ajustes cambian entre APK y navegador. */
const esNativo = Capacitor.isNativePlatform();

const mostrarBannerPush = computed(() =>
  estadoNotificacion.value !== 'granted' && estadoNotificacion.value !== 'unknown',
);

async function activarPush() {
  activandoPush.value = true;
  await pedirNotificaciones();
  activandoPush.value = false;
}

// ── Notificaciones Capacitor (FCM nativo) ─────────────────────────────────
/**
 * Los listeners de PushNotifications viven en el plugin nativo, no en el
 * componente: no se desmontan con AppLayout. Sin esta guarda, cada remount del
 * layout (navegar fuera de /app y volver, o logout→login) instalaba OTRO listener
 * `registration`, y un solo token acababa disparando N POST simultáneos.
 */
let pushListenersInstalados = false;

async function iniciarPushNativo() {
  if (!Capacitor.isNativePlatform()) return;
  // Diagnóstico VISIBLE en pantalla (sin depender de logcat): cada paso del
  // registro FCM muestra un aviso 🔧. Los errores/avisos quedan fijos (duration:0).
  // TODO: quitar estos diagPush() cuando el push quede confirmado funcionando.
  const diagPush = (
    type: 'error' | 'info' | 'success' | 'warning',
    title: string,
    content = '',
  ) => {
    const msg = content ? `[FCM] ${title} — ${content}` : `[FCM] ${title}`;
    if (type === 'error' || type === 'warning') console.warn(msg);
    else console.info(msg);
  };

  diagPush('info', 'Push: iniciando registro FCM…');
  try {
    const { PushNotifications } = await import('@capacitor/push-notifications');

    // ── El permiso se comprueba ANTES de montar nada ────────────────────────
    // Si el usuario denegó las notificaciones a nivel sistema, no se crea el
    // canal, no se instalan listeners y no se registra el token: sin permiso
    // Android descarta igualmente todo lo que llegue, así que montar el servicio
    // sólo gastaba batería y ensuciaba el log. El banner informa al usuario.
    // Si más tarde concede el permiso desde Ajustes, el watch de estadoNotificacion
    // vuelve a llamar aquí y entonces sí se monta todo.
    let perm = await PushNotifications.checkPermissions();
    if (perm.receive === 'prompt' || perm.receive === 'prompt-with-rationale') {
      perm = await PushNotifications.requestPermissions();
    }
    estadoNotificacion.value =
      perm.receive === 'granted' ? 'granted'
      : (perm.receive === 'denied' ? 'denied' : 'prompt');
    diagPush('info', `Permiso notificaciones: ${perm.receive}`);

    if (perm.receive !== 'granted') {
      console.warn('[FCM] permiso de notificaciones NO concedido:', perm.receive,
        '→ servicios de push locales detenidos; no se registrará token.');
      diagPush('warning', 'Permiso de notificaciones NO concedido', perm.receive);
      return;
    }

    // Canal con sonido personalizado (Android 8+: el sonido va por CANAL, no por
    // mensaje). El id 'sisu_alertas' y el sonido 'notificacion_nutria' deben
    // coincidir con FcmService (backend). Archivo: res/raw/notificacion_nutria.mp3.
    // OJO: si el canal YA existe, Android NO actualiza el sonido — para cambiarlo
    // hay que usar un id de canal nuevo (o reinstalar la app).
    try {
      await PushNotifications.createChannel({
        id: 'sisu_alertas',
        name: 'Alertas SISU',
        description: 'Avisos de monitoreo, turnos y permisos',
        importance: 5,                 // HIGH → sonido + heads-up
        sound: 'notificacion_nutria',  // sin extensión
        vibration: true,
        visibility: 1,
        lights: true,
      });
      diagPush('info', 'Canal de sonido "sisu_alertas" listo');
    } catch (eCanal: any) {
      console.warn('[FCM] No se pudo crear el canal de sonido:', eCanal);
    }

    // Los listeners se instalan UNA sola vez por vida del proceso (ver
    // pushListenersInstalados). El bloque de permisos/register de más abajo sí
    // vuelve a correr en cada llamada: es lo que refresca el token.
    if (!pushListenersInstalados) {
      pushListenersInstalados = true;

      // Escuchar token FCM y enviarlo al backend. registrarTokenFcm() colapsa los
      // eventos repetidos (un mismo token reemitido por cada register()) en un único
      // POST — antes salían varios en paralelo y chocaban contra idx_push_endpoint.
      PushNotifications.addListener('registration', async (token) => {
        console.info('[FCM] token recibido, registrando en backend:', token.value?.slice(0, 24) + '…');
        diagPush('success', 'Token FCM recibido', (token.value ?? '').slice(0, 24) + '…');
        try {
          await registrarTokenFcm(token.value ?? '');
          diagPush('success', 'Token registrado en backend OK ✅');
        } catch (error: any) {
          console.warn('[FCM] FALLO al registrar token en backend:', error);
          diagPush('error', 'FALLO registrando token en backend', String(error?.message ?? error));
        }
      });

      // Error de registro FCM (google-services mal configurado, sin red, etc.)
      PushNotifications.addListener('registrationError', (err) => {
        console.warn('[FCM] registrationError:', JSON.stringify(err));
        diagPush('error', 'FCM registrationError', JSON.stringify(err));
      });

      // Notificación recibida con la app abierta → mostrar toast
      PushNotifications.addListener('pushNotificationReceived', (notification) => {
        const tipo = (notification.data?.tipo as string) ?? 'notificacion';

        // Recargar el turno también por esta vía. El WebSocket se cae cuando el
        // sistema congela la app, así que el aviso llega por FCM pero `notifRtSignal`
        // nunca se emite: la pantalla se quedaba con el horario viejo hasta volver a
        // primer plano. Por eso el cambio tardaba en aparecer pese a llegar el aviso.
        if (TIPOS_RECARGAR_TURNO.has(tipo)) {
          void cargarTurnoHoy(true);
        }

        // El título ya viene formateado del backend (MAYÚSCULAS + icono al final).
        notification_api.create({
          title:   notification.title ?? 'SICOPE',
          content: notification.body ?? '',
          type:    tipo.includes('RECHAZADO') || tipo.includes('AUSENCIA') ? 'error'
                   : (tipo.includes('APROBADO') || tipo.includes('ASIGNADA')
                      || tipo === 'RETORNO_ZONA' || tipo === 'FIN_TURNO' ? 'success'
                   : 'info'),
          duration: 5000,
          keepAliveOnHover: true,
        });
      });
    }

    // Con el permiso ya confirmado arriba, registrar el token. Antes sólo se
    // registraba si tocabas el banner y el permiso ya venía concedido → fácil de
    // pasar por alto → sin token FCM no llega NINGÚN push fuera de la app.
    try {
      await PushNotifications.register();
      console.info('[FCM] register() llamado (permiso concedido)');
      diagPush('info', 'register() llamado — esperando token de Google…');
    } catch (error: any) {
      console.warn('[FCM] Error en register():', error);
      diagPush('error', 'Error pidiendo permiso / register()', String(error?.message ?? error));
    }
  } catch (error: any) {
    console.warn('[FCM] No se pudo inicializar push nativo:', error);
    diagPush('error', 'No se pudo inicializar push nativo', String(error?.message ?? error));
  }
}

// ── Toast al llegar notificación WS (en primer plano) ─────────────────────
const notification_api = useNotification();

const TIPO_NOTIF_TYPE: Record<string, 'error' | 'info' | 'success' | 'warning'> = {
  JORNADA_ASIGNADA:   'info',    ACTIVIDAD_ASIGNADA: 'success',
  PERMISO_APROBADO:   'success', PERMISO_RECHAZADO:  'error',
  PERMISO_OBSERVADO:  'warning', NUEVA_SOLICITUD:    'warning',
  AUSENCIA_DETECTADA: 'error',   GPS_DENEGADO:       'warning',
  RECORDATORIO_TURNO: 'info',    RETORNO_ZONA:       'success',
  FIN_TURNO:          'success', JORNADA_ACTUALIZADA: 'warning',
};

/** Avisos que invalidan el horario que la app tiene cargado. */
const TIPOS_RECARGAR_TURNO = new Set([
  'FIN_TURNO',
  'HORARIO_ASIGNADO',
  'JORNADA_ACTUALIZADA',
  'JORNADA_ASIGNADA',
  'RECORDATORIO_TURNO',
]);

// La recarga del turno vive en el LAYOUT y no en la pantalla de inicio: si el
// usuario está en Turnos o Actividades, el Home no está montado y su watcher no
// corre, así que el cambio de horario no se aplicaba hasta volver al inicio. Aquí
// se aplica esté donde esté, y de paso reevalúa el GPS por el watcher de fase.
watch(notifRtSignal, async (notif) => {
  if (!notif || !TIPOS_RECARGAR_TURNO.has(notif.tipo)) return;
  await cargarTurnoHoy(true);
});

watch(notifRtSignal, (notif) => {
  if (!notif) return;
  // Título ya formateado por el backend (MAYÚSCULAS + icono al final).
  notification_api.create({
    title:            notif.titulo,
    content:          notif.mensaje,
    type:             TIPO_NOTIF_TYPE[notif.tipo] ?? 'info',
    duration:         4500,
    keepAliveOnHover: true,
  });
});

// ── Arrancar GPS / alertar al cambiar el permiso de ubicación ─────────────
watch(estadoUbicacion, async (estado) => {
  console.info('[GPS-DEBUG] app.vue watch(estadoUbicacion) →', estado, '| watchActivo=', watchActivo.value, '| turnoHoy=', tieneTurnoHoy.value);
  if (estado === 'granted' && !watchActivo.value) {
    mostrarModalGps.value = false;
    enTurnoActivo.value   = false;
    // Sólo arranca el GPS si HOY es día de turno; si no, queda en pausa.
    await arrancarGpsSiCorresponde();
  }
  if (estado === 'denied') {
    await verificarTurnoYAlertarGps();
  }
});

// ── Montar FCM cuando se concede el permiso de notificaciones ──────────────
// Debe llamar a iniciarPushNativo() completo, NO sólo a register(): si la app
// arrancó con el permiso denegado, los listeners nunca se instalaron, y un
// register() suelto traería un token que nadie estaría escuchando. La función es
// idempotente (pushListenersInstalados), así que repetirla no duplica nada.
watch(estadoNotificacion, (estado) => {
  if (estado !== 'granted' || !Capacitor.isNativePlatform()) return;
  void iniciarPushNativo();
});

// ── Ciclo de vida ──────────────────────────────────────────────────────────
function rolesUsuario(): string[] {
  const rawRoles = userStore.userRoles ?? [];
  return rawRoles.map((r: any) =>
    typeof r === 'string' ? r : (r.value ?? r.roleName ?? ''),
  );
}

/**
 * Re-verifica permisos y reactiva servicios cuando la app vuelve al primer plano.
 * Resuelve el caso en que el usuario concede GPS/notificaciones en Ajustes del SO
 * y regresa: sin esto, el banner "Conceder permiso" quedaba pegado porque el
 * estado solo se leía una vez al montar.
 */
async function reactivarEnForeground() {
  await verificarEstado();
  // Recalcular si hoy es día de turno (pudo cambiar el día estando en background).
  await cargarTurnoHoy(true);

  // En Android, el sistema pudo destruir la Activity y con ella el servicio nativo
  // de GPS (handleOnDestroy del plugin lo mata). watchActivo queda true aunque el
  // servicio ya no exista. Siempre reiniciamos en nativo al volver a primer plano.
  if (watchActivo.value && Capacitor.isNativePlatform()) {
    console.info('[GPS-DEBUG] reactivarEnForeground: reiniciando watch nativo');
    await detenerWatch();
  }

  // GPS concedido y hoy es turno pero el watch no corre → arrancarlo.
  await arrancarGpsSiCorresponde();

  // Notificaciones concedidas → re-registrar el token FCM (puede haber rotado).
  // Vía iniciarPushNativo() para cubrir el caso de que el usuario acabe de
  // conceder el permiso en Ajustes: ahí hay que montar canal y listeners, no sólo
  // registrar. Si el permiso sigue denegado, la función sale sola sin montar nada.
  if (Capacitor.isNativePlatform()) {
    void iniciarPushNativo();
  }

  // WebSocket caído (el SO pudo matarlo en background) → reconectar.
  if (!conectado.value) connect(rolesUsuario());
}

/**
 * Arranca todo lo que necesita el backend: WebSocket, push y turno del día, más
 * el vaciado de la cola GPS bufferizada.
 *
 * Va aparte del `onMounted` porque también se invoca cuando vuelve la conexión:
 * si la app se abrió sin red, estos servicios no llegaron a levantarse y sin esto
 * quedaban muertos hasta que el usuario reiniciara la app.
 *
 * Idempotente: `connect()` corta si el cliente STOMP ya está activo, los listeners
 * de push están protegidos por `pushListenersInstalados`, y `cargarTurnoHoy` y
 * `flushNow` son repetibles.
 */
async function arrancarServiciosDeRed() {
  // Vaciar cola offline de GPS (fixes bufferizados mientras no había señal)
  void flushNow();

  // Conectar WS PRIMERO (fire-and-forget, handshake en background).
  connect(rolesUsuario());

  // Push FCM: fire-and-forget. NO bloquea GPS — puede tardar 1-3s (dynamic import,
  // canal, permisos). Si el backend tarda, el WS ya anda y el GPS arranca antes.
  void iniciarPushNativo();

  // Cargar turno de hoy: fire-and-forget. NO bloquea GPS — el fail-safe de
  // esDiaLibreConfirmado permite arrancar GPS antes de saber si hay turno.
  void cargarTurnoHoy(true).catch((e) =>
    console.warn('[GPS-DEBUG] cargarTurnoHoy falló al arrancar servicios de red', e),
  );
}

// ── Reacción a los cambios de red ─────────────────────────────────────────
// Al recuperar conexión: vaciar las colas bufferizadas y levantar lo que no pudo
// arrancar sin red (WS, push, turno). Al perderla no hay nada que hacer más que
// mostrar el banner — el `offline` computed ya lo activa solo.
watch(enLinea, (hayRed, habiaRed) => {
  if (hayRed === habiaRed) return;
  if (hayRed) {
    console.info('[red] Conexión recuperada: sincronizando pendientes y reanudando servicios.');
    void flushNow();
    void flushOffline();
    void arrancarServiciosDeRed();
  } else {
    console.warn('[red] Conexión perdida: se trabaja en modo offline, los envíos quedan en cola.');
  }
});

let resumeHandle: null | { remove: () => void } = null;

onMounted(async () => {
  // Captura la consola del frontend y la envía al backend (Consola del sistema).
  iniciarLogShipper();
  console.info('[GPS-DEBUG] app.vue onMounted INICIO | native=', Capacitor.isNativePlatform());

  // ── Estado de red PRIMERO ────────────────────────────────────────────────
  // Todo lo de abajo habla con el backend. Lanzarlo sin saber si hay conexión era
  // lo que producía la ráfaga de "Error de red" al abrir la app recién instalada:
  // media docena de peticiones fallando a la vez, cada una con su aviso.
  const hayRed = await iniciarMonitoreoRed();
  pendientesOffline.value = await contarPendientes();

  // ── Consentimiento antes que cualquier permiso ───────────────────────────
  // En el primer ingreso se explica qué se registra y cuándo, y se espera a que
  // el usuario acepte ANTES de que se disparen los diálogos de ubicación y
  // notificaciones. Si ya aceptó, esto resuelve al instante y no interrumpe nada.
  await asegurarConsentimiento(userStore.userInfo?.username ?? null);

  if (hayRed) {
    void arrancarServiciosDeRed();
  } else {
    console.warn('[arranque] Sin conexión: se posponen WS, push y turno hasta que vuelva la red.');
  }

  // 1. Verificar permisos de ubicación: RÁPIDO (100-300ms, solo Capacitor IPC).
  //    Necesario para saber si estadoUbicacion === 'granted'.
  try {
    await verificarEstado();
  } catch (error) {
    console.warn('[GPS-DEBUG] verificarEstado falló en onMounted', error);
  }
  console.info('[GPS-DEBUG] tras verificarEstado | ubicacion=', estadoUbicacion.value, '| notif=', estadoNotificacion.value, '| turnoHoy=', tieneTurnoHoy.value);

  // Si el GPS ya está denegado al montar y HOY es turno, mostrar modal de alerta.
  if (estadoUbicacion.value === 'denied') {
    await verificarTurnoYAlertarGps();
  }

  // 2. GPS — arrancar INMEDIATAMENTE si permiso concedido. NO espera a cargarTurnoHoy:
  //    si turnoCargado=false, esDiaLibreConfirmado=false → GPS arranca (fail-safe).
  await arrancarGpsSiCorresponde();

  // 5. Re-verificar permisos/servicios cada vez que la app vuelve al primer plano.
  if (Capacitor.isNativePlatform()) {
    resumeHandle = await CapacitorApp.addListener('appStateChange', ({ isActive }) => {
      appEnPrimerPlano.value = isActive;
      if (isActive) void reactivarEnForeground();
    });
  }
});

onUnmounted(async () => {
  detenerAvisoSenal();
  detenerMonitoreoRed();
  resumeHandle?.remove();
  resumeHandle = null;
  await detenerWatch();
  disconnect();
});

const nombreUsuario = computed(
  () => userStore.userInfo?.realName ?? userStore.userInfo?.username ?? 'Usuario',
)
const iniciales = computed(() => {
  const n = nombreUsuario.value
  return n.split(' ').map((p) => p[0]).slice(0, 2).join('').toUpperCase()
})

// ── Selector de idioma ─────────────────────────────────────────────────────
type AppLocale = 'es-ES' | 'pt-BR'
const currentLang = ref<AppLocale>('es-ES')

onMounted(() => {
  const saved = preferences.app.locale as AppLocale
  currentLang.value = (saved === 'es-ES' || saved === 'pt-BR') ? saved : 'es-ES'
  loadLocaleMessages(currentLang.value)
})

async function switchLang(lang: AppLocale) {
  if (currentLang.value === lang) return
  currentLang.value = lang
  updatePreferences({ app: { locale: lang } })
  await loadLocaleMessages(lang)
}

const NAV_ITEMS = [
  { path: '/app/home',           label: 'Inicio',   icon: 'home'     },
  { path: '/app/turnos',         label: 'Turnos',   icon: 'calendar' },
  { path: '/app/actividades',    label: 'Activid.', icon: 'list'     },
  { path: '/app/permisos',       label: 'Permisos', icon: 'file'     },
  { path: '/app/faltas',         label: 'Faltas',   icon: 'alert'    },
] as const;

function isActive(path: string) {
  return route.path.startsWith(path)
}

const RUTAS_RESTRINGIDAS_TURNO = ['/app/actividades', '/app/permisos'];

function isNavItemDisabled(item: typeof NAV_ITEMS[number]): boolean {
  if (enHorarioTurno.value) return false;
  return RUTAS_RESTRINGIDAS_TURNO.includes(item.path);
}

function handleNavClick(item: typeof NAV_ITEMS[number]) {
  if (isNavItemDisabled(item)) {
    message.warning('Disponible durante tu turno');
    return;
  }
  router.push(item.path);
}

// Mostrar el botón "volver" en todas las pantallas que no sean el inicio.
const enHome = computed(() => route.path === '/app' || route.path === '/app/home');
function volverHome() {
  router.push('/app/home');
}

function cerrarModalGps() {
  mostrarModalGps.value = false;
}

const dialog = useDialog();

function cerrarSesion() {
  // Si está en turno activo, advertir con fuerza: al cerrar sesión deja de
  // reportar su ubicación y, tras unos minutos sin señal, el sistema puede
  // registrarlo como abandono de puesto / falta.
  const enTurno = gpsStatus.value?.enTurnoActivo ?? tieneTurnoHoy.value;
  if (enTurno) {
    dialog.error({
      title: '⚠️ Estás en tu turno',
      content:
        'Si cierras sesión dejarás de reportar tu ubicación. Pasados unos minutos sin señal, ' +
        'el sistema puede registrarlo como ABANDONO de puesto y contarlo como falta. ' +
        '¿Seguro que deseas cerrar sesión?',
      positiveText: 'Cerrar sesión de todos modos',
      negativeText: 'Seguir en mi turno',
      onPositiveClick: async () => {
        await authStore.logout();
      },
    });
    return;
  }
  dialog.warning({
    title: 'Cerrar sesión',
    content: '¿Estás seguro de que deseas cerrar tu sesión?',
    positiveText: 'Sí, cerrar',
    negativeText: 'Cancelar',
    onPositiveClick: async () => {
      await authStore.logout();
    },
  });
}

function perfilSvg() {
  return h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2', width: '16', height: '16' }, [
    h('path', { d: 'M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2' }),
    h('circle', { cx: '12', cy: '7', r: '4' }),
  ]);
}
function logoutSvg() {
  return h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2', width: '16', height: '16' }, [
    h('path', { d: 'M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4' }),
    h('polyline', { points: '16 17 21 12 16 7' }),
    h('line', { x1: '21', y1: '12', x2: '9', y2: '12' }),
  ]);
}

function privacidadSvg() {
  return h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2', width: '16', height: '16' }, [
    h('path', { d: 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z' }),
  ]);
}

const perfilOptions = [
  {
    label: 'Ver mi perfil',
    key: 'perfil',
    icon: perfilSvg,
  },
  {
    label: 'Privacidad y condiciones',
    key: 'privacidad',
    icon: privacidadSvg,
  },
  {
    label: 'Cerrar sesión',
    key: 'logout',
    icon: logoutSvg,
  },
];

function onPerfilSelect(key: string) {
  if (key === 'perfil') router.push('/app/perfil');
  if (key === 'privacidad') mostrarAvisoPrivacidad();
  if (key === 'logout') cerrarSesion();
}
</script>

<template>
  <div class="app-shell">
<!-- ── Header ─────────────────────────────── -->
    <header class="app-header">
      <div class="app-header__brand">
        <button
          v-if="!enHome"
          class="app-header__back"
          aria-label="Volver al inicio"
          @click="volverHome"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <polyline points="15 18 9 12 15 6" />
          </svg>
        </button>
        <img src="/logoBlanco.png" alt="SICOPE" class="app-header__logo" />
        <div>
          <div class="app-header__name">SICOPE UAP</div>
        </div>
      </div>

      <!-- ── Acciones: notificaciones · idioma · perfil ── -->
      <div class="app-header__actions">
        <button
          class="app-header__bell"
          aria-label="Notificaciones"
          @click="router.push('/app/notificaciones')"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
            <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
            <path d="M13.73 21a2 2 0 0 1-3.46 0" />
          </svg>
          <span v-if="noLeidas > 0" class="bell-badge">
            {{ noLeidas > 9 ? '9+' : noLeidas }}
          </span>
        </button>

        <div class="app-lang-toggle">
          <button
            class="app-lang-btn"
            :class="{ 'app-lang-btn--active': currentLang === 'es-ES' }"
            @click="switchLang('es-ES')"
          >
            ES
          </button>
          <button
            class="app-lang-btn"
            :class="{ 'app-lang-btn--active': currentLang === 'pt-BR' }"
            @click="switchLang('pt-BR')"
          >
            PT
          </button>
        </div>

        <NDropdown
          placement="bottom-end"
          trigger="click"
          :options="perfilOptions"
          @select="onPerfilSelect"
        >
          <button
            class="app-header__avatar"
            aria-label="Mi perfil"
          >
            {{ iniciales }}
          </button>
        </NDropdown>
      </div>
    </header>

    <!-- ── Banner GPS — sólo con el turno EN CURSO ──
         Antes bastaba con que hoy fuera día de turno, así que ofrecía "activar
         ubicación" a cualquier hora: daba a entender que se rastrea todo el día. -->
    <Transition name="banner">
      <div
        v-if="!watchActivo && !esDiaLibreConfirmado && enHorarioTurno"
        class="gps-banner"
        :class="{
          'gps-banner--error': errorGps || estadoUbicacion === 'denied',
          'gps-banner--critico': estadoUbicacion === 'denied' && enTurnoActivo,
        }"
      >
        <div class="gps-banner__left">
          <span class="gps-banner__pulse"></span>
          <div class="gps-banner__text">
            <span class="gps-banner__title">
              {{ estadoUbicacion === 'denied' && enTurnoActivo
                  ? '⛔ Ubicación bloqueada — riesgo de abandono'
                  : (errorGps ? 'GPS denegado' : 'GPS desactivado') }}
            </span>
            <span class="gps-banner__sub">
              {{ estadoUbicacion === 'denied' && enTurnoActivo
                  ? 'Sin GPS durante tu turno se registrará como ausencia'
                  : (errorGps
                      ? 'Habilitá la ubicación en tu navegador'
                      : 'El sistema no registra tu presencia') }}
            </span>
          </div>
        </div>
        <button
          class="gps-banner__btn"
          :disabled="activandoBanner"
          @click="activarDesdeBanner"
        >
          <span v-if="activandoBanner" class="gps-banner__spin"></span>
          <span v-else>{{ errorGps || estadoUbicacion === 'denied' ? 'Habilitar' : 'Activar GPS' }}</span>
        </button>
      </div>
    </Transition>

    <!-- ── Banner GPS desactivado (watch activo pero GPS_PROVIDER apagado) ── -->
    <Transition name="banner">
      <div
        v-if="watchActivo && gpsActivo === false && !esDiaLibreConfirmado && enHorarioTurno"
        class="gps-banner gps-banner--error"
      >
        <div class="gps-banner__left">
          <span class="gps-banner__pulse"></span>
          <div class="gps-banner__text">
            <span class="gps-banner__title">⚠️ GPS desactivado</span>
            <span class="gps-banner__sub">
              El GPS está apagado — la ubicación por red es imprecisa. Activá el GPS en tu celular.
            </span>
          </div>
        </div>
        <button
          class="gps-banner__btn"
          :disabled="activandoBanner"
          @click="abrirConfiguracionApp"
        >
          Abrir configuración
        </button>
      </div>
    </Transition>

    <!-- ── Banner Ubicación en pausa (día libre CONFIRMADO) ── -->
    <Transition name="banner">
      <div
        v-if="!watchActivo && esDiaLibreConfirmado"
        class="pausa-banner"
      >
        <span class="pausa-banner__icon">📍</span>
        <div class="gps-banner__text">
          <span class="pausa-banner__title">Ubicación en pausa</span>
          <span class="pausa-banner__sub">Hoy no tienes turno · se activará el día que te toque</span>
        </div>
      </div>
    </Transition>

    <!-- ── Banner Push (visible cuando el permiso no está concedido) ── -->
    <Transition name="banner">
      <div
v-if="mostrarBannerPush" class="push-banner"
           :class="{ 'push-banner--blocked': permisoNotif === 'denied' }"
>
        <div class="gps-banner__left">
          <span class="push-banner__pulse"></span>
          <div class="gps-banner__text">
            <span class="push-banner__title">
              {{ permisoNotif === 'denied' ? 'Notificaciones bloqueadas' : 'Notificaciones desactivadas' }}
            </span>
            <span class="push-banner__sub">
              {{ permisoNotif === 'denied'
                  ? (esNativo
                      ? 'Para recibir alertas importantes, habilite las notificaciones en la configuración de su teléfono'
                      : 'Para recibir alertas importantes, habilite las notificaciones en su navegador')
                  : 'No recibirás alertas fuera de la app' }}
            </span>
          </div>
        </div>
        <button
          v-if="permisoNotif !== 'denied'"
          class="gps-banner__btn"
          :disabled="activandoPush"
          @click="activarPush"
        >
          <span v-if="activandoPush" class="gps-banner__spin"></span>
          <span v-else>Activar</span>
        </button>
        <!-- Denegado: el permiso ya no se puede volver a pedir desde la app, hay
             que ir a Ajustes. En nativo se abre directo; en web sólo queda la pista. -->
        <button
          v-else-if="esNativo"
          class="gps-banner__btn"
          @click="abrirConfiguracionApp"
        >
          Ajustes
        </button>
        <span v-else class="push-banner__hint">
          Sitio → Notificaciones → Permitir
        </span>
      </div>
    </Transition>

    <!-- ── Banner offline ── -->
    <Transition name="banner">
      <div
        v-if="offline || pendientesOffline > 0"
        class="offline-banner"
        :class="{ 'offline-banner--syncing': !offline && pendientesOffline > 0 }"
      >
        <span class="offline-banner__icon">{{ offline ? '📡' : '🔄' }}</span>
        <div class="gps-banner__text">
          <span class="offline-banner__title">
            {{ offline
              ? 'Dispositivo sin conexión a internet, esperando red...'
              : 'Sincronizando...' }}
          </span>
          <span class="offline-banner__sub">
            {{ pendientesOffline > 0
              ? `${pendientesOffline} operaciones pendientes de sincronizar`
              : 'Tu actividad se guarda y se enviará al recuperar la señal' }}
          </span>
        </div>
      </div>
    </Transition>

    <!-- ── Aviso de privacidad y condiciones ──
         Primer ingreso: bloqueante, antes de pedir permisos (spec §10). Reabierto
         desde el menú de perfil: informativo y cerrable. -->
    <Transition name="modal-fade">
      <div
        v-if="avisoVisible"
        class="aviso-overlay"
        @click.self="cerrarAviso"
      >
        <div class="aviso-card">
          <div class="aviso-card__icon">🛡️</div>
          <h2 class="aviso-card__title">Privacidad y condiciones de uso</h2>

          <div class="aviso__lista">
            <div class="aviso__item">
              <span class="aviso__k">Qué hace esta app</span>
              <span class="aviso__v">
                Durante tu turno registra tu ubicación para confirmar tu presencia en
                el lugar que tenés asignado.
              </span>
            </div>
            <div class="aviso__item">
              <span class="aviso__k">Cuándo se registra</span>
              <span class="aviso__v">
                <strong>Solo dentro de tu horario de turno.</strong> Fuera de turno no
                se registra tu ubicación.
              </span>
            </div>
            <div class="aviso__item">
              <span class="aviso__k">Si algo falla</span>
              <span class="aviso__v">
                Un atraso, una falta o un abandono quedan registrados, pero
                <strong>siempre podés justificarlos</strong> desde la app. Nada se
                penaliza de forma automática.
              </span>
            </div>
            <div class="aviso__item">
              <span class="aviso__k">Al cerrar sesión</span>
              <span class="aviso__v">
                Se detiene todo el registro y dejás de recibir notificaciones de
                control en este teléfono.
              </span>
            </div>
            <div class="aviso__item">
              <span class="aviso__k">Permisos que se piden</span>
              <span class="aviso__v">
                <strong>Ubicación</strong> (permitir todo el tiempo, para que funcione
                con la app minimizada) y <strong>Notificaciones</strong>, para avisarte
                del inicio y fin de tu turno.
              </span>
            </div>
          </div>

          <button v-if="avisoBloqueante" class="aviso-card__btn" @click="aceptarAviso">
            Entiendo y acepto
          </button>
          <button v-else class="aviso-card__btn" @click="cerrarAviso">
            Cerrar
          </button>
          <p v-if="avisoBloqueante" class="aviso-card__nota">
            Podés volver a leer esto cuando quieras desde tu perfil.
          </p>
        </div>
      </div>
    </Transition>

    <!-- ── Modal BLOQUEANTE: ubicación del sistema apagada durante el turno ──
         Sin @click.self ni botón de descarte a propósito: con la ubicación
         apagada no se puede registrar el turno, así que la única salida es
         encenderla. Se antepone al modal de permiso porque es la causa más
         grave y no admite continuar. -->
    <Transition name="modal-fade">
      <div v-if="ubicacionBloqueada" class="gps-modal-overlay">
        <div class="gps-modal">
          <div class="gps-modal__icon">📍</div>
          <h2 class="gps-modal__title">Debe habilitar la ubicación para registrar el turno</h2>
          <p class="gps-modal__body">
            La ubicación de tu teléfono está <strong>apagada</strong> y estás en
            horario de turno. Mientras siga apagada
            <strong>no se puede registrar tu presencia</strong>.
          </p>
          <div class="gps-modal__warning">
            <span class="gps-modal__warning-icon">⚠️</span>
            El sistema registrará una <strong>ausencia</strong> si el turno
            transcurre sin ubicación.
          </div>
          <button class="gps-modal__btn-primary" @click="abrirAjustesUbicacion">
            Activar ubicación
          </button>
          <p class="gps-modal__nota">
            Al volver a la app, la verificación se reanuda automáticamente.
          </p>
        </div>
      </div>
    </Transition>

    <!-- ── Modal GPS denegado durante turno activo ── -->
    <Transition name="modal-fade">
      <div
        v-if="mostrarModalGps && estadoUbicacion === 'denied' && enTurnoActivo && !ubicacionBloqueada"
        class="gps-modal-overlay"
        @click.self="cerrarModalGps"
      >
        <div class="gps-modal">
          <div class="gps-modal__icon">📍</div>
          <h2 class="gps-modal__title">Ubicación requerida</h2>
          <p class="gps-modal__body">
            Estás en tu turno laboral pero
            <strong>no se puede verificar tu ubicación</strong>
            porque el acceso GPS está bloqueado.
          </p>
          <div class="gps-modal__warning">
            <span class="gps-modal__warning-icon">⚠️</span>
            Sin verificación de ubicación, el sistema
            <strong>registrará tu ausencia</strong> como abandono de puesto.
          </div>
          <button
            class="gps-modal__btn-primary"
            :disabled="activandoBanner"
            @click="activarDesdeBanner(); cerrarModalGps()"
          >
            <span v-if="activandoBanner" class="gps-banner__spin"></span>
            <span v-else>Habilitar ubicación ahora</span>
          </button>
          <button class="gps-modal__btn-secondary" @click="cerrarModalGps">
            Ignorar (riesgo de ausencia)
          </button>
        </div>
      </div>
    </Transition>

    <!-- ── Contenido ──────────────────────────── -->
    <main class="app-main">
      <RouterView />
    </main>

    <!-- ── Barra de navegación inferior ──────── -->
    <nav class="app-bottom-nav">
      <div
        v-for="item in NAV_ITEMS"
        :key="item.path"
        class="app-nav-item"
        :class="{
          'app-nav-item--active': isActive(item.path),
          'app-nav-item--home': item.icon === 'home',
          'app-nav-item--disabled': isNavItemDisabled(item),
        }"
        @click="handleNavClick(item)"
      >
        <div style="position:relative; display:inline-flex">
          <svg
v-if="item.icon === 'home'" class="app-nav-icon" viewBox="0 0 24 24"
              fill="none" stroke="currentColor" stroke-width="2"
>
            <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
            <polyline points="9 22 9 12 15 12 15 22" />
          </svg>
          <svg
v-if="item.icon === 'calendar'" class="app-nav-icon" viewBox="0 0 24 24"
              fill="none" stroke="currentColor" stroke-width="2"
>
            <rect x="3" y="4" width="18" height="18" rx="2" ry="2" />
            <line x1="16" y1="2" x2="16" y2="6" />
            <line x1="8" y1="2" x2="8" y2="6" />
            <line x1="3" y1="10" x2="21" y2="10" />
          </svg>
          <svg
v-if="item.icon === 'list'" class="app-nav-icon" viewBox="0 0 24 24"
              fill="none" stroke="currentColor" stroke-width="2"
>
            <line x1="8" y1="6" x2="21" y2="6" />
            <line x1="8" y1="12" x2="21" y2="12" />
            <line x1="8" y1="18" x2="21" y2="18" />
            <line x1="3" y1="6" x2="3.01" y2="6" />
            <line x1="3" y1="12" x2="3.01" y2="12" />
            <line x1="3" y1="18" x2="3.01" y2="18" />
          </svg>
          <svg
v-if="item.icon === 'file'" class="app-nav-icon" viewBox="0 0 24 24"
              fill="none" stroke="currentColor" stroke-width="2"
>
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
            <polyline points="14 2 14 8 20 8" />
          </svg>
          <svg
v-if="item.icon === 'alert'" class="app-nav-icon" viewBox="0 0 24 24"
              fill="none" stroke="currentColor" stroke-width="2"
>
            <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
            <line x1="12" y1="9" x2="12" y2="13" />
            <line x1="12" y1="17" x2="12.01" y2="17" />
          </svg>
        </div>
        <span class="app-nav-label">{{ item.label }}</span>
      </div>
    </nav>
</div>
</template>

<style scoped>
/* ── Shell ──────────────────────────────────────────────── */
.app-shell {
  display: flex;
  flex-direction: column;
  max-width: 480px;
  height: 100dvh;           /* dynamic viewport height (mobile) */
  position: relative;
  margin: 0 auto;
  overflow: hidden;
  background: #f1f5f9;
}

/* ── Header ─────────────────────────────────────────────── */
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: linear-gradient(135deg, #1e3a8a 0%, #2563eb 100%);
  color: white;
  flex-shrink: 0;
  box-shadow: 0 2px 12px rgba(30, 58, 138, 0.25);
  z-index: 10;
}
.app-header__brand {
  display: flex;
  align-items: center;
  gap: 10px;
}
.app-header__back {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  margin-left: -4px;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
  cursor: pointer;
  flex-shrink: 0;
  -webkit-tap-highlight-color: transparent;
  transition: background 0.15s;
}
.app-header__back:active {
  background: rgba(255, 255, 255, 0.3);
}
.app-header__back svg {
  width: 18px;
  height: 18px;
  display: block;
}
.app-header__logo {
  width: 34px;
  height: 34px;
  object-fit: contain;
  flex-shrink: 0;
  border-radius: 8px;
}
.app-header__name {
  font-size: 16px;
  font-weight: 700;
  line-height: 1.2;
}
.app-lang-toggle {
  display: flex;
  align-items: center;
  gap: 4px;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 16px;
  padding: 2px 4px;
}
.app-lang-btn {
  background: transparent;
  border: none;
  color: rgba(255, 255, 255, 0.7);
  font-size: 11px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 12px;
  cursor: pointer;
  letter-spacing: 0.5px;
  transition: all 0.2s;
  -webkit-tap-highlight-color: transparent;
}
.app-lang-btn--active {
  background: rgba(255, 255, 255, 0.3);
  color: white;
}
.app-header__actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.app-header__bell {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
  border: none;
  cursor: pointer;
  flex-shrink: 0;
  -webkit-tap-highlight-color: transparent;
  transition: background 0.15s;
}
.app-header__bell:active { background: rgba(255, 255, 255, 0.3); }

.bell-badge {
  position: absolute;
  top: -2px;
  right: -2px;
  background: #dc2626;
  color: white;
  font-size: 9px;
  font-weight: 700;
  min-width: 16px;
  height: 16px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 3px;
  line-height: 1;
  box-shadow: 0 1px 3px rgba(220, 38, 38, 0.35);
  pointer-events: none;
}

.app-header__avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  color: #fff;
  padding: 0;
  font-family: inherit;
  border: 2px solid rgba(255, 255, 255, 0.4);
  -webkit-tap-highlight-color: transparent;
}

.nav-badge {
  position: absolute;
  top: -4px;
  right: -6px;
  background: #dc2626;
  color: white;
  font-size: 9px;
  font-weight: 700;
  min-width: 16px;
  height: 16px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 3px;
  line-height: 1;
  box-shadow: 0 1px 3px rgba(220, 38, 38, 0.35);
}

/* ── Banner GPS ─────────────────────────────────────────── */
.gps-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 14px;
  background: #78350f;        /* amber oscuro */
  border-bottom: 1px solid #92400e;
  flex-shrink: 0;
  min-height: 44px;
}

.gps-banner--error {
  background: #7f1d1d;
  border-bottom-color: #991b1b;
}

.gps-banner--fuera {
  background: #92400e;
  border-bottom-color: #b45309;
}

.gps-banner__left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

/* Punto pulsante de alerta */
.gps-banner__pulse {
  position: relative;
  width: 10px;
  height: 10px;
  flex-shrink: 0;
}
.gps-banner__pulse::before,
.gps-banner__pulse::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 50%;
}
.gps-banner__pulse::before {
  background: #fcd34d;
}
.gps-banner--error .gps-banner__pulse::before {
  background: #fca5a5;
}
.gps-banner--fuera .gps-banner__pulse::before {
  background: #fdba74;
}
.gps-banner--fuera .gps-banner__pulse::after {
  background: rgba(253, 186, 116, 0.4);
}
.gps-banner__pulse::after {
  background: rgba(252, 211, 77, 0.4);
  animation: bannerPulse 1.5s ease-out infinite;
}
.gps-banner--error .gps-banner__pulse::after {
  background: rgba(252, 165, 165, 0.4);
}
@keyframes bannerPulse {
  0%   { transform: scale(1);   opacity: 0.8; }
  100% { transform: scale(2.4); opacity: 0;   }
}

.gps-banner__text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
  min-width: 0;
}

.gps-banner__title {
  font-size: 14px;
  font-weight: 700;
  color: #fef3c7;
  white-space: nowrap;
}
.gps-banner--error .gps-banner__title {
  color: #fecaca;
}
.gps-banner--fuera .gps-banner__title {
  color: #fed7aa;
}

.gps-banner__sub {
  font-size: 12px;
  color: #fef3c7;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  opacity: 0.85;
}
.gps-banner--error .gps-banner__sub {
  color: #fecaca;
  opacity: 0.85;
}
.gps-banner--fuera .gps-banner__sub {
  color: #fed7aa;
  opacity: 0.85;
}

.gps-banner__btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  padding: 7px 18px;
  border-radius: 20px;
  border: 1.5px solid rgba(255,255,255,0.35);
  background: rgba(255,255,255,0.15);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  flex-shrink: 0;
  min-width: 100px;
  -webkit-tap-highlight-color: transparent;
  transition: background 0.15s;
}
.gps-banner__btn:active:not(:disabled) {
  background: rgba(255,255,255,0.25);
}
.gps-banner__btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.gps-banner__spin {
  width: 12px;
  height: 12px;
  border: 2px solid rgba(255,255,255,0.35);
  border-top-color: #fff;
  border-radius: 50%;
  animation: bannerSpin 0.7s linear infinite;
}
@keyframes bannerSpin { to { transform: rotate(360deg); } }

/* Transición entrada/salida del banner */
.banner-enter-active,
.banner-leave-active {
  transition: max-height 0.3s ease, opacity 0.3s ease;
  overflow: hidden;
}
.banner-enter-from,
.banner-leave-to {
  max-height: 0;
  opacity: 0;
}
.banner-enter-to,
.banner-leave-from {
  max-height: 60px;
  opacity: 1;
}

/* ── Contenido principal ────────────────────────────────── */
.app-main {
  flex: 1;
  overflow-y: auto;
  padding: 14px;
  padding-bottom: 76px;     /* espacio para bottom nav */
  -webkit-overflow-scrolling: touch;
}

/* ── Bottom Nav ─────────────────────────────────────────── */
.app-bottom-nav {
  position: fixed;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 100%;
  max-width: 480px;
  display: flex;
  background: white;
  border-top: 1px solid #e8e8e8;
  padding-top: 8px;
  padding-bottom: max(10px, env(safe-area-inset-bottom)); /* iOS notch */
  box-shadow: 0 -4px 16px rgba(0, 0, 0, 0.07);
  z-index: 1000;
}
.app-nav-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  text-decoration: none;
  color: #aaa;
  padding: 2px 0;
  transition: color 0.2s;
  -webkit-tap-highlight-color: transparent;
}
.app-nav-item--active {
  color: #2563eb;
}
.app-nav-item--disabled {
  opacity: 0.35;
  cursor: not-allowed;
}
.app-nav-icon {
  width: 22px;
  height: 22px;
  stroke-width: 1.8;
}
.app-nav-label {
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.2px;
}

/* ── Banner Ubicación en pausa (día libre) ──────────────── */
.pausa-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  background: #1e293b;
  border-bottom: 1px solid #334155;
  flex-shrink: 0;
  min-height: 40px;
}
.pausa-banner__icon {
  font-size: 16px;
  flex-shrink: 0;
  opacity: 0.8;
}
.pausa-banner__title {
  font-size: 14px;
  font-weight: 700;
  color: #f1f5f9;
  white-space: nowrap;
}
.pausa-banner__sub {
  font-size: 12px;
  color: #cbd5e1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ── Banner Push ────────────────────────────────────────── */
.push-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 14px;
  background: #1e3a5f;
  border-bottom: 1px solid #1a3254;
  flex-shrink: 0;
  min-height: 44px;
}
.push-banner--blocked {
  background: #7f1d1d;
  border-bottom-color: #991b1b;
}
.push-banner__pulse {
  position: relative;
  width: 10px;
  height: 10px;
  flex-shrink: 0;
}
.push-banner__pulse::before,
.push-banner__pulse::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 50%;
}
.push-banner__pulse::before { background: #93c5fd; }
.push-banner--blocked .push-banner__pulse::before { background: #fca5a5; }
.push-banner__pulse::after {
  background: rgba(147, 197, 253, 0.4);
  animation: bannerPulse 1.5s ease-out infinite;
}
.push-banner--blocked .push-banner__pulse::after {
  background: rgba(252, 165, 165, 0.4);
}
.push-banner__title {
  font-size: 14px;
  font-weight: 700;
  color: #dbeafe;
  white-space: nowrap;
}
.push-banner--blocked .push-banner__title { color: #fecaca; }
.push-banner__sub {
  font-size: 12px;
  color: #bfdbfe;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.push-banner--blocked .push-banner__sub { color: #fecaca; }
.push-banner__hint {
  font-size: 12px;
  font-weight: 600;
  color: #fecaca;
  text-align: right;
  flex-shrink: 0;
  max-width: 120px;
  line-height: 1.3;
}

/* ── Home tab: visualmente distinto ──────────── */
.app-nav-item--home {
  position: relative;
}
.app-nav-item--home .app-nav-icon {
  width: 24px;
  height: 24px;
  stroke-width: 2;
}
.app-nav-item--home .app-nav-label {
  font-weight: 700;
}
.app-nav-item--home::before {
  content: '';
  position: absolute;
  top: -1px;
  left: 50%;
  transform: translateX(-50%);
  width: 32px;
  height: 3px;
  border-radius: 0 0 3px 3px;
  background: transparent;
  transition: background 0.2s;
}
.app-nav-item--home.app-nav-item--active::before {
  background: #2563eb;
}

/* ── Banner GPS: variante crítica (denegado + en turno) ────── */
.gps-banner--critico {
  background: #7f1d1d;
  border-bottom-color: #991b1b;
  animation: bannerFlash 2s ease-in-out infinite;
}
.gps-banner--critico .gps-banner__title { color: #fecaca; }
.gps-banner--critico .gps-banner__sub   { color: rgba(254, 202, 202, 0.8); }

@keyframes bannerFlash {
  0%, 100% { opacity: 1;    }
  50%       { opacity: 0.85; }
}

/* ── Banner offline ────────────────────────────────────── */
.offline-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: #92400e;
  border-bottom: 2px solid #b45309;
  transition: background 0.3s;
}
.offline-banner--syncing {
  background: #1e40af;
  border-bottom-color: #2563eb;
}
.offline-banner__icon { font-size: 16px; flex-shrink: 0; }
.offline-banner__title {
  font-size: 13px;
  font-weight: 700;
  color: #fef3c7;
}
.offline-banner__sub {
  font-size: 12px;
  color: #fde68a;
}

/* ── Modal GPS denegado ─────────────────────────────────────── */
.gps-modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.65);
  z-index: 2000;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding-bottom: max(16px, env(safe-area-inset-bottom));
}

.gps-modal {
  background: #1a1a2e;
  border-radius: 20px 20px 12px 12px;
  width: 100%;
  max-width: 480px;
  padding: 28px 24px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  text-align: center;
  border-top: 4px solid #dc2626;
}

.gps-modal__icon {
  font-size: 44px;
  line-height: 1;
  animation: iconPulse 1.6s ease-in-out infinite;
}
@keyframes iconPulse {
  0%, 100% { transform: scale(1);    }
  50%       { transform: scale(1.1); }
}

.gps-modal__title {
  font-size: 18px;
  font-weight: 800;
  color: #fecaca;
  margin: 0;
  letter-spacing: -0.3px;
}

.gps-modal__body {
  font-size: 14px;
  color: #cbd5e1;
  line-height: 1.5;
  margin: 0;
}
.gps-modal__body strong { color: #f8fafc; }

.gps-modal__warning {
  background: rgba(239, 68, 68, 0.15);
  border: 1px solid rgba(239, 68, 68, 0.35);
  border-radius: 10px;
  padding: 10px 14px;
  font-size: 13px;
  color: #fca5a5;
  line-height: 1.45;
  display: flex;
  gap: 8px;
  align-items: flex-start;
  text-align: left;
}
.gps-modal__warning-icon { font-size: 16px; flex-shrink: 0; }
.gps-modal__warning strong { color: #fecaca; }

.gps-modal__btn-primary {
  width: 100%;
  padding: 14px;
  border-radius: 12px;
  border: none;
  background: #dc2626;
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  -webkit-tap-highlight-color: transparent;
  transition: background 0.15s;
}
.gps-modal__btn-primary:active:not(:disabled) { background: #b91c1c; }
.gps-modal__btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }

.gps-modal__btn-secondary {
  background: transparent;
  border: none;
  color: rgba(148, 163, 184, 0.7);
  font-size: 12px;
  cursor: pointer;
  padding: 4px 8px;
  -webkit-tap-highlight-color: transparent;
}

/* ── Aviso de privacidad ───────────────────────────────────
   Superficie clara y acentos azules, igual que el Inicio: es un texto para leer
   con calma, no una alarma. Por eso NO reutiliza el modal oscuro de GPS. */
.aviso-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.55);
  z-index: 2000;
  display: flex;
  align-items: center;          /* centrado vertical, no pegado abajo */
  justify-content: center;
  padding: 24px 20px;           /* aire contra los bordes del dispositivo */
}

.aviso-card {
  background: #fff;
  border-radius: 18px;
  width: 100%;
  max-width: 400px;             /* más angosto que el modal de alertas */
  max-height: 84vh;
  overflow-y: auto;
  padding: 26px 22px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  border-top: 4px solid #2563eb;
  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.28);
}

.aviso-card__icon { font-size: 40px; line-height: 1; }

.aviso-card__title {
  font-size: 20px;
  font-weight: 800;
  color: #1e3a8a;
  margin: 0;
  text-align: center;
  line-height: 1.25;
  text-wrap: balance;
}

.aviso__lista {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
  margin: 4px 0 8px;
  text-align: left;
}
.aviso__item { display: flex; flex-direction: column; gap: 3px; }
.aviso__k {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #2563eb;
}
/* Justificado, como pediste, con guionado para que no queden ríos de espacio. */
.aviso__v {
  font-size: 15px;
  line-height: 1.55;
  color: #334155;
  text-align: justify;
  hyphens: auto;
}
.aviso__v strong { color: #0f2547; font-weight: 700; }

.aviso-card__btn {
  width: 100%;
  padding: 14px;
  border: none;
  border-radius: 12px;
  background: #2563eb;
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
.aviso-card__btn:active { background: #1d4ed8; }

.aviso-card__nota {
  margin: 2px 0 0;
  font-size: 12.5px;
  line-height: 1.4;
  text-align: center;
  color: #64748b;
}

/* Nota al pie del modal bloqueante (no hay botón de descarte). */
.gps-modal__nota {
  margin: 8px 0 0;
  font-size: 11px;
  line-height: 1.4;
  text-align: center;
  color: rgba(148, 163, 184, 0.7);
}

/* Transición del modal */
.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}
.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;
  transform: translateY(30px);
}
</style>