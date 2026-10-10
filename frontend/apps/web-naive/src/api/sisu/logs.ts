import { requestClient } from '#/api/request';

export interface LogEntry {
  id:        number;
  timestamp: string;
  origin:    string;          // BACKEND | FRONTEND
  level:     string;          // ERROR | WARN | INFO | DEBUG | TRACE
  logger:    null | string;
  thread:    null | string;
  message:   string;
  detalle:   null | string;
  usuario:   null | string;   // solo FRONTEND
}

export interface ClienteLogEntry {
  level:    string;
  logger?:  string;
  message:  string;
  detalle?: string;
}

export interface GetLogsParams {
  origin?:  string;
  level?:   string;
  q?:       string;
  sinceId?: number;
  limit?:   number;
}

/** Lista los logs del sistema (backend + frontend). Solo ADMIN/SUPERVISOR. */
export async function getLogs(params: GetLogsParams = {}) {
  return requestClient.get<LogEntry[]>('/api/v1/logs', { params });
}

/** Envía un lote de logs de la consola del frontend al backend. */
export async function enviarLogsCliente(entries: ClienteLogEntry[]) {
  return requestClient.post<void>('/api/v1/logs/cliente', entries);
}

/** Vacía el buffer de logs. */
export async function limpiarLogs() {
  return requestClient.delete<void>('/api/v1/logs');
}
