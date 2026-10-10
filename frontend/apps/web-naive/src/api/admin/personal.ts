// api/admin/personal.ts
import { requestClient } from '#/api/request';

// ══ Tipos ══════════════════════════════════════════════════════════════════

export interface TipoPersonalDto {
  idTipoPersonal:        number;
  nombre:                string;
  descripcion:           string | null;
  color:                 string;
  esPersonalMedico:      boolean;
  puedeGestionarInternos: boolean;
  activo:                boolean;
  totalPersonal:         number;
}

export interface TipoPersonalRequest {
  nombre:                string;
  descripcion?:          string;
  color?:                string;
  esPersonalMedico:      boolean;
  puedeGestionarInternos: boolean;
  activo:                boolean;
}

export interface PersonalHospitalDto {
  idTitular:             number;
  idPersona:             number | null;
  nombreCompleto:        string;
  ci:                    string | null;
  email:                 string | null;
  telefono:              string | null;
  cargo:                 string | null;
  especialidad:          string | null;
  idTipoPersonal:        number | null;
  tipoPersonalNombre:    string | null;
  tipoPersonalColor:     string | null;
  esPersonalMedico:      boolean;
  puedeGestionarInternos: boolean;
  idLugar:               number | null;
  lugarNombre:           string | null;
  activo:                boolean;
  idUsuario:             number | null;
  username:              string | null;
  tieneUsuario:          boolean;
}

export interface RegistrarPersonalRequest {
  // Persona
  nombre:              string;
  apellido:            string;
  ci?:                 string;
  email?:              string;
  telefono?:           string;
  // Titular
  idTipoPersonal:      number;
  cargo?:              string;
  especialidad?:       string;
  idLugar?:            number;
  // Usuario opcional
  crearUsuario:        boolean;
  username?:           string;
  password?:           string;
  idRol?:              number;
}

// ══ TipoPersonal API ═══════════════════════════════════════════════════════

export async function getTiposPersonal() {
  return requestClient.get<TipoPersonalDto[]>('/api/v1/personal/tipos');
}
export async function crearTipoPersonal(data: TipoPersonalRequest) {
  return requestClient.post<TipoPersonalDto>('/api/v1/personal/tipos', data);
}
export async function actualizarTipoPersonal(id: number, data: TipoPersonalRequest) {
  return requestClient.put<TipoPersonalDto>(`/api/v1/personal/tipos/${id}`, data);
}
export async function toggleTipoPersonal(id: number) {
  return requestClient.post<void>(`/api/v1/personal/tipos/${id}/toggle`, {});
}

// ══ Personal API ════════════════════════════════════════════════════════════

export async function getPersonal(params: {
  q?: string; idTipoPersonal?: number; activo?: boolean; page: number; size: number;
}) {
  return requestClient.get<any>('/api/v1/personal', { params });
}
export async function registrarPersonal(data: RegistrarPersonalRequest) {
  return requestClient.post<PersonalHospitalDto>('/api/v1/personal', data);
}
export async function actualizarPersonal(id: number, data: RegistrarPersonalRequest) {
  return requestClient.put<PersonalHospitalDto>(`/api/v1/personal/${id}`, data);
}
export async function togglePersonal(id: number) {
  return requestClient.post<void>(`/api/v1/personal/${id}/toggle`, {});
}
