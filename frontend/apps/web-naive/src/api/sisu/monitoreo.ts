// apps/web-naive/src/api/sisu/monitoreo.ts

import type { UbicacionResponse } from '#/api/sisu/app';

import { requestClient } from '#/api/request';

// ══ Tipos ══════════════════════════════════════════════════════════════════

export type TipoRonda          = 'ALEATORIA' | 'MANUAL' | 'PROGRAMADA';
export type EstadoZona         = 'DENTRO' | 'DESCONOCIDO' | 'FUERA';
export type EstadoEvento       = 'ABANDONO_CONFIRMADO' | 'FUERA_ZONA' | 'RESUELTO' | 'RETORNADO';
export type EstadoRonda        = 'CANCELADA' | 'COMPLETADA' | 'EN_CURSO' | 'PENDIENTE';
export type EstadoVerificacion = 'AUSENTE' | 'PENDIENTE' | 'PRESENTE' | 'SIN_RESPUESTA';
export type MetodoVerificacion = 'FOTO' | 'GPS' | 'MANUAL' | 'QR';
export type MetodoRegistro     = 'FOTO' | 'GPS' | 'MANUAL' | 'QR';
export type EstadoEntrada      = 'A_TIEMPO' | 'AUSENTE' | 'JUSTIFICADO' | 'TARDANZA';
export type EstadoSalida       = 'COMPLETA' | 'NO_REGISTRADA' | 'SALIDA_ANTICIPADA';

export interface CodigoQrDto {
  idCodigoQr:       number;
  idLugar:          number;
  nombreLugar:      string;
  descripcionLugar: null | string;
  codigo:           string;
  activo:           boolean;
  esValido:         boolean;
  fechaExpiracion:  null | string;
  imagenUrl:        null | string;
  fechaCreacion:    null | string;
}

export interface RondaVerificacionDto {
  idRondaVerificacion: number;
  nombre:              null | string;
  tipoRonda:           TipoRonda;
  estado:              EstadoRonda;
  fechaHoraInicio:     string;
  fechaHoraFin:        null | string;
  timeoutMinutos:      number;
  observaciones:       null | string;
  totalVerificaciones: number;
  totalPresentes:      number;
  totalAusentes:       number;
  totalSinRespuesta:   number;
  totalPendientes:     number;
  porcentajePresencia: number;
  fechaCreacion:       null | string;
}

export interface VerificacionPresenciaDto {
  idVerificacionPresencia: number;
  idRondaVerificacion:     number;
  tipoPersonal:            'INTERNO' | 'TITULAR';
  idPersona:               number;
  nombrePersonal:          string;
  estadoVerificacion:      EstadoVerificacion;
  metodoVerificacion:      MetodoVerificacion | null;
  fechaHoraEnvio:          string;
  fechaHoraRespuesta:      null | string;
  tiempoRespuestaMinutos:  null | number;
  latitud:                 null | number;
  longitud:                null | number;
  fotoUrl:                 null | string;
  observaciones:           null | string;
}

export interface RegistroAsistenciaDto {
  idRegistroAsistencia: number;
  tipoPersonal:         'INTERNO' | 'TITULAR';
  idPersona:            number;
  nombrePersonal:       string;
  idJornada:            number;
  nombreJornada:        null | string;
  horaEntradaEsperada:  null | string;
  horaSalidaEsperada:   null | string;
  horaEntradaReal:      null | string;
  horaSalidaReal:       null | string;
  estadoEntrada:        EstadoEntrada | null;
  estadoSalida:         EstadoSalida | null;
  metodoEntrada:        MetodoRegistro | null;
  metodoSalida:         MetodoRegistro | null;
  minutosTardanza:      number;
  tieneEntrada:         boolean;
  tieneSalida:          boolean;
  observaciones:        null | string;
}

export interface RondaVerificacionRequest {
  nombre?:         string;
  tipoRonda?:      TipoRonda;
  fechaHoraInicio: string;   // ISO: "yyyy-MM-ddTHH:mm:ss"
  timeoutMinutos?: number;
  idJornada?:      number;
  observaciones?:  string;
}

export interface ConfirmarPresenciaRequest {
  metodo:          MetodoVerificacion;
  latitud?:        number;
  longitud?:       number;
  fotoUrl?:        string;
  observaciones?:  string;
}

export interface EscanearQrRequest {
  codigoQr:    string;
  idPersona:   number;
  tipoPersona: 'INTERNO' | 'TITULAR';
  latitud?:    number;
  longitud?:   number;
}

