// apps/web-naive/src/api/sisu/actividades.ts
import { requestClient } from '#/api/request';

export type TipoActividad    = 'CLINICA' | 'ADMINISTRATIVA' | 'ACADEMICA' | 'SUPERVISION' | 'EMERGENCIA' | 'OTRA';
export type EstadoCatalogo   = 'ACTIVO'  | 'INACTIVO' | 'ELIMINADO';
export type EstadoActividad  = 'PENDIENTE' | 'EN_PROGRESO' | 'COMPLETADA' | 'VALIDADA' | 'CANCELADA';

export interface ActividadDto {
  idActividad:   number;
  nombre:        string;
  descripcion:   string | null;
  tipoActividad: TipoActividad;
  estado:        EstadoCatalogo;
  activo:        boolean;
  vecesUsada:    number;
  fechaCreacion: string | null;
}

export interface ActividadRequest {
  nombre:         string;
  descripcion?:   string;
  tipoActividad?: TipoActividad;
  activo?:        boolean;
}

export interface ActividadJornadaDto {
  idActividadJornada:  number;
  idActividad:         number;
  nombreActividad:     string;
  tipoActividad:       TipoActividad;
  descripcionActividad: string | null;
  asignadoA:           'INTERNO' | 'TITULAR';
  idInternoJornada:    number | null;
  idTitularJornada:    number | null;
  idPersona:           number;
  nombrePersona:       string;
  idJornada:           number;
  nombreJornada:       string | null;
  fechaJornada:        string;
  estadoActividad:     EstadoActividad;
  horaInicio:          string | null;
  horaFin:             string | null;
  evidenciaUrl:        string | null;
  observaciones:       string | null;
  validada:            boolean;
  idValidadoPor:       number | null;
  nombreValidador:     string | null;
  fechaValidacion:     string | null;
  fechaCreacion:       string | null;
}

export interface ActividadJornadaRequest {
  idInternoJornada?:  number;
  idTitularJornada?:  number;
  idActividad:        number;
  horaInicio?:        string;
  horaFin?:           string;
  observaciones?:     string;
}

export interface CambiarEstadoActividadRequest {
  estado:          EstadoActividad;
  observaciones?:  string;
}

export interface ValidarActividadRequest {
  idTitular:       number;
  observaciones?:  string;
}

export interface ResumenActividadesDto {
  idJornada:             number;
  nombreJornada:         string | null;
  fechaJornada:          string;
  total:                 number;
  pendientes:            number;
  enProgreso:            number;
  completadas:           number;
  canceladas:            number;
  porcentajeCompletado:  number;
  porPersona:            ResumenPorPersona[];
}

export interface ResumenPorPersona {
  idPersona:      number;
  nombreCompleto: string;
  tipo:           'INTERNO' | 'TITULAR';
  total:          number;
  completadas:    number;
  pendientes:     number;
  enProgreso:     number;
}

export interface EstadisticasActividadesDto {
  totalRegistradas: number;
  totalCompletadas: number;
  totalPendientes:  number;
  totalEnProgreso:  number;
  totalCanceladas:  number;
  porTipo:          ConteoPorClave[];
  porEstado:        ConteoPorClave[];
  masUtilizadas:    ActividadPopular[];
}

export interface ConteoPorClave {
  clave:      string;
  cantidad:   number;
  porcentaje: number;
}

export interface ActividadPopular {
  idActividad: number;
  nombre:      string;
  tipo:        string;
  vecesUsada:  number;
}

function toVxePage<T>(raw: any) {
  const meta = (raw as any).page ?? raw;
  return { items: (raw.content ?? []) as T[], total: (meta.totalElements ?? 0) as number };
}

export interface ActividadParams {
  page:    number;
  size:    number;
  q?:      string;
  tipo?:   TipoActividad;
  estado?: EstadoCatalogo;
}

// ── Catálogo ──────────────────────────────────────────────────────────────
export async function getActividades(p: ActividadParams) {
  const raw = await requestClient.get<any>('/api/v1/actividades', { params: p });
  return toVxePage<ActividadDto>(raw);
}
export async function getCatalogoActivo() {
  return requestClient.get<ActividadDto[]>('/api/v1/actividades/catalogo');
}
export async function createActividad(data: ActividadRequest) {
  return requestClient.post<ActividadDto>('/api/v1/actividades', data);
}
export async function updateActividad(id: number, data: ActividadRequest) {
  return requestClient.put<ActividadDto>(`/api/v1/actividades/${id}`, data);
}
export async function desactivarActividad(id: number) {
  return requestClient.post<void>(`/api/v1/actividades/${id}/desactivar`, {});
}

