/**
 * usePlatformPermisos — solicita permisos de ubicación y notificaciones
 * tanto en Capacitor (nativo Android/iOS) como en navegador web.
 *
 * En Capacitor usa los plugins nativos; en web usa las APIs del navegador.
 */
import { ref } from 'vue';

import { Capacitor } from '@capacitor/core';

// ── Estado singleton ──────────────────────────────────────────────────────
const estadoUbicacion     = ref<'denied' | 'granted' | 'prompt' | 'unknown'>('unknown');
const estadoNotificacion  = ref<'denied' | 'granted' | 'prompt' | 'unknown'>('unknown');
const verificando         = ref(false);

export function usePlatformPermisos() {

  /** Consulta el estado actual de permisos sin pedirlos. */
  async function verificarEstado() {
    verificando.value = true;
    try {
      if (Capacitor.isNativePlatform()) {
        const { Geolocation } = await import('@capacitor/geolocation');
        const geoState = await Geolocation.checkPermissions();
        estadoUbicacion.value =
          geoState.location === 'granted' ? 'granted'
          : (geoState.location === 'denied' ? 'denied' : 'prompt');

        const { PushNotifications } = await import('@capacitor/push-notifications');
        const pushState = await PushNotifications.checkPermissions();
        estadoNotificacion.value =
          pushState.receive === 'granted' ? 'granted'
          : (pushState.receive === 'denied' ? 'denied' : 'prompt');

      } else {
        // Web — navigator.permissions no existe en Safari < 16 ni en algunos contextos
        try {
          if (navigator.permissions) {
            const geo = await navigator.permissions.query({ name: 'geolocation' });
            estadoUbicacion.value = geo.state as any;
          }
        } catch {
          // Ignorar: el estado se actualizará cuando el usuario responda el diálogo
        }
        if (typeof Notification !== 'undefined') {
          estadoNotificacion.value = Notification.permission as any;
        }
      }
    } finally {
      verificando.value = false;
    }
  }

  /** Pide permiso de ubicación y actualiza el estado. */
  async function pedirUbicacion(): Promise<boolean> {
    if (Capacitor.isNativePlatform()) {
      try {
        const { Geolocation } = await import('@capacitor/geolocation');
        const check = await Geolocation.checkPermissions();
        if (check.location === 'granted') {
          estadoUbicacion.value = 'granted';
          return true;
        }
        if (check.location === 'denied') {
          estadoUbicacion.value = 'denied';
          return false;
        }
        // 'prompt' — mostrar diálogo del sistema
        const perm = await Geolocation.requestPermissions();
        const concedido = perm.location === 'granted';
        estadoUbicacion.value = concedido ? 'granted' : 'denied';
        return concedido;
      } catch (error) {
        console.warn('[Permisos] Error al pedir ubicación:', error);
        estadoUbicacion.value = 'denied';
        return false;
      }
    }
    // Web: getCurrentPosition dispara el diálogo del navegador
    return new Promise((resolve) => {
      navigator.geolocation.getCurrentPosition(
        () => { estadoUbicacion.value = 'granted'; resolve(true); },
        () => { estadoUbicacion.value = 'denied';  resolve(false); },
        { timeout: 10_000 },
      );
    });
  }

  /**
   * Pide permiso de notificaciones (OS).
   * El registro FCM se maneja en app.vue (iniciarPushNativo) para centralizar
   * los listeners y evitar doble registro.
   */
  async function pedirNotificaciones(): Promise<boolean> {
    if (Capacitor.isNativePlatform()) {
      try {
        const { PushNotifications } = await import('@capacitor/push-notifications');

        // Verificar estado actual primero — evita mostrar diálogo si ya fue respondido
        const check = await PushNotifications.checkPermissions();
        if (check.receive === 'granted') {
          estadoNotificacion.value = 'granted';
          return true;
        }
        if (check.receive === 'denied') {
          estadoNotificacion.value = 'denied';
          return false;
        }

        // 'prompt' — mostrar diálogo del sistema
        const perm = await PushNotifications.requestPermissions();
        if (perm.receive !== 'granted') {
          estadoNotificacion.value = 'denied';
          return false;
        }
        estadoNotificacion.value = 'granted';
        return true;
      } catch (error) {
        console.warn('[Permisos] Error al pedir notificaciones:', error);
        return false;
      }
    }
    // Web
    if (typeof Notification === 'undefined') return false;
    const result = await Notification.requestPermission();
    estadoNotificacion.value = result as any;
    return result === 'granted';
  }

  /**
   * Pide todos los permisos necesarios en secuencia.
   * Devuelve true si ambos quedaron concedidos.
   */
  async function pedirTodos(): Promise<{ notificaciones: boolean; ubicacion: boolean; }> {
    const ubicacion      = await pedirUbicacion();
    const notificaciones = await pedirNotificaciones();
    return { ubicacion, notificaciones };
  }

  const necesitaPermisos = () =>
    estadoUbicacion.value !== 'granted' || estadoNotificacion.value !== 'granted';

  return {
    estadoUbicacion,
    estadoNotificacion,
    verificando,
    verificarEstado,
    pedirUbicacion,
    pedirNotificaciones,
    pedirTodos,
    necesitaPermisos,
  };
}
