import { ref } from 'vue';

import { useAppConfig } from '@vben/hooks';
import { preferences } from '@vben/preferences';
import { useAccessStore } from '@vben/stores';

import { Capacitor, registerPlugin } from '@capacitor/core';

import { enviarUbicacion as enviarUbicacionHttp } from '#/api/sisu/monitoreo';
import { guardarFixOffline, vaciarColaOffline } from '#/composables/offlineGpsQueue';
import { useWebSocket } from '#/composables/useWebSocket';

// URL absoluta del backend (igual que la baseURL de api/request.ts). El servicio
// nativo la necesita para postear la ubicacion por su cuenta en segundo plano.
const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);

export interface Coordenadas {
  lat: number;
  lon: number;
  precision?: number;
}

// ── Plugin nativo de geolocalización en segundo plano ────────────────────────
// @capacitor-community/background-geolocation: servicio en primer plano +
// notificación persistente, entrega fixes incluso con la app minimizada/cerrada.
interface BgLocation {
  latitude: number;
  longitude: number;
  accuracy?: number;
  // Adjuntados por el parche nativo: respuesta del backend al POST que hizo el
  // propio servicio (estado de zona / intervalo) y si el envio fue OK.
  serverResponse?: string;
  serverOk?: boolean;
  // Indica si el proveedor GPS (no solo red) esta activo en el dispositivo.
  gpsActivo?: boolean;
  // Nombre del proveedor que generó este fix: "gps" o "network".
  proveedor?: string;
}
interface BgError { code?: string; message?: string }
interface UploadConfig {
  url?: string;
  authToken?: string;
  locale?: string;
  intervalSeconds?: number;
}
interface BackgroundGeolocationPlugin {
  addWatcher(
    options: UploadConfig & {
      backgroundMessage?: string;
      backgroundTitle?: string;
      distanceFilter?: number;
      requestPermissions?: boolean;
      stale?: boolean;
    },
    callback: (location?: BgLocation, error?: BgError) => void,
  ): Promise<string>;
  removeWatcher(options: { id: string }): Promise<void>;
  openSettings(): Promise<void>;
  // Refresca url/token del servicio nativo sin reiniciar el watch (token rotado).
  updateUploadConfig(options: UploadConfig): Promise<void>;
  // Apagado TOTAL (logout): detiene todos los watchers, baja el servicio y olvida
  // el token, para que tras cerrar sesión NO se siga posteando ubicación.
  stopAll(): Promise<void>;
}
const BackgroundGeolocation =
  registerPlugin<BackgroundGeolocationPlugin>('BackgroundGeolocation');

// ── Estado singleton — compartido entre todos los componentes ────────────────
const coordenadas       = ref<Coordenadas | null>(null);
const errorGps          = ref<null | string>(null);
const permisoConcedido  = ref<boolean | null>(null); // null=desconocido
const watchActivo       = ref(false);
const intervaloEnvioSeg = ref(30);
// true cuando llevamos demasiado tiempo sin lograr enviar una posición al
// backend (GPS apagado, sin internet, modo avión…). Lo usa la UI para mostrar
// "Sin señal" en vez de un engañoso "Activo". Lo actualizan marcarSenal() y el
// heartbeat de programarEnvio().
const senalPerdida      = ref(false);
// Indica si el GPS (GPS_PROVIDER) está activo en el dispositivo Android.
// `false` cuando el usuario deshabilitó el GPS y solo hay ubicación por red.
// Lo actualiza reflejarRespuestaNativa() desde el plugin nativo.
const gpsActivo         = ref(true);
// `false` cuando los SERVICIOS DE UBICACIÓN del sistema están apagados (el
// interruptor de Ubicación del teléfono), que es distinto de que la app no tenga
// permiso. Lo mantiene la vigilancia de vigilarServicioUbicacion().
const servicioUbicacionActivo = ref(true);

