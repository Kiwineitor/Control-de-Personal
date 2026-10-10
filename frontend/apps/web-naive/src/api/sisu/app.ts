// apps/web-naive/src/api/sisu/app.ts
import { useAccessStore } from '@vben/stores';
import { requestClient } from '#/api/request';
import type {
  ActividadJornadaAppDto,
  SubirEvidenciaResponse,
  CulminarActividadRequest,
} from './actividades';

// ─── Re-exportar para que las vistas no importen de dos lugares ───
export type { ActividadJornadaAppDto, SubirEvidenciaResponse, CulminarActividadRequest };

// ─── Tipos propios del App Funcionarios ──────────────────────────

export type TipoAsignacion = 'TITULAR' | 'INTERNO';

export interface MiJornadaResponse {
  idJornada:              number;
  nombre:                 string;
  fechaInicio:            string;
  fechaFin:               string;
  horaInicio:             string;
  horaFin:                string;
  nombreLugar:            string | null;
  estadoJornada:          string;
  tipoAsignacion:         TipoAsignacion;
  idAsignacion:           number;
  totalActividades:       number;
  actividadesPendientes:  number;
  actividadesCompletadas: number;
  actividadesValidadas:   number;
  idTitular: number | null;
}

export interface ActividadCatalogoDto {
  idActividad:   number;
  nombre:        string;
  descripcion:   string | null;
  tipoActividad: string;
}

export interface AutoReporteRequest {
  idActividad:   number;
  observaciones?: string;
  horaInicio?:   string;   // formato HH:mm:ss
  horaFin?:      string;
}

// Agregar junto a los otros interfaces:
export interface UbicacionResponse {
  dentroRadio:              boolean;
  distanciaMetros:          number;
  nombreLugar:              string | null;
  radioMetros:              number;
  mensaje:                  string;
  timestamp:                string;
  estadoZona?:              string;
  intervaloProximoEnvioSeg?: number;
  enTurno?:                 boolean;
  enTurnoActivo?:           boolean;
  mensajePersonal?:         string;
  tiempoFueraMinutos?:      number;
  abandonoConfirmado?:      boolean;
}

// ─── API Calls ────────────────────────────────────────────────────

/** Jornada activa del usuario actual + resumen de actividades */
export async function getMiJornada() {
  return requestClient.get<MiJornadaResponse>('/api/v1/app/mi-jornada');
}

/**
 * Actividades asignadas/registradas del usuario en su jornada activa.
 * culminada: undefined = todas | true = completadas | false = pendientes
 */
export async function getMisActividades(params?: { culminada?: boolean }) {
  return requestClient.get<ActividadJornadaAppDto[]>(
    '/api/v1/app/mis-actividades',
    { params },
  );
}

/** Auto-reportar una actividad que el funcionario realizó */
export async function crearMiActividad(data: AutoReporteRequest) {
  return requestClient.post<ActividadJornadaAppDto>(
    '/api/v1/app/mis-actividades',
    data,
  );
}

/** Catálogo de actividades disponibles para el formulario de auto-reporte */
export async function getCatalogoActividades() {
  return requestClient.get<ActividadCatalogoDto[]>(
    '/api/v1/app/catalogo-actividades',
  );
}

/**
 * Subir evidencia desde la app.
 * Usa fetch nativo para evitar que Axios sobreescriba Content-Type con
 * application/json, lo que rompe el multipart/form-data + boundary.
 */
export async function subirEvidenciaApp(
  idActividadJornada: number,
  file: File,
): Promise<SubirEvidenciaResponse> {
  const accessStore = useAccessStore();
  const apiBase = (import.meta.env.VITE_GLOB_API_URL as string) ?? '';
  const fd = new FormData();
  fd.append('file', file);

  const resp = await fetch(
    `${apiBase}/api/v1/actividades/asignacion/${idActividadJornada}/subir-evidencia`,
    {
      method: 'POST',
      headers: { Authorization: `Bearer ${accessStore.accessToken ?? ''}` },
      body: fd,
    },
  );

  const json = await resp.json();
  if (!resp.ok || json.code !== 0)
    throw new Error(json.message ?? `Error al subir evidencia (${resp.status})`);
  return json.data as SubirEvidenciaResponse;
}

/** Cancelar/suspender una actividad propia desde la app */
export async function cancelarActividadApp(
  idActividadJornada: number,
  observaciones?: string,
) {
  return requestClient.post<ActividadJornadaAppDto>(
    `/api/v1/actividades/${idActividadJornada}/estado`,
    { estado: 'CANCELADA', observaciones: observaciones ?? '' },
  );
}

/** Culminar actividad desde la app (reutiliza el endpoint de Paso 8A) */
export async function culminarActividadApp(
  idActividadJornada: number,
  observaciones?: string,
) {
  return requestClient.post<ActividadJornadaAppDto>(
    `/api/v1/actividades/asignacion/${idActividadJornada}/culminar`,
    { observaciones: observaciones ?? '' },
  );
}

/** Descargar evidencia con JWT (retorna Blob para abrir en nueva pestaña) */
export async function descargarEvidenciaApp(downloadUrl: string): Promise<Blob> {
  return requestClient.get<Blob>(downloadUrl, { responseType: 'blob' });
}

/* ------------------------ permiso ----------------- */

// Agregar al final del archivo (importar SolicitudPermisoResponse de permisos):
import type { SolicitudPermisoResponse } from './permisos';

