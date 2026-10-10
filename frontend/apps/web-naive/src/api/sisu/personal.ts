/**
 * API Layer — Módulo Personal SISU
 * apps/web-naive/src/api/sisu/personal.ts
 *
 * FIX aplicados:
 *  1. toggleTitular / toggleInterno: patch() → post()
 *     RequestClient no expone patch() tipado en Vben Admin.
 *     ⚠️  Backend: @PatchMapping("/{id}/toggle") → @PostMapping("/{id}/toggle")
 */
import { requestClient } from '#/api/request';

// ═══════════════════════════════════════════════════════════════════════════
//  TIPOS BASE
// ═══════════════════════════════════════════════════════════════════════════

// ─────────────────────────────────────────────────────────────────────────
// Persona (para selector en formularios de Titular e Interno)
// ─────────────────────────────────────────────────────────────────────────

export interface PersonaResumenDto {
  idPersona: number;
  nombre: string;
  apellido: string;
  nombreCompleto: string;
  ci: string;
  email: string;
  telefono: string;
}

export interface SpringPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

/** Formato que espera el VxeTable adapter (response: result='items', total='total') */
export interface VxePage<T> {
  items: T[];
  total: number;
}

// ─────────────────────────────────────────────────────────────────────────
// Titular
// ─────────────────────────────────────────────────────────────────────────

export interface TitularDto {
  idTitular: number;
  perId: number;
  contratoId: number;
  nombre: string;
  apellido: string;
  nombreCompleto: string;
  ci: string;
  email: string;
  telefono: string;
  cargo: string;
  especialidad: string;
  activo: boolean;
  totalInternosSupervisados: number;
  fechaCreacion: string;
}

export interface TitularRequest {
  idTitular?: number;
  // Persona: seleccionar existente O ingresar datos manuales (uno de los dos)
  idPersona?: number;
  // Londra
  perId: number;
  contratoId: number;
  // Persona (manual — se ignoran si idPersona está presente)
  nombre?: string;
  apellido?: string;
  ci?: string;
  email?: string;
  telefono?: string;
  // Titular
  cargo?: string;
  especialidad?: string;
  activo?: boolean;
  sincronizarDesdeLondra?: boolean;
}

export interface TitularFiltros {
  page?: number;
  size?: number;
  q?: string;
  cargo?: string;
  especialidad?: string;
  activo?: boolean;
}

// ─────────────────────────────────────────────────────────────────────────
// Interno
// ─────────────────────────────────────────────────────────────────────────

export interface InternoDto {
  idInterno: number;
  perId: number;
  contratoId?: number;
  nombre: string;
  apellido: string;
  nombreCompleto: string;
  ci: string;
  email: string;
  telefono: string;
  universidad: string;
  carrera: string;
  anoFormacion?: number;
  nroMatricula?: string;
  activo: boolean;
  estaEnRotacion?: boolean;
  fechaInicioRotacion?: string;
  fechaFinRotacion?: string;
}

export interface InternoRequest {
  idInterno?: number;
  // Persona: seleccionar existente O ingresar datos manuales (uno de los dos)
  idPersona?: number;
  // Persona (manual — se ignoran si idPersona está presente)
  nombre?: string;
  apellido?: string;
  ci?: string;
  email?: string;
  telefono?: string;
  // Académico
  universidad: string;
  carrera: string;
  anoFormacion?: number;
  nroMatricula?: string;
  activo?: boolean;
}

export interface InternoFiltros {
  page?: number;
  size?: number;
  q?: string;
  carrera?: string;
  universidad?: string;
  enRotacion?: boolean;
  activo?: boolean;
}

// ─────────────────────────────────────────────────────────────────────────
// Responsable
// ─────────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────────────────
// Responsable
// ─────────────────────────────────────────────────────────────────────────

export interface InternoAsignadoDto {
  idInterno: number;
  idResponsableInterno: number;
  nombreCompleto: string;
  carrera: string;
  universidad: string;
  estaEnRotacion: boolean;
  fechaAsignacion: string;
}

export interface ResponsableResumenDto {
  idTitular: number;
  nombreCompleto: string;
  cargo: string;
  especialidad: string;
  email: string;
  telefono: string;
  internos: InternoAsignadoDto[];
  totalInternos: number;
  fechaAsignacion: string;
}

export interface ResponsableDto {
  idResponsableInterno: number;
  activo: boolean;
  fechaAsignacion: string;
  fechaFinAsignacion?: string;
  observaciones?: string;
  creadoPor: string;
  fechaCreacion: string;
  idTitular: number;
  nombreCompletoTitular: string;
  cargoTitular: string;
  idInterno: number;
  nombreCompletoInterno: string;
  carreraInterno: string;
}

export interface AsignarRequest {
  idTitular: number;
  idInternos?: number[];
  asignarTodos?: boolean;
  fechaFinAsignacion?: string;
  observaciones?: string;
}

// ═══════════════════════════════════════════════════════════════════════════
//  UTILIDADES
// ═══════════════════════════════════════════════════════════════════════════

function toVxePage<T>(page: SpringPage<T>): VxePage<T> {
  return { items: page.content, total: page.totalElements };
}