let watchHandle:       null | number | string = null;
let envioTimer:        null | ReturnType<typeof setTimeout> = null;
// Vigilancia de los servicios de ubicación. 10 s: lo bastante rápido para que el
// aviso se sienta inmediato, y barato porque la sonda usa la posición cacheada.
const INTERVALO_VIGILANCIA_MS = 10_000;
let vigilanciaTimer:   null | ReturnType<typeof setInterval> = null;
let pendienteCallback: ((c: Coordenadas) => void) | null = null;
// Garantiza un único envío inmediato en el primer fix; los siguientes los hace
// el throttle por tiempo. Evita el doble-envío que creaba sesiones/eventos duplicados.
let primerEnvioHecho = false;
// Timestamp del último envío al backend. procesarFix() envía con throttle por
// tiempo (cada intervaloEnvioSeg) — funciona en segundo plano donde setTimeout se frena.
let ultimoEnvioMs = 0;
// Timestamp del último envío CONFIRMADO OK (lo marca app.vue con marcarSenal).
// Si pasa demasiado tiempo sin un OK → senalPerdida = true.
let ultimoEnvioOkTs = 0;
// Timestamp del último callback exitoso del plugin nativo. Si el plugin deja
// de reportar (sistema lo mató), el heartbeat bufferiza a IndexedDB como respaldo;
// mientras el plugin esté vivo NO se bufferiza para evitar duplicados.
let ultimoCallbackPluginMs = 0;
// En web usamos sondeo con getCurrentPosition (watchPosition no entrega fixes
// fiables en escritorio sin GPS). Esta función refresca coordenadas.value.
let refrescarPosicionWeb: (() => Promise<void>) | null = null;

// guards: evitan solicitudes/inicios nativos simultáneos

let _permisoPendiente = false;

let _watchPendiente = false;

/**
 * Punto ÚNICO de envío. Guarda la posición y la envía al backend con throttle
 * por tiempo (cada intervaloEnvioSeg). El primer fix se envía de inmediato; los
 * siguientes respetan el intervalo. Funciona en web y nativo (incl. background).
 */
function procesarFix(lat: number, lon: number, precision?: number) {
  coordenadas.value = { lat, lon, precision };
  errorGps.value    = null;
  const ahora       = Date.now();
  const intervaloMs = intervaloEnvioSeg.value * 1000;
  // 0.9 de tolerancia: no perder un beat por jitter del proveedor de GPS.
  const debeEnviarPorTiempo =
    !primerEnvioHecho || (ahora - ultimoEnvioMs) >= intervaloMs * 0.9;
  if (debeEnviarPorTiempo && pendienteCallback) {
    primerEnvioHecho = true;
    ultimoEnvioMs    = ahora;
    pendienteCallback(coordenadas.value);
  }
}

/**
 * app.vue llama esto tras intentar mandar la ubicación: true si el backend la
 * recibió, false si falló (sin internet / modo avión). Controla senalPerdida.
 */
function marcarSenal(ok: boolean) {
  if (ok) {
    ultimoEnvioOkTs    = Date.now();
    senalPerdida.value = false;
  } else {
    senalPerdida.value = true;
  }
}

/**
 * Punto ÚNICO y canónico de envío al backend, usado por defecto cuando se inicia
 * el watch sin pasar callback. Centraliza la decisión de transporte:
 *
 *  - NATIVO  → HTTP (POST /monitoreo/ubicacion). Es el único canal que sobrevive
 *    a la app en segundo plano: el WebSocket/STOMP se cae cuando Android congela
 *    el WebView, pero el fetch disparado desde el callback del plugin de
 *    background-geolocation sí completa. Actualiza gpsStatus con la respuesta
 *    (estado de zona, intervalo adaptativo) y marca la señal como OK/perdida.
 *  - WEB     → WebSocket (el escritorio solo opera en primer plano; el WS ya anda
 *    y el backend responde por /user/queue/gps-status).
 *
 * ANTES cada vista pasaba su propio callback (home mandaba SOLO por STOMP), lo que
 * en nativo rompía el background. Con un único sender el comportamiento es
 * idéntico en toda la app y no hay carrera entre callbacks del singleton.
 */
/**
 * Lee señales de contexto disponibles en el navegador (Fase 2): batería y tipo de
 * conexión. Best-effort: si la API no existe (típico en WebView), el campo queda
 * sin valor y el backend lo trata como "sin dato". El origen real en background es
 * el servicio nativo, que adjunta estas señales en su propio POST.
 */
