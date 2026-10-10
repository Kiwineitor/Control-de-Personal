/**
 * usePushNotifications — gestión de Web Push para SISU.
 *
 * Flujo:
 *   1. Verifica soporte del navegador (SW + PushManager)
 *   2. Obtiene la clave pública VAPID del backend
 *   3. Solicita permiso al usuario si no lo tiene
 *   4. Suscribe al PushManager con la clave VAPID
 *   5. Envía la suscripción al backend para guardarla
 *
 * Usar en app.vue (portal móvil) y en el layout principal.
 */
import { readonly, ref } from 'vue';

import { getPushPublicKey, subscribePush, unsubscribePush } from '#/api/sisu/push';

// ── Estado singleton ──────────────────────────────────────────────────────
const suscrito     = ref(false);
const cargando     = ref(false);
const errorPush    = ref<null | string>(null);
const permisoRef   = ref<NotificationPermission>(
  typeof Notification === 'undefined' ? 'default' : Notification.permission,
);

/** Clave en localStorage con el token FCM del dispositivo actual (para darlo de baja en logout). */
const FCM_TOKEN_KEY = 'sisu:fcm-token';

// ── Helpers ───────────────────────────────────────────────────────────────

/** Convierte base64url a Uint8Array para PushManager.subscribe(). */
function base64UrlToUint8Array(base64url: string): Uint8Array {
  const padding = '='.repeat((4 - (base64url.length % 4)) % 4);
  const base64  = (base64url + padding).replaceAll('-', '+').replaceAll('_', '/');
  const raw     = window.atob(base64);
  return Uint8Array.from(raw, (c) => c.charCodeAt(0));
}

/** Convierte ArrayBuffer a base64url (para enviar keys al backend). */
function arrayBufferToBase64Url(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let   bin   = '';
  bytes.forEach((b) => (bin += String.fromCharCode(b)));
  return window.btoa(bin)
    .replaceAll('+', '-')
    .replaceAll('/', '_')
    .replace(/=+$/, '');
}

// ── API pública ───────────────────────────────────────────────────────────

export function usePushNotifications() {

  /** ¿El navegador soporta Web Push? */
  const soportado = typeof window !== 'undefined'
    && 'serviceWorker' in navigator
    && 'PushManager' in window
    && 'Notification' in window;

  /** Estado reactivo del permiso de notificaciones del navegador. */
  const permiso = readonly(permisoRef);

  /**
   * Suscribe al usuario a push notifications.
   * Si el permiso fue denegado, retorna silenciosamente.
   */
  async function suscribir(): Promise<void> {
    if (!soportado || cargando.value || suscrito.value) return;

    cargando.value = true;
    errorPush.value = null;

    try {
      // 1. Verificar si el backend tiene push habilitado
      const vapidInfo = await getPushPublicKey();
      if (!vapidInfo.enabled || !vapidInfo.publicKey) {
        console.info('[Push] Backend push deshabilitado — omitiendo suscripción.');
        return;
      }

      // 2. Pedir permiso de notificación al usuario
      permisoRef.value = Notification.permission;
      if (Notification.permission === 'denied') {
        console.info('[Push] Permiso denegado por el usuario.');
        return;
      }

      if (Notification.permission !== 'granted') {
        const result = await Notification.requestPermission();
        permisoRef.value = result;
        if (result !== 'granted') {
          console.info('[Push] Usuario denegó el permiso de notificaciones.');
          return;
        }
      }

      // 3. Esperar que el Service Worker esté listo
      const registration = await navigator.serviceWorker.ready;

      // 4. Verificar si ya existe una suscripción activa
      const existente = await registration.pushManager.getSubscription();
      if (existente) {
        // Ya estaba suscrito — solo confirmamos al backend
        await enviarSubscripcionAlBackend(existente);
        suscrito.value = true;
        return;
      }

      // 5. Crear nueva suscripción VAPID
      const subscription = await registration.pushManager.subscribe({
        userVisibleOnly:      true,
        applicationServerKey: base64UrlToUint8Array(vapidInfo.publicKey).buffer as ArrayBuffer,
      });

      // 6. Enviar suscripción al backend
      await enviarSubscripcionAlBackend(subscription);
      suscrito.value = true;
      console.info('[Push] Suscripción registrada exitosamente.');

    } catch (error: any) {
      errorPush.value = error?.message ?? 'Error al suscribirse a push';
      console.warn('[Push] Error suscribiendo:', error);
    } finally {
      cargando.value = false;
    }
  }

  /**
   * Cancela la suscripción push del usuario.
   * Llamar al hacer logout.
   */
  async function desuscribir(): Promise<void> {
    if (!soportado) return;
    try {
      const registration = await navigator.serviceWorker.getRegistration();
      if (!registration) return;
      const sub = await registration.pushManager.getSubscription();
      if (!sub) return;

      // Notificar al backend primero (antes de invalidar el endpoint)
      try {
        await unsubscribePush(sub.endpoint);
      } catch {
        // No bloquear el logout si falla el backend
      }

      await sub.unsubscribe();
      suscrito.value = false;
      console.info('[Push] Suscripción cancelada.');
    } catch (error) {
      console.warn('[Push] Error cancelando suscripción:', error);
    }
  }

  return {
    soportado,
    suscrito:   readonly(suscrito),
    cargando:   readonly(cargando),
    errorPush:  readonly(errorPush),
    permiso,
    suscribir,
    desuscribir,
  };
}