export interface MiSolicitudRequest {
  tipoPermiso:     string;
  motivoSolicitud: string;
  fechaPermiso?:   string;   // yyyy-MM-dd  (día único o inicio del rango)
  fechaFin?:       string;   // yyyy-MM-dd  (solo para permisos de varios días)
  horaInicio?:     string;   // HH:mm
  horaFin?:        string;
}

export interface NotificacionWs {
  /** ID local o idNotificacion de BD — siempre disponible. */
  id:                  number;
  /** ID persistido en BD (viene del backend tras guardar). */
  idNotificacion?:     number;
  tipo:                string;
  titulo:              string;
  mensaje:             string;
  timestamp:           string;
  idSolicitud?:        number;
  idJornada?:          number;
  idActividadJornada?: number;
  tipoPermiso?:        string;
  leida:               boolean;
  /** true → broadcast global enviado a todos. */
  esGlobal?:           boolean;
}

export async function getMisPermisos() {
  return requestClient.get<SolicitudPermisoResponse[]>('/api/v1/app/mis-permisos');
}
export async function crearMiPermiso(data: MiSolicitudRequest) {
  return requestClient.post<SolicitudPermisoResponse>('/api/v1/app/mis-permisos', data);
}
export async function modificarMiPermiso(idSolicitud: number, data: MiSolicitudRequest) {
  return requestClient.put<SolicitudPermisoResponse>(`/api/v1/app/mis-permisos/${idSolicitud}`, data);
}
export async function subirJustificanteApp(idSolicitud: number, file: File): Promise<SolicitudPermisoResponse> {
  const accessStore = useAccessStore();
  const apiBase = (import.meta.env.VITE_GLOB_API_URL as string) ?? '';
  const fd = new FormData();
  fd.append('file', file);

  const resp = await fetch(
    `${apiBase}/api/v1/app/mis-permisos/${idSolicitud}/justificante`,
    {
      method: 'POST',
      headers: { Authorization: `Bearer ${accessStore.accessToken ?? ''}` },
      body: fd,
    },
  );

  const json = await resp.json();
  if (!resp.ok || json.code !== 0)
    throw new Error(json.message ?? `Error al subir justificante (${resp.status})`);
  return json.data as SolicitudPermisoResponse;
}
export interface TipoPermisoOpcion { value: string; label: string }
export async function getTiposPermiso() {
  return requestClient.get<TipoPermisoOpcion[]>('/api/v1/app/tipos-permiso');
}

// ── Mis Faltas ────────────────────────────────────────────────────────────

export interface MiFaltaDto {
  idEvento:           number;
  /** FUERA_ZONA | ABANDONO_CONFIRMADO | RETORNADO | RESUELTO */
  estado:             string;
  nombreLugar:        string | null;
  timestampSalida:    string;
  timestampRetorno:   string | null;
  tiempoFueraMinutos: number;
  observaciones:      string | null;
  /** ABANDONO_ZONA | AUSENCIA_SENAL | LOGOUT_EN_TURNO | ATRASO | FALTA | SALIDA_TEMPRANA */
  tipo:                     string;
  tipoLabel:                string;
  /** NO_REQUERIDA | PENDIENTE | EN_REVISION | JUSTIFICADA | RECHAZADA */
  estadoJustificacion:      string;
  estadoJustificacionLabel: string;
  /** true → el funcionario todavía puede enviar una justificación. */
  puedeJustificar:          boolean;
  motivoJustificacion:      string | null;
  observacionRevision:      string | null;
  /** Minutos de la incidencia cuando aplica (atraso/salida temprana). */
  minutosIncidencia:        number | null;
}

export async function getMisFaltas() {
  return requestClient.get<MiFaltaDto[]>('/api/v1/app/mis-faltas');
}

/**
 * Justifica una incidencia propia (motivo + adjunto opcional).
 * Usa fetch nativo para no romper el multipart/form-data (ver subirEvidenciaApp).
 */
export async function justificarIncidencia(
  idEvento: number,
  motivo: string,
  file?: File | null,
): Promise<void> {
  const accessStore = useAccessStore();
  const apiBase = (import.meta.env.VITE_GLOB_API_URL as string) ?? '';
  const fd = new FormData();
  fd.append('motivo', motivo);
  if (file) fd.append('justificante', file);

  const resp = await fetch(
    `${apiBase}/api/v1/monitoreo/incidencias/${idEvento}/justificar`,
    {
      method: 'POST',
      headers: { Authorization: `Bearer ${accessStore.accessToken ?? ''}` },
      body: fd,
    },
  );

  const json = await resp.json();
  if (!resp.ok || json.code !== 0)
    throw new Error(json.message ?? `Error al enviar la justificación (${resp.status})`);
}

// ── Calendario de Turnos ──────────────────────────────────────────────────

export interface DiaTurnoCalendarioDto {
  fecha:           string;   // yyyy-MM-dd
  diaSemana:       string;   // "lunes", "martes"...
  horaInicio:      string | null;
  horaFin:         string | null;
  modalidad:       'CONTINUA' | 'PARTIDA';
  horaInicioTarde: string | null;
  horaFinTarde:    string | null;
  nombreTurno:     string | null;
  tipoTurno:       string;
  idJornada:       number;
  nombreLugar:     string | null;
  esHoy:           boolean;
  esPasado:        boolean;
}

export async function getMisTurnos(mes?: number, anio?: number) {
  const params: Record<string, any> = {};
  if (mes  != null) params.mes  = mes;
  if (anio != null) params.anio = anio;
  return requestClient.get<DiaTurnoCalendarioDto[]>('/api/v1/app/mis-turnos', { params });
}

// Re-exportar para las vistas
export type { SolicitudPermisoResponse } from './permisos';