async function leerSenalesWeb(): Promise<{ bateria?: number; conectividad?: string }> {
  const out: { bateria?: number; conectividad?: string } = {};
  try {
    const conn = (navigator as any).connection;
    if (navigator.onLine === false)     out.conectividad = 'NINGUNA';
    else if (conn?.type === 'wifi')     out.conectividad = 'WIFI';
    else if (conn?.type === 'cellular') out.conectividad = 'CELULAR';
    else if (conn?.effectiveType)       out.conectividad = 'CELULAR';
  } catch { /* noop */ }
  try {
    const anyNav = navigator as any;
    if (typeof anyNav.getBattery === 'function') {
      const b = await anyNav.getBattery();
      if (b && typeof b.level === 'number') out.bateria = Math.round(b.level * 100);
    }
  } catch { /* noop */ }
  return out;
}

async function enviarUbicacionAlBackend(c: Coordenadas) {
  const ws = useWebSocket();
  const senales = await leerSenalesWeb();
  if (Capacitor.isNativePlatform()) {
    try {
      const resp = await enviarUbicacionHttp(c.lat, c.lon, c.precision, senales);
      if (resp) ws.gpsStatus.value = resp;
      marcarSenal(true);
      void vaciarColaOffline();
    } catch (error) {
      console.warn('[GPS] Error enviando ubicación por HTTP:', error);
      guardarFixOffline({
        latitud: c.lat,
        longitud: c.lon,
        precision: c.precision,
        timestampDispositivoMs: Date.now(),
        ...senales,
      });
      marcarSenal(false);
    }
  } else {
    ws.enviarGps(c.lat, c.lon, c.precision, senales);
    marcarSenal(true);
    void vaciarColaOffline();
  }
}

/**
 * Datos que el servicio NATIVO necesita para postear la ubicación por su cuenta
 * en segundo plano (app cerrada / pantalla bloqueada): URL absoluta del backend,
 * JWT del usuario, locale e intervalo. Sin esto el POST nativo no se autentica.
 */
function obtenerConfigSubida(): UploadConfig {
  let authToken: string | undefined;
  try {
    authToken = useAccessStore().accessToken ?? undefined;
  } catch {
    authToken = undefined;
  }
  return {
    url: apiURL,
    authToken,
    locale: preferences.app.locale,
    intervalSeconds: intervaloEnvioSeg.value,
  };
}

/**
 * En NATIVO el POST lo hace el servicio (Java), que adjunta al fix la respuesta
 * del backend. Aquí solo REFLEJAMOS esa respuesta en la UI (estado de zona en
 * gpsStatus + señal OK/perdida). No se postea desde JS: lo haría por duplicado y
 * además el JS no corre con la app congelada (que es justo el caso a cubrir).
 */
function reflejarRespuestaNativa(loc: BgLocation) {
  // El plugin se reportó → está vivo. Resetear contador de silencio.
  ultimoCallbackPluginMs = Date.now();
  // Actualizar estado del proveedor GPS (GPS_PROVIDER activo o no).
  if (loc.gpsActivo === false) {
    gpsActivo.value = false;
    senalPerdida.value = true;
  } else {
    gpsActivo.value = true;
  }
  if (loc.serverResponse) {
    try {
      useWebSocket().gpsStatus.value = JSON.parse(loc.serverResponse);
      if (loc.gpsActivo !== false) marcarSenal(true);
    } catch (error) {
      console.warn('[GPS] No se pudo parsear serverResponse nativo:', error);
    }
  } else if (loc.serverOk === false) {
    // El plugin nativo no pudo enviar → bufferizar en IndexedDB como respaldo
    guardarFixOffline({
      latitud: loc.latitude,
      longitud: loc.longitude,
      precision: loc.accuracy,
      timestampDispositivoMs: Date.now(),
    });
    marcarSenal(false);
  }
}

