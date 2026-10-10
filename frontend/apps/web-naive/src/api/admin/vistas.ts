import { requestClient } from '#/api/request';

export interface VistaDto {
  idVista: number;
  codigo:  string;
  nombre:  string;
  grupo:   null | string;
  orden:   number;
  activo:  boolean;
}

/** tipo: 'GRANT' (agrega) | 'DENY' (quita). */
export interface OverrideVista {
  codigo: string;
  tipo:   string;
}

export interface UsuarioVistasResumen {
  baseRol:   string[];
  overrides: OverrideVista[];
  efectivas: string[];
}

/** Catálogo completo de vistas asignables. */
export async function getCatalogoVistas() {
  return requestClient.get<VistaDto[]>('/api/v1/vistas');
}

/** Códigos de vista base de un rol. */
export async function getVistasDeRol(idRol: number) {
  return requestClient.get<string[]>(`/api/v1/vistas/rol/${idRol}`);
}

/** Reemplaza las vistas base de un rol. */
export async function setVistasDeRol(idRol: number, codigos: string[]) {
  return requestClient.put<void>(`/api/v1/vistas/rol/${idRol}`, codigos);
}

/** Resumen de permisos de un usuario (base por rol + overrides + efectivas). */
export async function getResumenUsuarioVistas(idUsuario: number) {
  return requestClient.get<UsuarioVistasResumen>(`/api/v1/vistas/usuario/${idUsuario}`);
}

/** Reemplaza los overrides (GRANT/DENY) de un usuario. */
export async function setOverridesUsuario(idUsuario: number, overrides: OverrideVista[]) {
  return requestClient.put<void>(`/api/v1/vistas/usuario/${idUsuario}`, overrides);
}