export interface RegistrarMovimientoRequest {
  idInternoJornada?: number;
  idTitularJornada?: number;
  metodo?:           MetodoRegistro;
  latitud?:          number;
  longitud?:         number;
  observaciones?:    string;
}

export interface ResumenRondaDto {
  idRondaVerificacion: number;
  nombre:              null | string;
  estado:              EstadoRonda;
  fechaHoraInicio:     string;
  total:               number;
  presentes:           number;
  ausentes:            number;
  sinRespuesta:        number;
  pendientes:          number;
  porcentajePresencia: number;
  verificaciones:      VerificacionPresenciaDto[];
}

export interface EstadisticasMonitoreoDto {
  totalRondas:              number;
  verificacionesTotales:    number;
  presenciaConfirmada:      number;
  ausencias:                number;
  sinRespuesta:             number;
  porcentajePresenciaGlobal: number;
  registrosAsistenciaHoy:   number;
  tardanzasHoy:             number;
}

function toVxePage<T>(raw: any) {
  const meta = (raw as any).page ?? raw;
  return { items: (raw.content ?? []) as T[], total: (meta.totalElements ?? 0) as number };
}

export interface RondaParams {
  page:    number;
  size:    number;
  q?:      string;
  estado?: EstadoRonda;
  fecha?:  string;
}

// ── QR Codes ──────────────────────────────────────────────────────────────
export async function listarQrs() {
  return requestClient.get<CodigoQrDto[]>('/api/v1/monitoreo/codigos-qr');
}
export async function crearQrParaLugar(idLugar: number) {
  return requestClient.post<CodigoQrDto>(`/api/v1/monitoreo/codigos-qr/lugar/${idLugar}`, {});
}
export async function regenerarQr(idLugar: number) {
  return requestClient.post<CodigoQrDto>(`/api/v1/monitoreo/codigos-qr/lugar/${idLugar}/regenerar`, {});
}
export async function toggleQr(idLugar: number) {
  return requestClient.post<CodigoQrDto>(`/api/v1/monitoreo/codigos-qr/lugar/${idLugar}/toggle`, {});
}

// ── Rondas ────────────────────────────────────────────────────────────────
export async function getRondas(p: RondaParams) {
  const raw = await requestClient.get<any>('/api/v1/monitoreo/rondas', { params: p });
  return toVxePage<RondaVerificacionDto>(raw);
}
export async function crearRonda(data: RondaVerificacionRequest) {
  return requestClient.post<RondaVerificacionDto>('/api/v1/monitoreo/rondas', data);
}
export async function iniciarRonda(id: number) {
  return requestClient.post<RondaVerificacionDto>(`/api/v1/monitoreo/rondas/${id}/iniciar`, {});
}
export async function cerrarRonda(id: number) {
  return requestClient.post<RondaVerificacionDto>(`/api/v1/monitoreo/rondas/${id}/cerrar`, {});
}
export async function cancelarRonda(id: number) {
  return requestClient.post<RondaVerificacionDto>(`/api/v1/monitoreo/rondas/${id}/cancelar`, {});
}

// ── Verificaciones ────────────────────────────────────────────────────────
export async function getVerificacionesByRonda(id: number) {
  return requestClient.get<VerificacionPresenciaDto[]>(`/api/v1/monitoreo/rondas/${id}/verificaciones`);
}
export async function confirmarPresencia(id: number, data: ConfirmarPresenciaRequest) {
  return requestClient.post<VerificacionPresenciaDto>(`/api/v1/monitoreo/verificaciones/${id}/confirmar`, data);
}
export async function marcarAusente(id: number, observaciones?: string) {
  return requestClient.post<VerificacionPresenciaDto>(
    `/api/v1/monitoreo/verificaciones/${id}/ausente`,
    {},
    { params: observaciones ? { observaciones } : {} }
  );
}
export async function escanearQr(data: EscanearQrRequest) {
  return requestClient.post<VerificacionPresenciaDto>('/api/v1/monitoreo/verificaciones/escanear-qr', data);
}