// Heartbeat: cada intervalo re-envía la ÚLTIMA posición conocida aunque el
// usuario esté quieto (con distanceFilter el watcher nativo no emite si no hay
// movimiento → sin esto, un usuario sentado parecería "sin señal"). En web
// re-sondea con getCurrentPosition. Además marca senalPerdida si llevamos
// >2.5 intervalos sin un envío OK.
function programarEnvio() {
  if (envioTimer) clearTimeout(envioTimer);
  envioTimer = setTimeout(async () => {
    // ── Fallback universal: intentar getCurrentPosition en cada heartbeat ────
    // En Android (XOS, MIUI, etc.) el plugin nativo o el setTimeout pueden
    // morir cuando el sistema congela el WebView. getCurrentPosition es una
    // solicitud one-shot independiente que Reactiva el GPS aunque el servicio
    // de larga duración haya sido asesinado.
    const fallbackPosition = (): Promise<void> =>
      new Promise((resolve) => {
        if (!navigator.geolocation) { resolve(); return; }
        navigator.geolocation.getCurrentPosition(
          (pos) => { procesarFix(pos.coords.latitude, pos.coords.longitude, pos.coords.accuracy); resolve(); },
          () => { resolve(); },
          { enableHighAccuracy: true, timeout: 15_000, maximumAge: 0 },
        );
      });

    // Heartbeat por tipo de plataforma: primero el mecanismo principal, luego
    // el fallback por si el principal no entregó datos frescos.
    const antesMs = coordenadas.value ? coordenadas.value.lat : undefined;
    if (refrescarPosicionWeb) {
      try { await refrescarPosicionWeb(); }
      catch (error) { console.warn('[GPS-DEBUG] refrescarPosicionWeb falló', error); }
    } else if (coordenadas.value && gpsActivo.value) {
      // En nativo, solo reenviar la ultima posicion si el GPS esta activo.
      // Si el GPS esta apagado, no enviar coordenadas stale (evita que el
      // servidor crea que seguimos en zona cuando en realidad no hay GPS).
      procesarFix(coordenadas.value.lat, coordenadas.value.lon, coordenadas.value.precision);
    }
    // Si el plugin nativo no reportó nada en todo el intervalo (ni un callback),
    // intentar el fallback one-shot para reactivar FusedLocationProvider.
    const pluginSilencioso = Capacitor.isNativePlatform()
      && Date.now() - ultimoCallbackPluginMs > intervaloEnvioSeg.value * 1000;
    if (pluginSilencioso) {
      await fallbackPosition();
    }

    // En modo nativo: bufferizar a IndexedDB solo si el plugin dejó de reportar.
    // Mientras el plugin esté vivo, sus callbacks ya se encargan del envío y
    // bufferizan automáticamente cuando fallan (serverOk===false). El heartbeat
    // NO debe bufferizar también o se crean registros duplicados que el servidor
    // reprocesa, generando transiciones de zona fantasma (flickering).
    if (Capacitor.isNativePlatform()
        && coordenadas.value
        && Date.now() - ultimoCallbackPluginMs > intervaloEnvioSeg.value * 1000 * 2) {
      guardarFixOffline({
        latitud: coordenadas.value.lat,
        longitud: coordenadas.value.lon,
        precision: coordenadas.value.precision,
        timestampDispositivoMs: Date.now(),
      });
    }

    // Intentar vaciar la cola offline en cada heartbeat
    void vaciarColaOffline();

    // ¿Demasiado tiempo sin un envío OK confirmado? → sin señal.
    if (ultimoEnvioOkTs > 0 && Date.now() - ultimoEnvioOkTs > intervaloEnvioSeg.value * 1000 * 1.5) {
      senalPerdida.value = true;
    }
    programarEnvio();
  }, intervaloEnvioSeg.value * 1000);
}

/**
 * Verifica/solicita permiso de ubicación.
 * En nativo: primero checkPermissions (sin diálogo); solo llama requestPermissions
 * si el estado es 'prompt'. Incluye guard contra solicitudes simultáneas.
 */
async function solicitarPermiso(): Promise<boolean> {
  if (!Capacitor.isNativePlatform()) {
    if (!navigator.geolocation) {
      errorGps.value = 'Este navegador no soporta geolocalización.';
      permisoConcedido.value = false;
      return false;
    }
    return new Promise((resolve) => {
      navigator.geolocation.getCurrentPosition(
        () => { permisoConcedido.value = true; errorGps.value = null; resolve(true); },
        (err) => {
          permisoConcedido.value = false;
          errorGps.value = err.code === 1
            ? 'Permiso denegado. Habilitá la ubicación en Configuración del navegador.'
            : `Error de ubicación: ${err.message}`;
          resolve(false);
        },
        { timeout: 10_000, maximumAge: 60_000 },
      );
    });
  }

  // Nativo Android/iOS
  if (_permisoPendiente) {
    return permisoConcedido.value === true;
  }

  _permisoPendiente = true;
  try {
    const { Geolocation } = await import('@capacitor/geolocation');

    const check = await Geolocation.checkPermissions();
    if (check.location === 'granted') {
      permisoConcedido.value = true;
      errorGps.value = null;
      return true;
    }
    if (check.location === 'denied') {
      permisoConcedido.value = false;
      errorGps.value = 'Permiso de ubicación denegado. Ve a Configuración → Permisos de la app.';
      return false;
    }

    // 'prompt' — mostrar diálogo del sistema
    const perm = await Geolocation.requestPermissions();
    permisoConcedido.value = perm.location === 'granted';
    errorGps.value = permisoConcedido.value ? null : 'Permiso de ubicación no concedido.';
    return permisoConcedido.value;
  } catch (error: any) {
    console.warn('[GPS] Error al solicitar permiso de ubicación:', error);
    permisoConcedido.value = false;
    return false;
  } finally {
    _permisoPendiente = false;
  }
}

