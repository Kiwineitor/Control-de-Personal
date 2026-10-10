/**
 * useLogShipper — captura la consola del frontend (console.log/info/warn/error +
 * errores no atrapados) en un buffer y la envía periódicamente al backend, para
 * verla en la "Consola del sistema" del admin. Útil para diagnosticar la app del
 * funcionario en producción sin tener el dispositivo a mano.
 *
 * Best-effort: si el envío falla (sin sesión / sin red) descarta el lote y sigue.
 * Se auto-protege contra recursión (no captura mientras envía).
 */
import { useAccessStore } from '@vben/stores';

import { enviarLogsCliente, type ClienteLogEntry } from '#/api/sisu/logs';

const MAX_PENDIENTES = 300;
const FLUSH_MS = 8000;

let iniciado = false;
let enviando = false;
let pendientes: ClienteLogEntry[] = [];

function serializar(args: any[]): string {
  return args
    .map((a) => {
      if (typeof a === 'string') return a;
      if (a instanceof Error) return `${a.name}: ${a.message}`;
      try { return JSON.stringify(a); } catch { return String(a); }
    })
    .join(' ');
}

function capturar(level: string, args: any[]) {
  if (enviando) return; // evitar recursión: no capturar lo que produce el propio envío
  try {
    pendientes.push({ level, logger: 'console', message: serializar(args).slice(0, 4000) });
    if (pendientes.length > MAX_PENDIENTES) pendientes.shift();
  } catch { /* nunca romper por logging */ }
}

/** ¿Hay sesión iniciada? El endpoint de logs exige Bearer. */
function haySesion(): boolean {
  try {
    return !!useAccessStore().accessToken;
  } catch {
    // Pinia aún no inicializado (arranque muy temprano) → no enviar todavía.
    return false;
  }
}

async function flush() {
  if (enviando || pendientes.length === 0) return;

  // Sin sesión NO se envía: el intervalo sigue vivo tras el logout, así que en la
  // pantalla de login este POST daba 401 cada 8 s y el interceptor mostraba un
  // "token no encontrado" sin que el usuario hubiera hecho nada. Los logs quedan
  // en el buffer (tope MAX_PENDIENTES) y se despachan al volver a iniciar sesión.
  if (!haySesion()) return;

  const lote = pendientes;
  pendientes = [];
  enviando = true;
  try {
    await enviarLogsCliente(lote);
  } catch {
    // Sin sesión / sin red: descartar (best-effort) para no crecer sin límite.
  } finally {
    enviando = false;
  }
}

/** Arranca la captura + envío. Idempotente. Llamar una vez al montar la app. */
export function iniciarLogShipper() {
  if (iniciado || typeof window === 'undefined') return;
  iniciado = true;

  const orig = {
    log:   console.log.bind(console),
    info:  console.info.bind(console),
    warn:  console.warn.bind(console),
    error: console.error.bind(console),
  };

  console.log   = (...a: any[]) => { orig.log(...a);   capturar('DEBUG', a); };
  console.info  = (...a: any[]) => { orig.info(...a);  capturar('INFO',  a); };
  console.warn  = (...a: any[]) => { orig.warn(...a);  capturar('WARN',  a); };
  console.error = (...a: any[]) => { orig.error(...a); capturar('ERROR', a); };

  window.addEventListener('error', (ev) => {
    capturar('ERROR', [`window.error: ${ev.message}`, `${ev.filename}:${ev.lineno}:${ev.colno}`]);
  });
  window.addEventListener('unhandledrejection', (ev: PromiseRejectionEvent) => {
    capturar('ERROR', [`unhandledrejection:`, ev.reason]);
  });

  setInterval(() => { void flush(); }, FLUSH_MS);
}
