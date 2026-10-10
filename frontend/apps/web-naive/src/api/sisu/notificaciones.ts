import { requestClient } from '#/api/request';

// ── Tipos ────────────────────────────────────────────────────────────────────

export interface NotificacionHistorial {
  idNotificacion:     number;
  tipo:               string;
  titulo:             string;
  mensaje:            string;
  timestamp:          string;
  leida:              boolean;
  fechaLectura:       null | string;
  idJornada:          null | number;
  idActividadJornada: null | number;
  idSolicitud:        null | number;
  esGlobal:           boolean;
  remitenteUsername:  null | string;
}

export interface PagedNotificaciones {
  content:          NotificacionHistorial[];
  totalElements:    number;
  totalPages:       number;
  number:           number;   // página actual (0-based)
  size:             number;
}

export interface EnviarNotificacionRequest {
  usernameDestinatario?: string;   // vacío/null = broadcast global
  tipo:     string;
  titulo:   string;
  mensaje:  string;
  idJornada?:          number;
  idActividadJornada?: number;
  idSolicitud?:        number;
}

// ── Calls ────────────────────────────────────────────────────────────────────

/** Mis notificaciones (personales + globales), paginadas más recientes primero. */
export async function getMisNotificaciones(page = 0, size = 30) {
  return requestClient.get<PagedNotificaciones>(
    '/api/v1/notificaciones',
    { params: { page, size, sort: 'timestamp,desc' } },
  );
}

/** Cantidad de notificaciones personales no leídas (para el badge). */
export async function getCountNoLeidas(): Promise<number> {
  const res = await requestClient.get<number>('/api/v1/notificaciones/no-leidas');
  return res ?? 0;
}

/** Marcar una notificación como leída. */
export async function marcarLeida(idNotificacion: number) {
  return requestClient.post<NotificacionHistorial>(
    `/api/v1/notificaciones/${idNotificacion}/leer`,
  );
}

/** Marcar todas las notificaciones personales como leídas. */
export async function marcarTodasLeidas() {
  return requestClient.post<number>('/api/v1/notificaciones/leer-todas');
}

/**
 * ADMIN/SUPERVISOR: enviar notificación.
 * Si {@code usernameDestinatario} es null/vacío → broadcast a todos.
 */
export async function enviarNotificacion(data: EnviarNotificacionRequest) {
  return requestClient.post<NotificacionHistorial>('/api/v1/notificaciones/enviar', data);
}

/**
 * ADMIN/SUPERVISOR: notificaciones personales ya enviadas a un titular/interno.
 * Excluye los broadcast globales — acá interesa lo que se le avisó a esa persona.
 * Mismo par tipo/id que `getIncidenciasPersonal`.
 */
export async function getNotificacionesPersonal(
  tipo: 'INTERNO' | 'TITULAR',
  id: number,
  limit = 20,
) {
  return requestClient.get<NotificacionHistorial[]>(
    '/api/v1/notificaciones/personal',
    { params: { tipo, id, limit } },
  );
}