export function useGeolocalizacion() {
  function actualizarIntervalo(segs: number) {
    intervaloEnvioSeg.value = segs;
    // Reprogramar el heartbeat para aplicar el nuevo intervalo (web y nativo).
    if (watchActivo.value) programarEnvio();
  }

  /**
   * Inicia el watch de posición GPS.
   * Si el watch ya está activo no lo reinicia.
   * El envío usa el sender canónico (HTTP en nativo, WS en web) salvo que se pase
   * un onEnvio explícito (compatibilidad; ya nadie lo hace → un solo camino).
   * Guard _watchPendiente evita que dos llamadas concurrentes inicien dos watchPosition.
   */
  async function iniciarWatch(onEnvio?: (c: Coordenadas) => void) {
    // [GPS-DEBUG] temporal
    console.info('[GPS-DEBUG] iniciarWatch() llamado', {
      conCallback: onEnvio !== undefined,
      watchHandleYaActivo: watchHandle !== null,
      pendiente: _watchPendiente,
    });
    // En NATIVO el POST lo hace el servicio nativo (background-geolocation), no el
    // JS: dejamos pendienteCallback en null para que procesarFix solo actualice
    // coordenadas (UI/mapa) sin postear por duplicado. En WEB el sender es el WS.
    pendienteCallback = Capacitor.isNativePlatform()
      ? null
      : (onEnvio ?? enviarUbicacionAlBackend);

    if (watchHandle !== null) return;
    if (_watchPendiente) return;
    _watchPendiente = true;
    primerEnvioHecho = false;   // watch fresco → permitir el primer envío inmediato
    ultimoEnvioMs    = 0;

    try {
      const concedido = await solicitarPermiso();
      console.info('[GPS-DEBUG] solicitarPermiso() →', concedido);
      if (!concedido) return;

      // El envío se centraliza en procesarFix() (módulo): primer fix inmediato y
      // luego throttle por tiempo. El heartbeat de programarEnvio() lo re-invoca
      // periódicamente con la última posición para cubrir usuarios estáticos.

      if (Capacitor.isNativePlatform()) {
        try {
          // BackgroundGeolocation: servicio en primer plano + notificación
          // persistente. Entrega fixes aunque la app esté minimizada o cerrada.
          watchHandle = await BackgroundGeolocation.addWatcher(
            {
              backgroundTitle:   'SICOPE — Servicio activo',
              backgroundMessage: 'No cierres la app durante tu turno (puedes minimizarla).',
              requestPermissions: true,
              stale: false,
              // Config para que el servicio nativo postee solo al backend.
              ...obtenerConfigSubida(),
              // distanceFilter=15: el plugin nativo usa setInterval(1000) + 
              // setSmallestDisplacement(). Con 0, el GPS pide ubicación cada 1s,
              // lo que activa el estrangulamiento de Android Doze y detiene las
              // actualizaciones al bloquear la pantalla. Con 15m, el sistema no
              // estrangula el servicio. El heartbeat (programarEnvio) re-envía
              // la última posición cada intervalo aunque el usuario esté quieto.
              distanceFilter: 15,
            },
            (location, error) => {
              if (error) {
                console.warn('[GPS-DEBUG] BackgroundGeolocation error', error.code, error.message);
                if (error.code === 'NOT_AUTHORIZED') {
                  permisoConcedido.value = false;
                  watchActivo.value      = false;
                  errorGps.value = 'Permiso de ubicación denegado. Actívalo en Ajustes del dispositivo.';
                }
                return;
              }
              if (location) {
                procesarFix(location.latitude, location.longitude, location.accuracy);
                // El POST ya lo hizo el servicio nativo; reflejamos su respuesta.
                reflejarRespuestaNativa(location);
              }
            },
          );
        } catch (error: any) {
          console.warn('[GPS] Error iniciando BackgroundGeolocation:', error);
          errorGps.value = 'No se pudo iniciar el seguimiento GPS.';
          return;
        }
      } else {
        // WEB (escritorio): watchPosition con highAccuracy no entrega fixes
        // fiables sin GPS físico. Usamos sondeo con getCurrentPosition, que sí
        // funciona vía geolocalización por red. El timer de programarEnvio()
        // invoca refrescarPosicionWeb() en cada ciclo.
        refrescarPosicionWeb = () =>
          new Promise<void>((resolve) => {
            navigator.geolocation.getCurrentPosition(
              (pos) => {
                procesarFix(pos.coords.latitude, pos.coords.longitude, pos.coords.accuracy);
                resolve();
              },
              (err) => {
                console.warn('[GPS-DEBUG] getCurrentPosition error', err.code, err.message);
                errorGps.value = err.message;
                if (err.code === 1) {
                  permisoConcedido.value = false;
                  watchActivo.value = false;
                }
                resolve();
              },
              // enableHighAccuracy: true → el navegador prioriza WiFi/GPS sobre la
              // geolocalización por IP (que erra kilómetros). Clave para que la zona
              // se calcule con una posición real y no un punto a km de distancia.
              { enableHighAccuracy: true, maximumAge: 0, timeout: 20_000 },
            );
          });

        // Sentinel para que la lógica de "watch ya activo" funcione igual.
        watchHandle = 'web-poll';
        // Disparar el primer fix SIN await: así la UI no se queda colgada hasta
        // 20s si la geolocalización por red tarda. procesarFix enviará al resolver.
        void refrescarPosicionWeb();
      }

      watchActivo.value  = true;
      senalPerdida.value = false;   // arranque optimista; el heartbeat la corrige
    } finally {
      _watchPendiente = false;
    }

    // Heartbeat en web (re-sondeo) y nativo (re-envío del último fix): garantiza
    // reportes periódicos aunque el usuario esté quieto y detecta la pérdida de señal.
    if (watchActivo.value) programarEnvio();
  }

  /**
   * Detiene el watch. Con `apagarServicio=true` (logout) usa stopAll(): apaga TODO
   * el servicio nativo (todos los watchers, foreground, token) — imprescindible
   * para que tras cerrar sesión no quede un watcher huérfano posteando ubicación.
   * Sin él (restart de foreground) basta removeWatcher del handle actual.
   */
  async function detenerWatch(apagarServicio = false) {
    if (envioTimer) { clearTimeout(envioTimer); envioTimer = null; }
    pendienteCallback     = null;
    primerEnvioHecho      = false;
    ultimoEnvioMs         = 0;
    ultimoEnvioOkTs       = 0;
    refrescarPosicionWeb  = null;
    watchActivo.value     = false;
    senalPerdida.value    = false;

    if (Capacitor.isNativePlatform()) {
      try {
        if (apagarServicio) {
          // Logout: apagado total e incondicional (aunque watchHandle sea null,
          // pudo haber watchers huérfanos de una carrera previa).
          await BackgroundGeolocation.stopAll();
        } else if (watchHandle !== null) {
          await BackgroundGeolocation.removeWatcher({ id: watchHandle as string });
        }
      } catch (error) {
        console.warn('[GPS] Error al detener BackgroundGeolocation:', error);
      }
    } else if (watchHandle !== null && watchHandle !== 'web-poll') {
      // 'web-poll' no es un handle real de watchPosition; solo sondeo por timer.
      navigator.geolocation.clearWatch(watchHandle as number);
    }
    watchHandle = null;
  }

  /**
   * Reset total para el logout: detiene el watcher nativo (apaga el servicio en
   * primer plano) y limpia toda la posición/estado para que el siguiente usuario
   * en el mismo dispositivo no herede coordenadas ni "GPS activo" del anterior.
   */
  async function reset() {
    await detenerWatch(true);   // logout → apagar el servicio nativo por completo
    detenerVigilanciaUbicacion();
    coordenadas.value      = null;
    errorGps.value         = null;
    permisoConcedido.value = null;
    intervaloEnvioSeg.value = 30;
    senalPerdida.value     = false;
    gpsActivo.value       = true;
  }

  /** Abre la página de ajustes de la app en Android (permisos, batería). */
  async function abrirConfiguracionApp() {
    if (Capacitor.isNativePlatform()) {
      try {
        await BackgroundGeolocation.openSettings();
      } catch (error) {
        console.warn('[GPS] Error al abrir configuración:', error);
      }
    }
  }

  /** Vacía la cola offline de GPS. app.vue lo llama al montar/recuperar conexión. */
  async function flushNow() {
    await vaciarColaOffline();
  }

  // ── Vigilancia de los servicios de ubicación del sistema ──────────────────

  /**
   * Comprueba si el teléfono tiene la ubicación ENCENDIDA.
   *
   * El plugin nativo solo informa "Location services disabled." al crear el
   * watcher; si el usuario apaga la ubicación con el watcher ya corriendo, el
   * plugin se queda mudo (deja de emitir fixes sin error). Ése era el motivo de
   * que el aviso tardara tanto en aparecer.
   *
   * La sonda usa `maximumAge` alto a propósito: con la ubicación encendida
   * responde con la posición cacheada, sin despertar el GPS ni gastar batería;
   * con la ubicación apagada falla enseguida con POSITION_UNAVAILABLE.
   */
  function comprobarServicioUbicacion(): Promise<boolean> {
    return new Promise((resolve) => {
      if (!navigator.geolocation) { resolve(true); return; }
      navigator.geolocation.getCurrentPosition(
        () => resolve(true),
        (err) => {
          // 1 = PERMISSION_DENIED  → es un problema de permiso, no del servicio;
          //     lo cubre estadoUbicacion, así que aquí no se marca nada.
          // 2 = POSITION_UNAVAILABLE → no hay proveedor: ubicación apagada.
          // 3 = TIMEOUT → no concluyente (puede ser interior o señal débil).
          resolve(err.code !== 2);
        },
        { enableHighAccuracy: false, maximumAge: 60_000, timeout: 8000 },
      );
    });
  }

  /**
   * Arranca la vigilancia periódica. Sólo debe correr DURANTE EL TURNO: fuera de
   * él la ubicación es asunto del usuario (principio de privacidad por turno).
   *
   * Exige dos fallos seguidos antes de dar la ubicación por apagada, para que un
   * POSITION_UNAVAILABLE puntual —típico en interiores— no dispare un modal
   * bloqueante en la cara del usuario.
   */
  function vigilarServicioUbicacion() {
    if (vigilanciaTimer) return;

    let fallosSeguidos = 0;
    const revisar = async () => {
      const activo = await comprobarServicioUbicacion();
      if (activo) {
        fallosSeguidos = 0;
        if (!servicioUbicacionActivo.value) {
          console.info('[GPS] Servicios de ubicación reactivados.');
          servicioUbicacionActivo.value = true;
        }
      } else {
        fallosSeguidos += 1;
        if (fallosSeguidos >= 2 && servicioUbicacionActivo.value) {
          console.warn('[GPS] Servicios de ubicación APAGADOS durante el turno.');
          servicioUbicacionActivo.value = false;
        }
      }
    };

    void revisar();
    vigilanciaTimer = setInterval(() => void revisar(), INTERVALO_VIGILANCIA_MS);
  }

  /** Detiene la vigilancia (fin de turno o logout) y limpia el estado. */
  function detenerVigilanciaUbicacion() {
    if (vigilanciaTimer) {
      clearInterval(vigilanciaTimer);
      vigilanciaTimer = null;
    }
    servicioUbicacionActivo.value = true;
  }

  /** Abre los ajustes de UBICACIÓN del sistema (distinto de los ajustes de la app). */
  async function abrirAjustesUbicacion() {
    if (!Capacitor.isNativePlatform()) return;
    try {
      // El plugin abre los ajustes de la app; desde ahí se llega a Ubicación. Es
      // lo más cercano que expone sin añadir otra dependencia nativa.
      await BackgroundGeolocation.openSettings();
    } catch (error) {
      console.warn('[GPS] No se pudieron abrir los ajustes de ubicación:', error);
    }
  }

  return {
    coordenadas,
    errorGps,
    permisoConcedido,
    watchActivo,
    intervaloEnvioSeg,
    senalPerdida,
    gpsActivo,
    servicioUbicacionActivo,
    iniciarWatch,
    detenerWatch,
    actualizarIntervalo,
    marcarSenal,
    reset,
    abrirConfiguracionApp,
    abrirAjustesUbicacion,
    vigilarServicioUbicacion,
    detenerVigilanciaUbicacion,
    flushNow,
  };
}