function clean(obj: Record<string, unknown>): Record<string, unknown> {
  return Object.fromEntries(
    Object.entries(obj).filter(([, v]) => v !== undefined && v !== null && v !== ''),
  );
}

// ═══════════════════════════════════════════════════════════════════════════
//  API — TITULARES
// ═══════════════════════════════════════════════════════════════════════════

const T = '/api/v1/titulares';

export async function getTitulares(filtros: TitularFiltros = {}): Promise<VxePage<TitularDto>> {
  const { page = 0, size = 20, ...rest } = filtros;
  const result = await requestClient.get<SpringPage<TitularDto>>(T, {
    params: clean({ page, size, ...rest }),
  });
  return toVxePage(result);
}

export async function getTitularById(id: number): Promise<TitularDto> {
  return requestClient.get<TitularDto>(`${T}/${id}`);
}

export async function createTitular(data: TitularRequest): Promise<TitularDto> {
  return requestClient.post<TitularDto>(T, data);
}

export async function updateTitular(id: number, data: TitularRequest): Promise<TitularDto> {
  return requestClient.put<TitularDto>(`${T}/${id}`, data);
}

// ✅ FIX: patch → post
// ⚠️  BACKEND: cambiar en TitularApiController.java:
//     @PatchMapping("/{id}/toggle")  →  @PostMapping("/{id}/toggle")
export async function toggleTitular(id: number): Promise<TitularDto> {
  return requestClient.post<TitularDto>(`${T}/${id}/toggle`);
}

export async function syncTitularLondra(id: number): Promise<TitularDto> {
  return requestClient.post<TitularDto>(`${T}/${id}/sincronizar`);
}

export async function getTitularesActivos(): Promise<TitularDto[]> {
  return requestClient.get<TitularDto[]>(`${T}/activos`);
}

export async function getTitularesCargos(): Promise<string[]> {
  return requestClient.get<string[]>(`${T}/cargos`);
}

export async function getTitularesEspecialidades(): Promise<string[]> {
  return requestClient.get<string[]>(`${T}/especialidades`);
}

// ═══════════════════════════════════════════════════════════════════════════
//  API — INTERNOS
// ═══════════════════════════════════════════════════════════════════════════

const I = '/api/v1/internos';

export async function getInternos(filtros: InternoFiltros = {}): Promise<VxePage<InternoDto>> {
  const { page = 0, size = 20, ...rest } = filtros;
  const result = await requestClient.get<SpringPage<InternoDto>>(I, {
    params: clean({ page, size, ...rest }),
  });
  return toVxePage(result);
}

export async function getInternoById(id: number): Promise<InternoDto> {
  return requestClient.get<InternoDto>(`${I}/${id}`);
}

export async function createInterno(data: InternoRequest): Promise<InternoDto> {
  return requestClient.post<InternoDto>(I, data);
}

export async function updateInterno(id: number, data: InternoRequest): Promise<InternoDto> {
  return requestClient.put<InternoDto>(`${I}/${id}`, data);
}

// ✅ FIX: patch → post
// ⚠️  BACKEND: cambiar en InternoApiController.java:
//     @PatchMapping("/{id}/toggle")  →  @PostMapping("/{id}/toggle")
export async function toggleInterno(id: number): Promise<InternoDto> {
  return requestClient.post<InternoDto>(`${I}/${id}/toggle`);
}

export async function regenerarQrInterno(id: number): Promise<{ codigo: string }> {
  return requestClient.post<{ codigo: string }>(`${I}/${id}/regenerar-qr`);
}

export async function getInternosActivos(): Promise<InternoDto[]> {
  return requestClient.get<InternoDto[]>(`${I}/activos`);
}

export async function getInternosCarreras(): Promise<string[]> {
  return requestClient.get<string[]>(`${I}/carreras`);
}

export async function getInternosUniversidades(): Promise<string[]> {
  return requestClient.get<string[]>(`${I}/universidades`);
}

// ═══════════════════════════════════════════════════════════════════════════
//  API — PERSONAS (selector para formularios)
// ═══════════════════════════════════════════════════════════════════════════

const P = '/api/v1/personas';

export async function buscarPersonas(q: string): Promise<PersonaResumenDto[]> {
  return requestClient.get<PersonaResumenDto[]>(`${P}/buscar`, {
    params: { q },
  });
}

// ═══════════════════════════════════════════════════════════════════════════
//  API — RESPONSABLE
// ═══════════════════════════════════════════════════════════════════════════

const R = '/api/v1/responsables';

export async function getResponsableActivo(): Promise<null | ResponsableResumenDto> {
  return requestClient.get<null | ResponsableResumenDto>(`${R}/activo`);
}

export async function getResponsableHistorial(): Promise<ResponsableDto[]> {
  return requestClient.get<ResponsableDto[]>(`${R}/historial`);
}

export async function asignarResponsable(
  data: AsignarRequest,
): Promise<ResponsableDto[]> {
  return requestClient.post<ResponsableDto[]>(`${R}/asignar`, data);
}

export async function desasignarInterno(id: number): Promise<void> {
  return requestClient.post<void>(`${R}/${id}/desasignar`);
}