// ── Asistencia ────────────────────────────────────────────────────────────
export async function getAsistenciaJornada(idJornada: number) {
  return requestClient.get<RegistroAsistenciaDto[]>(`/api/v1/monitoreo/asistencia/jornada/${idJornada}`);
}
export async function registrarEntrada(data: RegistrarMovimientoRequest) {
  return requestClient.post<RegistroAsistenciaDto>('/api/v1/monitoreo/asistencia/entrada', data);
}
export async function registrarSalida(data: RegistrarMovimientoRequest) {
  return requestClient.post<RegistroAsistenciaDto>('/api/v1/monitoreo/asistencia/salida', data);
}
export async function inicializarRegistrosJornada(idJornada: number) {
  return requestClient.post<void>(`/api/v1/monitoreo/asistencia/inicializar-jornada/${idJornada}`, {});
}

// ── Reportes ──────────────────────────────────────────────────────────────
export async function getResumenRonda(id: number) {
  return requestClient.get<ResumenRondaDto>(`/api/v1/monitoreo/reportes/resumen-ronda/${id}`);
}
export async function getEstadisticasMonitoreo() {
  return requestClient.get<EstadisticasMonitoreoDto>('/api/v1/monitoreo/reportes/estadisticas');
}

// ══ Tipos: Monitoreo Continuo ═══════════════════════════════════════════════

export interface SesionMonitoreoDto {
  idSesion:               number;
  nombrePersonal:         string;
  cargo:                  null | string;
  tipoPersonal:           'INTERNO' | 'TITULAR';
  idPersonal:             null | number;
  idJornada:              null | number;
  nombreJornada:          null | string;
  idLugar:                null | number;
  nombreLugar:            null | string;
  estadoZona:             EstadoZona;
  timestampInicio:        string;
  timestampPrimeraPresencia: null | string;
  ultimaActualizacion:    null | string;
  tiempoFueraMinutos:     number;
  ultimaLatitud:          null | number;
  ultimaLongitud:         null | number;
  ultimaDistanciaMetros:  null | number;
  tieneEventoAbierto:     boolean;
}

export interface PersonalActivoMapaDto {
  idSesion:            number;
  nombrePersonal:      string;
  cargo:               null | string;
  tipoPersonal:        'INTERNO' | 'TITULAR';
  idLugar:             null | number;
  nombreLugar:         null | string;
  latitudLugar:        null | number;
  longitudLugar:       null | number;
  radioMetrosLugar:    null | number;
  latitudPersonal:     null | number;
  longitudPersonal:    null | number;
  distanciaMetros:     null | number;
  estadoZona:          EstadoZona;
  tiempoFueraMinutos:  number;
  ultimaActualizacion: null | string;
}

export interface EventoAbandonoDto {
  idEvento:               number;
  idSesion:               null | number;
  nombrePersonal:         string;
  cargo:                  null | string;
  tipoPersonal:           'INTERNO' | 'TITULAR';
  idLugar:                null | number;
  nombreLugar:            null | string;
  timestampSalida:        string;
  timestampAbandono:      null | string;
  timestampRetorno:       null | string;
  tiempoFueraMinutos:     number;
  distanciaAlSalirMetros: null | number;
  estado:                 EstadoEvento;
  notificadoAdmin:        boolean;
  resuelto:               boolean;
  observaciones:          null | string;
}

export interface ConfiguracionMonitoreoDto {
  timeoutAbandonoMinutos:   number;
  timeoutSinSenalMinutos:   number;
  toleranciaAtrasoMin:      number;
  umbralFaltaMin:           number;
  intervaloGpsActivoSeg:    number;
  intervaloGpsFueraZonaSeg: number;
  deltaMinMovimientoMetros: number;
  radioAlertaExtraMetros:   number;
  confirmacionFueraSeg:     number;
  precisionMaxConfiableMetros: number;
}

export interface PersonalEnTurnoDto {
  idPersonal:       number;
  tipoPersonal:     'INTERNO' | 'TITULAR';
  nombrePersonal:   string;
  cargo:            null | string;
  idJornada:        null | number;
  nombreJornada:    null | string;
  idLugar:          null | number;
  nombreLugar:      null | string;
  horaInicio:       null | string;
  horaFin:          null | string;
  tieneSesionActiva: boolean;
}

export interface AlertaMonitoreoDto {
  tipo:               'ABANDONO' | 'GPS_DENEGADO' | 'RETORNO' | 'SALIDA_ZONA';
  idEvento:           number;
  idSesion:           null | number;
  nombrePersonal:     string;
  nombreLugar:        null | string;
  estado:             EstadoEvento;
  tiempoFueraMinutos: number;
  timestamp:          string;
}

