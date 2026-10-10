import { requestClient } from '#/api/request';

// ── Types ──────────────────────────────────────────────────────────────

export interface LugarResponse {
  idLugar: number;
  nombre: string;
  descripcion?: string;
  tipoLugar?: string;
  capacidad?: number;
  activo: boolean;
  latitudCentro?: number;
  longitudCentro?: number;
  radioMetros?: number;
  tieneGeofencing: boolean;
  fechaCreacion?: string;
}

export interface RolResponse {
  idRol: number;
  nombre: string;
  estado: string;
  cantidadUsuarios: number;
  fechaCreacion?: string;
}

export interface UsuarioResponse {
  idUsuario: number;
  nombre: string;
  apellido: string;
  username: string;
  email?: string;
  activo: boolean;
  idRol?: number;
  roles?: string[];
  perId?: number;
  requiereReset: boolean;
  ultimoAcceso?: string;
  fechaCreacion?: string;
  // Persona vinculada
  idPersona?: number;
  ci?: string;
  telefono?: string;
  nombreCompleto?: string;
}

export interface PersonaParaUsuario {
  idPersona: number;
  nombre: string;
  apellido: string;
  nombreCompleto: string;
  ci?: string;
  email?: string;
  telefono?: string;
  tieneUsuario: boolean;
  usuarioActivo?: boolean;
}

export interface TokenResetResponse {
  token: string;
  expiry: string;
  mensaje: string;
}

// ── Lugares ────────────────────────────────────────────────────────────
export const getLugaresPaginadoApi  = (params: Record<string, any>) =>
  requestClient.get('/api/v1/lugares/paginado', { params });

export const getLugaresActivosApi   = () =>
  requestClient.get<LugarResponse[]>('/api/v1/lugares');

export const getLugarByIdApi        = (id: number) =>
  requestClient.get<LugarResponse>(`/api/v1/lugares/${id}`);

export const crearLugarApi          = (data: any) =>
  requestClient.post<LugarResponse>('/api/v1/lugares', data);

export const actualizarLugarApi     = (id: number, data: any) =>
  requestClient.post<LugarResponse>(`/api/v1/lugares/${id}/editar`, data);

export const activarLugarApi        = (id: number) =>
  requestClient.post(`/api/v1/lugares/${id}/activar`);

export const desactivarLugarApi     = (id: number) =>
  requestClient.post(`/api/v1/lugares/${id}/desactivar`);

export const getGeofencingStatsApi  = () =>
  requestClient.get<number>('/api/v1/lugares/stats/geofencing');

// ── Roles ──────────────────────────────────────────────────────────────
export const getRolesPaginadoApi    = (params: Record<string, any>) =>
  requestClient.get('/api/v1/roles', { params });

export const getRolesActivosApi     = () =>
  requestClient.get<RolResponse[]>('/api/v1/roles/activos');

export const crearRolApi            = (data: { nombre: string }) =>
  requestClient.post<RolResponse>('/api/v1/roles', data);

export const actualizarRolApi       = (id: number, data: { nombre: string }) =>
  requestClient.post<RolResponse>(`/api/v1/roles/${id}/editar`, data);

export const activarRolApi          = (id: number) =>
  requestClient.post(`/api/v1/roles/${id}/activar`);

export const desactivarRolApi       = (id: number) =>
  requestClient.post(`/api/v1/roles/${id}/desactivar`);

// ── Usuarios ───────────────────────────────────────────────────────────
export const getUsuariosPaginadoApi = (params: Record<string, any>) =>
  requestClient.get('/api/v1/usuarios', { params });

export const getUsuarioByIdApi      = (id: number) =>
  requestClient.get<UsuarioResponse>(`/api/v1/usuarios/${id}`);

export const crearUsuarioApi        = (data: any) =>
  requestClient.post<UsuarioResponse>('/api/v1/usuarios', data);

export const actualizarUsuarioApi   = (id: number, data: any) =>
  requestClient.post<UsuarioResponse>(`/api/v1/usuarios/${id}/editar`, data);

export const activarUsuarioApi      = (id: number) =>
  requestClient.post(`/api/v1/usuarios/${id}/activar`);

export const desactivarUsuarioApi   = (id: number) =>
  requestClient.post(`/api/v1/usuarios/${id}/desactivar`);

export const generarTokenResetApi   = (id: number) =>
  requestClient.post<TokenResetResponse>(`/api/v1/usuarios/${id}/generar-token-reset`);

export const forzarResetApi         = (id: number) =>
  requestClient.post(`/api/v1/usuarios/${id}/forzar-reset`);

// ── Personas ───────────────────────────────────────────────────────────
export const getPersonasParaUsuarioApi = () =>
  requestClient.get<PersonaParaUsuario[]>('/api/v1/personas/para-usuario');