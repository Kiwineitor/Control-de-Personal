import { requestClient } from '#/api/request';

// ── Types ──────────────────────────────────────────────────────────────
export interface SolicitudPermisoResponse {
  idSolicitudPermiso: number;
  idTitular: number;
  nombreTitular: string;
  apellidoTitular: string;
  idTitularJornada?: number;
  idJornada?: number;
  nombreJornada?: string;
  lugarJornada?: string;
  tipoPermiso: string;
  tipoPermisoLabel: string;
  motivoSolicitud: string;
  fechaPermiso?: string;
  horaInicioSolicitada?: string;
  horaFinSolicitada?: string;
  estadoSolicitud: string;
  estadoSolicitudLabel: string;
  fechaHoraSolicitud: string;
  revisadoPor?: string;
  fechaHoraRevision?: string;
  observacionRevision?: string;
  idDelegacion?: number;
  estadoDelegacion?: string;
  estadoDelegacionLabel?: string;
  internoAsignado?: string;
  fechaCreacion: string;
  justificanteUrl?:         string;
  justificanteDownloadUrl?: string;
}

export interface DelegacionResponse {
  idDelegacion: number;
  idTitularJornada: number;
  idTitular: number;
  nombreTitular: string;
  idJornada: number;
  nombreJornada: string;
  lugarNombre?: string;
  idInternoResponsable: number;
  nombreInterno: string;
  horaInicioPlanificada?: string;
  horaFinPlanificada?: string;
  horaInicioReal?: string;
  horaFinReal?: string;
  duracionPlanificadaMinutos?: number;
  motivo: string;
  estado: string;
  estadoLabel: string;
  aprobadoPor?: string;
  fechaAprobacion?: string;
  observacionAprobacion?: string;
  idSolicitudPermiso?: number;
  tipoPermiso?: string;
  tipoPermisoLabel?: string;
  fechaCreacion: string;
}

// ── Solicitudes ────────────────────────────────────────────────────────
export async function getSolicitudesApi(params?: Record<string, any>) {
  return requestClient.get('/api/v1/permisos', { params });
}
export async function crearSolicitudApi(data: Record<string, any>) {
  return requestClient.post('/api/v1/permisos', data);
}
export async function aprobarSolicitudApi(id: number, data: Record<string, any>) {
  return requestClient.post(`/api/v1/permisos/${id}/aprobar`, data);
}
export async function rechazarSolicitudApi(id: number, data: { observacion?: string }) {
  return requestClient.post(`/api/v1/permisos/${id}/rechazar`, data);
}
export async function observarSolicitudApi(id: number, data: { observacion: string }) {
  return requestClient.post(`/api/v1/permisos/${id}/observar`, data);
}
export async function cancelarSolicitudApi(id: number) {
  return requestClient.post(`/api/v1/permisos/${id}/cancelar`, {});
}
export async function getEstadisticasApi() {
  return requestClient.get('/api/v1/permisos/estadisticas');
}

// ── Delegaciones ───────────────────────────────────────────────────────
export async function getDelegacionesApi(params?: Record<string, any>) {
  return requestClient.get('/api/v1/delegaciones', { params });
}
export async function crearDelegacionApi(data: Record<string, any>) {
  return requestClient.post('/api/v1/delegaciones', data);
}
export async function aprobarDelegacionApi(id: number, data?: Record<string, any>) {
  return requestClient.post(`/api/v1/delegaciones/${id}/aprobar`, data ?? {});
}
export async function rechazarDelegacionApi(id: number, data: { observacion: string }) {
  return requestClient.post(`/api/v1/delegaciones/${id}/rechazar`, data);
}
export async function iniciarDelegacionApi(id: number) {
  return requestClient.post(`/api/v1/delegaciones/${id}/iniciar`, {});
}
export async function completarDelegacionApi(id: number) {
  return requestClient.post(`/api/v1/delegaciones/${id}/completar`, {});
}
export async function cancelarDelegacionApi(id: number, data?: Record<string, any>) {
  return requestClient.post(`/api/v1/delegaciones/${id}/cancelar`, data ?? {});
}
export async function getDelegacionesActivasApi() {
  return requestClient.get('/api/v1/delegaciones/activas');
}

export async function subirJustificanteApi(idSolicitud: number, file: File) {
  const fd = new FormData();
  fd.append('file', file);
  return requestClient.post<SolicitudPermisoResponse>(
    `/api/v1/permisos/${idSolicitud}/subir-justificante`,
    fd,
  );
}