/**
 * Guarda el token FCM del dispositivo actual para poder darlo de baja en el
 * logout. FCM es el único canal que entrega con la app cerrada, así que sin
 * borrarlo el dispositivo seguiría recibiendo push tras cerrar sesión.
 */
export function recordarTokenFcm(token: string): void {
  try {
    if (token) localStorage.setItem(FCM_TOKEN_KEY, token);
  } catch {
    /* localStorage no disponible — no bloquear */
  }
}

/** Último token FCM ya confirmado por el backend en esta sesión. */
let ultimoTokenEnviado: null | string = null;
/** Cola de envíos: garantiza que nunca haya dos POST /subscribe en vuelo a la vez. */
let colaRegistro: Promise<void> = Promise.resolve();

/**
 * Registra el token FCM del dispositivo en el backend, una sola vez por token
 * y por sesión.
 *
 * `PushNotifications.register()` se dispara desde varios puntos del ciclo de vida
 * (arranque, concesión del permiso, vuelta a primer plano) y cada llamada reemite
 * el evento `registration` con el MISMO token. Sin esta guarda salían varios POST
 * concurrentes a /push/subscribe y el backend chocaba contra el índice único
 * `idx_push_endpoint`. El upsert del backend ya es atómico; esto además evita el
 * tráfico redundante.
 *
 * Los envíos se serializan en una cola en vez de descartarse: si el token ROTÓ
 * mientras había uno en vuelo, el nuevo se manda igual (encolado detrás). La
 * comprobación de duplicado va dentro de la sección serializada, así que N eventos
 * con el mismo token producen exactamente un POST.
 *
 * La memoria se limpia en `desuscribirDispositivo()` (logout), de modo que el
 * siguiente usuario en el MISMO dispositivo sí reenvía el token y el backend puede
 * reasignar la suscripción a su cuenta.
 */
export function registrarTokenFcm(token: string): Promise<void> {
  if (!token) return Promise.resolve();

  colaRegistro = colaRegistro
    // Un fallo anterior no debe romper la cadena para los siguientes envíos.
    .catch(() => {})
    .then(async () => {
      if (token === ultimoTokenEnviado) {
        console.info('[FCM] token ya registrado en esta sesión — se omite el reenvío.');
        return;
      }
      await subscribePush({
        endpoint:  token,
        p256dh:    '',
        auth:      '',
        userAgent: navigator.userAgent,
        platform:  'FCM',
      });
      // Solo tras el OK del backend: si falló, el próximo register() reintenta.
      recordarTokenFcm(token);
      ultimoTokenEnviado = token;
      console.info('[FCM] token registrado en backend OK');
    });

  return colaRegistro;
}

/**
 * Da de baja TODAS las suscripciones push de ESTE dispositivo (FCM nativo +
 * Web Push del navegador) y limpia el estado local. Se llama en el logout para
 * garantizar, por privacidad, que el dispositivo deje de recibir notificaciones.
 *
 * En el logout normal debe invocarse ANTES de borrar el token de sesión: el
 * backend exige Bearer para dar de baja la suscripción.
 *
 * @param notificarBackend `false` cuando la sesión ya está muerta (token expirado
 *   o invalidado). El DELETE devolvería 401, y ese 401 realimenta al interceptor
 *   → `doReAuthenticate` → `logout` → otro DELETE: el bucle infinito de peticiones
 *   que tumbaba la app al abrirla con la sesión vencida. La limpieza LOCAL sí se
 *   hace siempre, así que el dispositivo queda desuscrito igual.
 */
export async function desuscribirDispositivo(
  notificarBackend: boolean = true,
): Promise<void> {
  // 1) Token FCM nativo — el canal que entrega con la app cerrada.
  try {
    const fcmToken = localStorage.getItem(FCM_TOKEN_KEY);
    if (fcmToken) {
      if (notificarBackend) {
        try {
          await unsubscribePush(fcmToken);
        } catch {
          /* best-effort: no bloquear el logout si el backend falla */
        }
      }
      localStorage.removeItem(FCM_TOKEN_KEY);
    }
  } catch {
    /* noop */
  }

  // 2) Suscripción Web Push del navegador (si existe).
  try {
    if (typeof navigator !== 'undefined' && 'serviceWorker' in navigator) {
      const registration = await navigator.serviceWorker.getRegistration();
      const sub = await registration?.pushManager.getSubscription();
      if (sub) {
        if (notificarBackend) {
          try {
            await unsubscribePush(sub.endpoint);
          } catch {
            /* best-effort */
          }
        }
        await sub.unsubscribe();
      }
    }
  } catch {
    /* noop */
  }

  suscrito.value = false;
  // El siguiente usuario en este dispositivo debe reenviar el token para que el
  // backend reasigne la suscripción a su cuenta.
  ultimoTokenEnviado = null;
}

// ── Helper privado ────────────────────────────────────────────────────────

async function enviarSubscripcionAlBackend(sub: PushSubscription): Promise<void> {
  const p256dh = sub.getKey('p256dh');
  const auth   = sub.getKey('auth');
  if (!p256dh || !auth) throw new Error('Suscripción sin keys de cifrado.');

  await subscribePush({
    endpoint:  sub.endpoint,
    p256dh:    arrayBufferToBase64Url(p256dh),
    auth:      arrayBufferToBase64Url(auth),
    userAgent: navigator.userAgent,
  });
}
