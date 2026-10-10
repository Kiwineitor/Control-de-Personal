/// <reference lib="webworker" />
/**
 * Service Worker — SISU Hospital UAP
 *
 * Estrategia: injectManifest (VitePWA inyecta __WB_MANIFEST).
 * Combina:
 *   - Precaching de assets del build (Workbox)
 *   - Soporte de notificaciones Push (Web Push API)
 */

import { clientsClaim }             from 'workbox-core';
import { precacheAndRoute, cleanupOutdatedCaches } from 'workbox-precaching';
import { registerRoute }            from 'workbox-routing';
import { CacheableResponsePlugin }  from 'workbox-cacheable-response';
import { NetworkFirst, NetworkOnly } from 'workbox-strategies';

declare const self: ServiceWorkerGlobalScope;

// Tomar control inmediatamente en nuevas versiones (clave para Capacitor:
// al instalar un APK nuevo, el SW nuevo toma control al instante y no
// sirve assets cacheados viejos del APK anterior).
self.skipWaiting();
clientsClaim();

// ── Precache ─────────────────────────────────────────────────────────────
cleanupOutdatedCaches();
// eslint-disable-next-line @typescript-eslint/ban-ts-comment
// @ts-ignore __WB_MANIFEST es inyectado por VitePWA en build time
precacheAndRoute((self as any).__WB_MANIFEST ?? []);

// API GET: NetworkFirst — sirve datos cacheados cuando offline.
registerRoute(
  ({ url, request }) =>
    url.pathname.startsWith('/api/') && request.method === 'GET',
  new NetworkFirst({
    cacheName: 'sisu-api',
    plugins: [
      new CacheableResponsePlugin({ statuses: [200] }),
    ],
  }),
);

// API mutaciones (POST/PUT/DELETE): nunca cachear
registerRoute(
  ({ url, request }) =>
    url.pathname.startsWith('/api/') && request.method !== 'GET',
  new NetworkOnly(),
);

// WebSocket: nunca interceptar
registerRoute(
  ({ url }) => url.pathname.startsWith('/ws'),
  new NetworkOnly(),
);

// JS / CSS del app: NetworkFirst — intenta red primero, cachea para
// la próxima vez. Esto mejora drásticamente la carga en conexiones lentas.
registerRoute(
  ({ request }) =>
    request.destination === 'script' || request.destination === 'style',
  new NetworkFirst({ cacheName: 'sisu-assets' }),
);

// ── Activate: limpiar runtime caches de versiones anteriores ─────────────
// cleanupOutdatedCaches() ya limpia los precache de workbox. Este handler
// se encarga de los cachés runtime propios ('sisu-*') para que al instalar
// un APK nuevo no queden assets obsoletos.
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(
        keys
          .filter((k) => k.startsWith('sisu-'))
          .map((k) => caches.delete(k)),
      ),
    ),
  );
});

// ── Push Notifications ───────────────────────────────────────────────────

interface PushPayload {
  titulo:   string;
  mensaje:  string;
  tipo:     string;
  url:      string;
  urgente:  boolean;
}

/** Mapeo tipo → emoji de icono en el cuerpo del mensaje */
const TIPO_ICONO: Record<string, string> = {
  JORNADA_ASIGNADA:    '📋',
  ACTIVIDAD_ASIGNADA:  '✅',
  PERMISO_APROBADO:    '✅',
  PERMISO_RECHAZADO:   '❌',
  PERMISO_OBSERVADO:   '👁',
  AUSENCIA_DETECTADA:  '⚠️',
  NUEVA_SOLICITUD:     '📩',
};

self.addEventListener('push', (event) => {
  if (!event.data) return;

  let payload: PushPayload;
  try {
    payload = event.data.json() as PushPayload;
  } catch {
    payload = {
      titulo:  'SISU Hospital',
      mensaje: event.data.text(),
      tipo:    'notificacion',
      url:     '/app/home',
      urgente: false,
    };
  }

  const icono   = TIPO_ICONO[payload.tipo] ?? '🔔';
  const titulo  = payload.titulo  || 'SISU Hospital';
  const mensaje = payload.mensaje || '';

  const opciones = {
    body:               `${icono}  ${mensaje}`,
    icon:               '/logo192.png',
    badge:              '/logo192.png',
    data:               { url: payload.url ?? '/app/home', tipo: payload.tipo },
    tag:                payload.tipo,               // agrupa notifs del mismo tipo
    renotify:           true,                       // vibrar aunque exista el mismo tag
    requireInteraction: payload.urgente ?? false,   // urgente = no se cierra sola
    vibrate:            payload.urgente ? [200, 100, 200, 100, 200] : [100, 50, 100],
    silent:             false,
  } satisfies NotificationOptions & { renotify?: boolean; vibrate?: number[] };

  event.waitUntil(
    self.registration.showNotification(titulo, opciones),
  );
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();

  const data    = event.notification.data as { url?: string } | null;
  const destUrl = data?.url ?? '/app/home';

  event.waitUntil(
    self.clients
      .matchAll({ type: 'window', includeUncontrolled: true })
      .then((clientList) => {
        // Enfocar pestaña ya abierta con la misma URL
        const existing = clientList.find((c) => {
          try {
            const clientUrl = new URL(c.url);
            return clientUrl.pathname === destUrl || c.url.includes(destUrl);
          } catch {
            return false;
          }
        });

        if (existing && 'focus' in existing) return existing.focus();

        // Enfocar cualquier pestaña del origen y navegar
        const any = clientList.find((c) => c.url.startsWith(self.location.origin));
        if (any && 'focus' in any) {
          any.focus();
          if (typeof (any as any).navigate === 'function') {
            return (any as any).navigate(destUrl);
          }
          return;
        }

        // Ninguna pestaña abierta → abrir nueva
        return self.clients.openWindow(destUrl);
      }),
  );
});
