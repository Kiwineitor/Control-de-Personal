const DB_NAME = 'sisu-offline';
const STORE_NAME = 'write-queue';
const DB_VERSION = 2;

interface OperacionPendienteBase {
  clientEventId: string;
  tipo: 'bitacora' | 'culminar' | 'subir-evidencia' | 'cancelar';
  endpoint: string;
  method: string;
  createdAt: number;
}

export interface OperacionBitacora extends OperacionPendienteBase {
  tipo: 'bitacora';
  body: { fechaHoraEvento: string; descripcion: string };
}

export interface OperacionCulminar extends OperacionPendienteBase {
  tipo: 'culminar';
  body: { observaciones?: string };
  idActividadJornada: number;
}

export interface OperacionEvidencia extends OperacionPendienteBase {
  tipo: 'subir-evidencia';
  idActividadJornada: number;
  fileName: string;
  fileType: string;
  fileData: ArrayBuffer;
}

export interface OperacionCancelar extends OperacionPendienteBase {
  tipo: 'cancelar';
  body: { estado: string; observaciones?: string };
  idActividadJornada: number;
}

export type OperacionPendiente =
  | OperacionBitacora
  | OperacionCulminar
  | OperacionEvidencia
  | OperacionCancelar;

type OperacionSinMeta = Omit<OperacionBitacora, 'clientEventId' | 'createdAt'>
  | Omit<OperacionCulminar, 'clientEventId' | 'createdAt'>
  | Omit<OperacionEvidencia, 'clientEventId' | 'createdAt'>
  | Omit<OperacionCancelar, 'clientEventId' | 'createdAt'>;

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

export async function encolarOperacion(op: OperacionSinMeta): Promise<string> {
  const clientEventId = generarId();
  try {
    const db = await openDb();
    const tx = db.transaction(STORE_NAME, 'readwrite');
    tx.objectStore(STORE_NAME).put({ ...op, clientEventId, createdAt: Date.now() } as OperacionPendiente);
    return new Promise((resolve, reject) => {
      tx.oncomplete = () => resolve(clientEventId);
      tx.onerror = () => reject(tx.error);
    });
  } catch (error) {
    console.warn('[OfflineQueue] Error guardando operación:', error);
    return clientEventId;
  }
}

async function obtenerPendientes(): Promise<OperacionPendiente[]> {
  try {
    const db = await openDb();
    const tx = db.transaction(STORE_NAME, 'readonly');
    const store = tx.objectStore(STORE_NAME);
    const request = store.getAll();
    return new Promise((resolve, reject) => {
      request.onsuccess = () => resolve((request.result ?? []) as OperacionPendiente[]);
      request.onerror = () => reject(request.error);
    });
  } catch {
    return [];
  }
}

async function eliminarEnviados(ids: string[]): Promise<void> {
  if (ids.length === 0) return;
  try {
    const db = await openDb();
    const tx = db.transaction(STORE_NAME, 'readwrite');
    const store = tx.objectStore(STORE_NAME);
    for (const id of ids) store.delete(id);
    return new Promise((resolve, reject) => {
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  } catch (error) {
    console.warn('[OfflineQueue] Error eliminando operaciones:', error);
  }
}

export async function contarPendientes(): Promise<number> {
  try {
    const ops = await obtenerPendientes();
    return ops.length;
  } catch {
    return 0;
  }
}

export async function vaciarColaOffline(
  ejecutar: (op: OperacionPendiente) => Promise<boolean>,
): Promise<{ ok: number; fail: number }> {
  const pendientes = await obtenerPendientes();
  if (pendientes.length === 0) return { ok: 0, fail: 0 };

  let ok = 0;
  let fail = 0;
  const idsEnviados: string[] = [];

  for (const op of pendientes) {
    try {
      const exito = await ejecutar(op);
      if (exito) {
        ok++;
        idsEnviados.push(op.clientEventId);
      } else {
        fail++;
      }
    } catch {
      fail++;
    }
  }

  if (idsEnviados.length > 0) {
    await eliminarEnviados(idsEnviados);
  }

  return { ok, fail };
}