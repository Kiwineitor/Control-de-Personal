import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import process from 'node:process';

import { defineConfig } from '@vben/vite-config';

/**
 * Destino del proxy de desarrollo.
 *
 * Vite NO vuelca los ficheros .env en `process.env`, así que leer sólo
 * `process.env.VITE_PROXY_TARGET` dejaba muerta la variable escrita en
 * `.env.development`: el proxy caía siempre al fallback. Aquí se leen los .env a
 * mano, con la misma detección de `--mode` que usa `internal/vite-config`.
 *
 * Se hace sin importar nada de `vite` a propósito: este fichero queda fuera del
 * `tsconfig.json` de la app, y con resolución node10 el editor no encuentra los
 * tipos de `vite` y marca errores falsos.
 *
 * Prioridad: shell > .env.<mode>.local > .env.<mode> > .env.local > .env > default.
 */
function resolverProxyTarget(): string {
  if (process.env.VITE_PROXY_TARGET) return process.env.VITE_PROXY_TARGET;

  const script = process.env.npm_lifecycle_script ?? '';
  const mode = /--mode ([\d_a-z]+)/.exec(script)?.[1] ?? 'production';

  const ficheros = [`.env.${mode}.local`, `.env.${mode}`, '.env.local', '.env'];
  for (const fichero of ficheros) {
    const ruta = join(process.cwd(), fichero);
    if (!existsSync(ruta)) continue;
    // Ignora comentarios y comillas; sólo interesa esta clave.
    const linea = /^[^\S\n]*VITE_PROXY_TARGET[^\S\n]*=[^\S\n]*(.*)$/m.exec(
      readFileSync(ruta, 'utf8'),
    );
    const valor = linea?.[1]?.trim().replaceAll(/^["']|["']$/g, '');
    if (valor) return valor;
  }

  // Default 127.0.0.1: el backend corre en la MISMA máquina que el dev server,
  // así evitamos el EACCES del firewall al cruzar por la IP LAN.
  return 'http://127.0.0.1:7050';
}

export default defineConfig(async () => {
  const proxyTarget = resolverProxyTarget();

  return {
    // Service Worker (injectManifest) para cachear assets en producción y cargar
    // rápido en conexiones lentas. El SW usa skipWaiting() + clientsClaim() +
    // cleanupOutdatedCaches() para que cada APK nuevo tome control inmediato sin
    // servir assets obsoletos de versiones anteriores. Solo se registra en build
    // de producción (bootstrap.ts chequea import.meta.env.PROD).
    application: {
      pwaOptions: {
        strategies: 'injectManifest',
        srcDir: 'src',
        filename: 'sw.ts',
        registerType: 'autoUpdate',
        manifest: {
          display: 'standalone',
          start_url: '/',
        },
      },
    },
    vite: {
      // Shim necesario para sockjs-client (CJS legacy usa global de Node.js)
      define: {
        global: 'globalThis',
      },

      // ── Proxy de desarrollo ──────────────────────────────────
      // Solo aplica en `pnpm dev`; producción sirve estáticos y NO usa este proxy.
      // Si VITE_GLOB_API_URL tiene valor, las llamadas /api NO pasan por aquí.
      // Para pegarle al backend de producción:
      //   VITE_PROXY_TARGET=http://virtual.uap.edu.bo:7050
      server: {
        proxy: {
          '/api': {
            changeOrigin: true,
            target      : proxyTarget,
            ws          : true,
          },
          '/ws': {
            changeOrigin: true,
            target      : proxyTarget,
            ws          : true,
          },
        },
      },
    },
  };
});
