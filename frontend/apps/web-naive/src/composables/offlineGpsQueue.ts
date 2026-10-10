import type { FixBufferizado, LoteUbicacionResponse } from '#/api/sisu/monitoreo';

import { enviarUbicacionesLote } from '#/api/sisu/monitoreo';

const DB_NAME = 'sisu-offline';
const STORE_NAME = 'gps-fixes';
const DB_VERSION = 1;

function generarId(): string {
  try {
    return crypto.randomUUID();
  } catch {
    return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
  }
}

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        db.createObjectStore(STORE_NAME, { keyPath: 'clientEventId' });
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

export async function guardarFixOffline(fix: Omit<FixBufferizado, 'clientEventId'>): Promise<string> {
  const clientEventId = generarId();
  try {
    const db = await openDb();
    const tx = db.transaction(STORE_NAME, 'readwrite');
    tx.objectStore(STORE_NAME).put({ ...fix, clientEventId, timestampDispositivoMs: fix.timestampDispositivoMs ?? Date.now() });
    return new Promise((resolve, reject) => {
      tx.oncomplete = () => resolve(clientEventId);
      tx.onerror = () => reject(tx.error);
    });
  } catch (error) {
    console.warn('[GPS-Offline] Error guardando fix:', error);
    return clientEventId;
  }
}

async function obtenerFixesPendientes(): Promise<FixBufferizado[]> {
  try {
    const db = await openDb();
    const tx = db.transaction(STORE_NAME, 'readonly');
    const store = tx.objectStore(STORE_NAME);
    const request = store.getAll();
    return new Promise((resolve, reject) => {
      request.onsuccess = () => resolve(request.result ?? []);
      request.onerror = () => reject(request.error);
    });
  } catch {
    return [];
  }
}

async function eliminarFixesEnviados(ids: string[]): Promise<void> {
  if (ids.length === 0) return;
  try {
    const db = await openDb();
    const tx = db.transaction(STORE_NAME, 'readwrite');
    const store = tx.objectStore(STORE_NAME);
    ids.forEach((id) => store.delete(id));
    return new Promise((resolve, reject) => {
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  } catch (error) {
    console.warn('[GPS-Offline] Error eliminando fixes:', error);
  }
}

export async function vaciarColaOffline(): Promise<void> {
  try {
    const fixes = await obtenerFixesPendientes();
    if (fixes.length === 0) return;
    let resp: LoteUbicacionResponse;
    try {
      resp = await enviarUbicacionesLote(fixes);
    } catch {
      return;
    }
    if (resp.procesados !== undefined) {
      await eliminarFixesEnviados(fixes.map((f) => f.clientEventId!));
      console.info(`[GPS-Offline] Enviados ${resp.procesados}/${fixes.length} fixes pendientes`);
    }
  } catch (error) {
    console.warn('[GPS-Offline] Error vaciando cola:', error);
  }
}