// ── Monitoreo Continuo ────────────────────────────────────────────────────

/**
 * Envía las coordenadas GPS por HTTP (espejo del canal STOMP /app/gps/location).
 * Se usa en la app nativa porque el WebSocket se cae con la app en segundo plano:
 * el plugin de background-geolocation sigue entregando fixes y los manda por aquí.
 * La respuesta trae el mismo UbicacionResponse (estado de zona, intervalo, etc.).
 */
/** Señales de contexto opcionales (Fase 2) que acompañan a un fix GPS. */
export interface SenalesContexto {
  bateria?:      number;   // 0–100
  cargando?:     boolean;
  conectividad?: string;   // WIFI | CELULAR | NINGUNA
  esMock?:       boolean;
}

export async function enviarUbicacion(
  lat: number,
  lon: number,
  precision?: number,
  senales?: SenalesContexto,
) {
  return requestClient.post<UbicacionResponse>('/api/v1/monitoreo/ubicacion', {
    latitud: lat,
    longitud: lon,
    ...(precision == null ? {} : { precision }),
    ...(senales ?? {}),
  });
}

/** Un fix bufferizado para envío en lote (store-and-forward, Fase 3). */
export interface FixBufferizado extends SenalesContexto {
  latitud:                 number;
  longitud:                number;
  precision?:              number;
  /** epoch millis del fix en el dispositivo (para ordenar el lote). */
  timestampDispositivoMs?: number;
  /** id único del fix generado en el cliente (idempotencia). */
  clientEventId?:          string;
}

export interface LoteUbicacionResponse {
  recibidos:    number;
  procesados:   number;
  omitidos:     number;
  ultimoEstado: UbicacionResponse | null;
}

/**
 * Envía un LOTE de ubicaciones bufferizadas mientras el dispositivo estuvo sin
 * internet (store-and-forward). El backend las reproduce en orden, con idempotencia
 * por clientEventId, y reconcilia abandonos provisionales.
 */
export async function enviarUbicacionesLote(fixes: FixBufferizado[]) {
  return requestClient.post<LoteUbicacionResponse>('/api/v1/monitoreo/ubicaciones/lote', fixes);
}

export async function getSesionesActivas() {
  return requestClient.get<SesionMonitoreoDto[]>('/api/v1/monitoreo/sesiones/activas');
}
export async function getPersonalActivoMapa() {
  return requestClient.get<PersonalActivoMapaDto[]>('/api/v1/monitoreo/mapa/personal-activo');
}
export async function getAlertasActivas() {
  return requestClient.get<EventoAbandonoDto[]>('/api/v1/monitoreo/alertas');
}
export async function resolverAlerta(id: number, observaciones?: string) {
  return requestClient.post<EventoAbandonoDto>(
    `/api/v1/monitoreo/alertas/${id}/resolver`,
    {},
    { params: observaciones ? { observaciones } : {} },
  );
}
export async function getConfiguracion() {
  return requestClient.get<ConfiguracionMonitoreoDto>('/api/v1/monitoreo/configuracion');
}
export async function updateConfiguracion(data: Partial<ConfiguracionMonitoreoDto>) {
  return requestClient.put<ConfiguracionMonitoreoDto>('/api/v1/monitoreo/configuracion', data);
}
export async function getPersonalEnTurnoAhora() {
  return requestClient.get<PersonalEnTurnoDto[]>('/api/v1/monitoreo/personal/en-turno-ahora');
}

// ── Historial de incidencias por usuario (ficha del admin) ──────────────────

export interface IncidenciaPersonalDto {
  idEvento:                 number;
  tipo:                     string;
  tipoLabel:                string;
  estado:                   string;
  estadoJustificacion:      string;
  estadoJustificacionLabel: string;
  nombreLugar:              null | string;
  timestampSalida:          null | string;
  timestampRetorno:         null | string;
  tiempoFueraMinutos:       number;
  minutosIncidencia:        null | number;
  observaciones:            null | string;
}

/** Incidencias (abandonos, atrasos, faltas…) de un titular/interno. tipo: 'TITULAR'|'INTERNO'. */
export async function getIncidenciasPersonal(tipo: string, id: number) {
  return requestClient.get<IncidenciaPersonalDto[]>(
    '/api/v1/monitoreo/personal/incidencias',
    { params: { tipo, id } },
  );
}

// ── Justificación de incidencias (revisión por admin/supervisor) ────────────

