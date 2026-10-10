import { requestClient } from '#/api/request';

export interface BitacoraEntrada {
  idBitacora: number;
  fechaHoraEvento: string;
  descripcion: string;
  evidenciaUrl: string | null;
  evidenciaDownloadUrl: string | null;
  nombreJornada: string | null;
  nombreLugar: string | null;
  nombrePersonal?: string | null;
  createdAt: string;
}

export interface CrearBitacoraPayload {
  fechaHoraEvento: string;
  descripcion: string;
}

/** Crear una entrada de bitácora */
export function crearBitacora(data: CrearBitacoraPayload) {
  return requestClient.post<BitacoraEntrada>('/api/v1/app/bitacora', data);
}

/** Subir evidencia a una entrada de bitácora (usar native fetch para multipart) */
export async function subirEvidenciaBitacora(
  idBitacora: number,
  file: File,
): Promise<BitacoraEntrada> {
  const { useAccessStore } = await import('@vben/stores');
  const accessStore = useAccessStore();
  const apiBase = (import.meta.env.VITE_GLOB_API_URL as string) ?? '';

  const formData = new FormData();
  formData.append('file', file);

  const resp = await fetch(
    `${apiBase}/api/v1/app/bitacora/${idBitacora}/evidencia`,
    {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${accessStore.accessToken ?? ''}`,
      },
      body: formData,
    },
  );

  const json = await resp.json();
  if (!resp.ok || json.code !== 0) {
    throw new Error(json.message ?? `Error al subir evidencia (${resp.status})`);
  }
  return json.data as BitacoraEntrada;
}

/** Obtener entradas recientes del usuario */
export function misEntradasBitacora(limit = 20) {
  return requestClient.get<BitacoraEntrada[]>('/api/v1/app/bitacora', {
    params: { limit },
  });
}

/** Obtener entradas de una jornada específica */
export function bitacoraPorJornada(idTitularJornada: number) {
  return requestClient.get<BitacoraEntrada[]>(
    `/api/v1/app/bitacora/jornada/${idTitularJornada}`,
  );
}

/** Obtener todas las entradas de bitácora de una jornada (admin) */
export function bitacoraJornadaAdmin(idJornada: number) {
  return requestClient.get<BitacoraEntrada[]>(
    `/api/v1/monitoreo/bitacora/jornada/${idJornada}`,
  );
}