// ── Asignaciones ──────────────────────────────────────────────────────────
export async function asignarActividad(data: ActividadJornadaRequest) {
  return requestClient.post<ActividadJornadaDto>('/api/v1/actividades/asignar', data);
}
export async function cambiarEstadoActividad(id: number, data: CambiarEstadoActividadRequest) {
  return requestClient.post<ActividadJornadaDto>(`/api/v1/actividades/${id}/estado`, data);
}
export async function validarActividad(id: number, data: ValidarActividadRequest) {
  return requestClient.post<ActividadJornadaDto>(`/api/v1/actividades/${id}/validar`, data);
}
export async function removerActividadJornada(id: number) {
  return requestClient.delete<void>(`/api/v1/actividades/${id}`);
}

// ── Consultas ─────────────────────────────────────────────────────────────
export async function getActividadesJornada(idJornada: number) {
  return requestClient.get<ActividadJornadaDto[]>(`/api/v1/actividades/por-jornada/${idJornada}`);
}
export async function getActividadesInternoJornada(id: number) {
  return requestClient.get<ActividadJornadaDto[]>(`/api/v1/actividades/por-interno-jornada/${id}`);
}
export async function getActividadesTitularJornada(id: number) {
  return requestClient.get<ActividadJornadaDto[]>(`/api/v1/actividades/por-titular-jornada/${id}`);
}
export async function getPendientesInterno(id: number) {
  return requestClient.get<ActividadJornadaDto[]>(`/api/v1/actividades/pendientes-interno/${id}`);
}
export async function getHistorialInterno(id: number) {
  return requestClient.get<ActividadJornadaDto[]>(`/api/v1/actividades/historial-interno/${id}`);
}
export async function getHistorialTitular(id: number) {
  return requestClient.get<ActividadJornadaDto[]>(`/api/v1/actividades/historial-titular/${id}`);
}

// ── Reportes ──────────────────────────────────────────────────────────────
export async function getResumenJornada(idJornada: number) {
  return requestClient.get<ResumenActividadesDto>(`/api/v1/actividades/resumen-jornada/${idJornada}`);
}
export async function getEstadisticas() {
  return requestClient.get<EstadisticasActividadesDto>('/api/v1/actividades/estadisticas');
}

// ── Tipos de Paso 8 (nuevas respuestas del backend) ───────────

/**
 * Respuesta del endpoint /api/v1/actividades/asignacion/**
 * Diferente a ActividadJornadaDto (Paso 4) — incluye campos v2
 */
export interface ActividadJornadaAppDto {
  idActividadJornada:   number;
  idActividad:          number;
  nombreActividad:      string;
  tipoActividad:        TipoActividad;
  idJornada:            number | null;
  nombreJornada:        string | null;
  // Asignación
  idInternoJornada:     number | null;
  nombreInterno:        string | null;
  idTitularJornada:     number | null;
  nombreTitular:        string | null;
  // Estado v2
  estadoActividad:      EstadoActividad;
  culminada:            boolean;
  fechaCulminacion:     string | null;
  observaciones:        string | null;
  // Evidencia
  evidenciaUrl:         string | null;
  evidenciaDownloadUrl: string | null;
  // Validación
  validadoPor:          string | null;
  fechaValidacion:      string | null;
  // Carry-over
  carryOverDeId:        number | null;
  esCarryOver:          boolean;
  // Tiempos
  horaInicio:           string | null;
  horaFin:              string | null;
  fechaCreacion:        string | null;
}

export interface SubirEvidenciaResponse {
  idActividadJornada: number;
  evidenciaUrl:       string;
  downloadUrl:        string;
  mensaje:            string;
}

export interface CulminarActividadRequest {
  observaciones?: string;
}

// ── Nuevas funciones Paso 8 ───────────────────────────────────

/** Listado paginado de asignaciones para una jornada (panel admin v2) */
export async function getActividadesAsignacion(
  idJornada: number,
  params?: { page?: number; size?: number; culminada?: boolean },
) {
  const raw = await requestClient.get<any>(
    `/api/v1/actividades/asignacion/jornada/${idJornada}`,
    { params },
  );
  return toVxePage<ActividadJornadaAppDto>(raw);
}

/** Subir o reemplazar evidencia de una ActividadJornada */
export async function subirEvidenciaActividad(
  idActividadJornada: number,
  file: File,
) {
  const fd = new FormData();
  fd.append('file', file);
  return requestClient.post<SubirEvidenciaResponse>(
    `/api/v1/actividades/asignacion/${idActividadJornada}/subir-evidencia`,
    fd,
  );
}

/** Marcar actividad como culminada */
export async function culminarActividad(
  idActividadJornada: number,
  data: CulminarActividadRequest,
) {
  return requestClient.post<ActividadJornadaAppDto>(
    `/api/v1/actividades/asignacion/${idActividadJornada}/culminar`,
    data,
  );
}