export interface IncidenciaRevisionDto {
  idEvento:                    number;
  nombrePersonal:              string;
  tipo:                        string;
  tipoLabel:                   string;
  estado:                      string;
  estadoJustificacion:         string;
  nombreLugar:                 null | string;
  timestampSalida:             null | string;
  tiempoFueraMinutos:          number;
  minutosIncidencia:           null | number;
  observaciones:               null | string;
  motivoJustificacion:         null | string;
  justificanteUrl:             null | string;
  fechaSolicitudJustificacion: null | string;
}

export async function getIncidenciasPendientesJustificacion() {
  return requestClient.get<IncidenciaRevisionDto[]>(
    '/api/v1/monitoreo/incidencias/pendientes-justificacion',
  );
}
export async function aprobarJustificacion(id: number, observacion?: string) {
  return requestClient.post<void>(
    `/api/v1/monitoreo/incidencias/${id}/justificacion/aprobar`,
    { observacion: observacion ?? '' },
  );
}
export async function rechazarJustificacion(id: number, observacion?: string) {
  return requestClient.post<void>(
    `/api/v1/monitoreo/incidencias/${id}/justificacion/rechazar`,
    { observacion: observacion ?? '' },
  );
}

export interface UsuarioConectadoDto {
  username:    string;
  nombre:      string;
  tieneGps:    boolean;
  estadoZona:  'DENTRO' | 'DESCONOCIDO' | 'FUERA' | 'SIN_SESION';
  ultimaSenal: null | string;
  sesiones:    number;
}

export async function getUsuariosConectados() {
  return requestClient.get<UsuarioConectadoDto[]>('/api/v1/monitoreo/usuarios-conectados');
}

// ══ Tipos: ROL TURNO ════════════════════════════════════════════════════════

export interface DiaTurnoDto {
  idDiaTurno:   null | number;
  fecha:        string;   // yyyy-MM-dd
  horaInicio:   null | string;
  horaFin:      null | string;
  observaciones: null | string;
}

export interface RolTurnoDto {
  idRolTurno:        number;
  idTitularJornada:  number;
  idJornada:         number;
  nombreJornada:     null | string;
  idTitular:         number;
  nombreTitular:     string;
  cargo:             null | string;
  mes:               number;
  anio:              number;
  observaciones:     null | string;
  activo:            boolean;
  dias:              DiaTurnoDto[];
  horaInicioJornada: null | string;
  horaFinJornada:    null | string;
}

export interface RolTurnoRequest {
  idTitularJornada: number;
  mes:              number;
  anio:             number;
  observaciones?:   string;
  dias: Array<{
    fecha:        string;
    horaFin?:     string;
    horaInicio?:  string;
    observaciones?: string;
  }>;
}

// ── ROL TURNO API ─────────────────────────────────────────────────────────
export async function guardarRolTurno(data: RolTurnoRequest) {
  return requestClient.post<RolTurnoDto>('/api/v1/rol-turno', data);
}
export async function getRolTurnoByJornada(idJornada: number) {
  return requestClient.get<RolTurnoDto[]>(`/api/v1/rol-turno/jornada/${idJornada}`);
}
export async function getRolTurnoByTitularJornada(idTitularJornada: number) {
  return requestClient.get<RolTurnoDto[]>(`/api/v1/rol-turno/titular-jornada/${idTitularJornada}`);
}
export async function eliminarRolTurno(id: number) {
  return requestClient.delete<void>(`/api/v1/rol-turno/${id}`);
}

// ── SEGUIMIENTO POR DÍAS ───────────────────────────────────────────────────

export interface AusenciaJornadaDto {
  idAusencia:       number;
  idUsuario:        number;
  nombrePersonal:   string;
  inicioAusencia:   string;
  finAusencia:      null | string;
  resuelta:         boolean;
  registradoPor:    null | string;
}

export async function getSesionesJornada(idJornada: number) {
  return requestClient.get<SesionMonitoreoDto[]>(`/api/v1/monitoreo/sesiones/jornada/${idJornada}`);
}

export async function getAusenciasJornada(idJornada: number) {
  return requestClient.get<AusenciaJornadaDto[]>(`/api/v1/monitoreo/ausencias/jornada/${idJornada}`);
}

export async function getIncidenciasJornada(idJornada: number) {
  return requestClient.get<EventoAbandonoDto[]>(`/api/v1/monitoreo/incidencias/jornada/${idJornada}`);
}