/**
 * useEstadoRed — estado de conectividad del dispositivo, en tiempo real.
 *
 * Por qué no basta `navigator.onLine`: en el WebView de Android su evento `offline`
 * puede tardar minutos en dispararse (o no dispararse) al apagar el WiFi, porque
 * el WebView no observa los cambios de red del sistema. Ése era el síntoma de
 * "la app tarda hasta 5 minutos en darse cuenta de que no hay conexión".
 *
 * En nativo se usa `@capacitor/network`, que escucha el ConnectivityManager de
 * Android y notifica en el instante del cambio. En navegador se cae a los eventos
 * `online`/`offline` de siempre, que ahí sí funcionan.
 *
 * Alcance: informa si el dispositivo tiene RED, no si el backend es alcanzable.
 * Un WiFi sin salida a internet se reporta como conectado; esos fallos los
 * absorben las colas offline (GPS y operaciones) y el reintento al reconectar.
 */
import { readonly, ref } from 'vue';

import { Capacitor } from '@capacitor/core';

/** Tipos que expone el plugin: 'wifi' | 'cellular' | 'none' | 'unknown'. */
export type TipoConexion = 'cellular' | 'none' | 'unknown' | 'wifi';

// ── Estado singleton ──────────────────────────────────────────────────────
// Optimista al arrancar: se corrige en cuanto iniciarMonitoreoRed() lee el
// estado real. Partir de `false` haría parpadear el banner en cada arranque.
const enLinea = ref(true);
const tipoConexion = ref<TipoConexion>('unknown');

let iniciado = false;
let quitarListenerNativo: (() => void) | null = null;
let _alConectarWeb: (() => void) | null = null;
let _alDesconectarWeb: (() => void) | null = null;

/**
 * Empieza a observar la red y deja `enLinea` sincronizado con el estado real.
 * Idempotente. Devuelve el estado inicial ya resuelto.
 */
export async function iniciarMonitoreoRed(): Promise<boolean> {
  if (iniciado) return enLinea.value;
  iniciado = true;

  if (Capacitor.isNativePlatform()) {
    try {
      const { Network } = await import('@capacitor/network');

      const aplicar = (estado: { connected: boolean; connectionType: string }) => {
        const antes = enLinea.value;
        enLinea.value = estado.connected;
        tipoConexion.value = estado.connectionType as TipoConexion;
        if (antes !== estado.connected) {
          console.info(
            `[red] Cambio de conectividad: ${estado.connected ? 'CONECTADO' : 'SIN CONEXIÓN'}`,
            `(${estado.connectionType})`,
          );
        }
      };

      aplicar(await Network.getStatus());

      const handle = await Network.addListener('networkStatusChange', aplicar);
      quitarListenerNativo = () => handle.remove();

      return enLinea.value;
    } catch (error) {
      // Plugin ausente (APK sin `cap sync`) → seguir con el fallback del WebView
      // en vez de quedarse sin ninguna detección.
      console.warn('[red] @capacitor/network no disponible, se usa navigator.onLine:', error);
    }
  }

  // ── Navegador (o nativo sin el plugin) ──
  enLinea.value = navigator.onLine;
  _alDesconectarWeb = () => {
    enLinea.value = false;
    tipoConexion.value = 'none';
    console.info('[red] Cambio de conectividad: SIN CONEXIÓN (navigator.onLine)');
  };
  _alConectarWeb = () => {
    enLinea.value = true;
    tipoConexion.value = 'unknown';
    console.info('[red] Cambio de conectividad: CONECTADO (navigator.onLine)');
  };
  window.addEventListener('offline', _alDesconectarWeb);
  window.addEventListener('online', _alConectarWeb);

  return enLinea.value;
}

/** Deja de observar la red y libera los listeners. */
export function detenerMonitoreoRed(): void {
  if (quitarListenerNativo) {
    try {
      quitarListenerNativo();
    } catch {
      /* noop */
    }
    quitarListenerNativo = null;
  }
  if (_alDesconectarWeb) {
    window.removeEventListener('offline', _alDesconectarWeb);
    _alDesconectarWeb = null;
  }
  if (_alConectarWeb) {
    window.removeEventListener('online', _alConectarWeb);
    _alConectarWeb = null;
  }
  iniciado = false;
}

export function useEstadoRed() {
  return {
    /** true si el dispositivo tiene red. Reactivo. */
    enLinea: readonly(enLinea),
    /** 'wifi' | 'cellular' | 'none' | 'unknown'. */
    tipoConexion: readonly(tipoConexion),
  };
}
