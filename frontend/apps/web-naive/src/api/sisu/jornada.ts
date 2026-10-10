// apps/web-naive/src/api/sisu/jornadas.ts
import { requestClient } from '#/api/request';

// ══ Tipos ══════════════════════════════════════════════════════════════════

export type TipoJornada =
  | 'ADMINISTRATIVA'  // Personal admin/apoyo con horario fijo (puede ser PARTIDA)
  | 'COBERTURA'       // Cobertura por ausencia
  | 'EMERGENCIA'      // Respuesta urgente
  | 'GUARDIA'        // ← legacy (backward compat)
  | 'GUARDIA_12H'     // Guardia continua de 12 horas
  | 'GUARDIA_24H'     // Guardia continua de 24 horas
  | 'LLAMADA'         // Guardia de localización / on-call
  | 'NOCTURNA'        // Turno nocturno (ej: 19:00-07:00)
  | 'REGULAR'         // Jornada diaria estándar
  | 'ROTACION';        // Rotación médica (MIR / internos)

export type ModalidadHorario = 'CONTINUA' | 'PARTIDA';

export type EstadoJornada    = 'ACTIVA'  | 'CANCELADA' | 'CERRADA';
export type EstadoAsignacion = 'CANCELADO' | 'COMPLETADO' | 'CONFIRMADO' | 'EN_CURSO' | 'PROGRAMADO';

export interface JornadaDto {
  idJornada:       number;
  nombre:          null | string;
  fechaInicio:     string;   // yyyy-MM-dd
  fechaFin:        string;
  horaInicio:      string;   // HH:mm:ss
  horaFin:         string;
  modalidad?:      ModalidadHorario;
  horaInicioTarde?: null | string;
  horaFinTarde?:   null | string;
  tipoJornada:     TipoJornada;
  estado:          EstadoJornada;
  descripcion:     null | string;
  duracionHoras:   number;
  estaEnCurso:     boolean;
  totalTitulares:  number;
  totalInternos:   number;
  idLugar:         null | number;
  nombreLugar:     null | string;
  diasDefault?:    null | string;  // CSV "2025-05-01,2025-05-04,..." — días específicos del turno
  /** Control GPS suspendido: el turno corre pero no penaliza a nadie. */
  monitoreoPausado?: boolean;
}

export interface JornadaRequest {
  nombre?:          string;
  fechaInicio:      string;
  fechaFin:         string;
  horaInicio:       string;    // HH:mm:ss
  horaFin:          string;
  modalidad?:       ModalidadHorario;
  horaInicioTarde?: string;    // solo si modalidad=PARTIDA
  horaFinTarde?:    string;
  tipoJornada?:     TipoJornada;
  descripcion?:     string;
  idLugar?:         number;
  diasDefault?:     string;  // CSV de fechas ISO, ej: "2025-05-01,2025-05-04,..."
}

export interface TitularJornadaDto {
  idTitularJornada: number;
  idTitular:        number;
  nombreCompleto:   string;
  cargo:            null | string;
  especialidad:     null | string;
  idLugar:          null | number;
  nombreLugar:      null | string;
  estado:           EstadoAsignacion;
  observaciones:    null | string;
  horaInicioOverride: null | string;
  horaFinOverride:    null | string;
  horaInicioEfectiva: string;
  horaFinEfectiva:    string;
  diasTrabajoJson:    null | string;

  // ── Franja de tarde (jornadas PARTIDAS / administrativas) ────────────────
  horaInicioTardeOverride: null | string;
  horaFinTardeOverride:    null | string;
  /** Ya resueltas por el backend: override del titular, o las de la jornada. */
  horaInicioTardeEfectiva: null | string;
  horaFinTardeEfectiva:    null | string;
  /** Qué franjas cubre. Solo significativas si `jornadaPartida`. */
  trabajaManana:  boolean;
  trabajaTarde:   boolean;
  jornadaPartida: boolean;
}

export interface InternoJornadaDto {
  idInternoJornada:     number;
  idInterno:            number;
  nombreCompleto:       string;
  carrera:              null | string;
  universidad:          null | string;
  estaEnRotacion:       boolean;
  idLugar:              null | number;
  nombreLugar:          null | string;
  idResponsableInterno: null | number;
  nombreResponsable:    null | string;
  estado:               EstadoAsignacion;
  adjuntoUrl:           null | string;
  observaciones:        null | string;
  horaInicioOverride:   null | string;
  horaFinOverride:      null | string;
  horaInicioEfectiva:   string;
  horaFinEfectiva:      string;
}

export interface TitularSelectDto {
  idTitular:      number;
  nombreCompleto: string;
  cargo:          null | string;
}

export interface InternoSelectDto {
  idInterno:      number;
  nombreCompleto: string;
  carrera:        null | string;
  universidad:    null | string;
  estaEnRotacion: boolean;
}

export interface LugarSelectDto {
  idLugar:     number;
  nombre:      string;
  descripcion: null | string;
}

/**
 * Override de horario de una persona dentro de una jornada.
 *
 * Las horas en null/ausentes significan "hereda de la jornada"; por eso las
 * franjas se activan con los flags y no dejando las horas vacías. Los campos de
 * tarde solo aplican cuando la jornada es PARTIDA (tipo Administrativa).
 */
export interface HorarioIndividual {
  horaInicio:       string;  // HH:mm:ss — franja mañana (o única si es CONTINUA)
  horaFin:          string;
  horaInicioTarde?: null | string;
  horaFinTarde?:    null | string;
  trabajaManana?:   boolean;
  trabajaTarde?:    boolean;
}

export interface AsignarTitularRequest {
  idTitulares:    number[];
  idLugar?:       number;
  observaciones?: string;
  horariosIndividuales?: Record<number, HorarioIndividual>;
}

export interface AsignarInternoRequest {
  idInternos:     number[];
  idLugar?:       number;
  observaciones?: string;
  horariosIndividuales?: Record<number, HorarioIndividual>;
}

export interface CambiarEstadoRequest {
  estado:         EstadoAsignacion;
  observaciones?: string;
}

// ══ Helper de paginación (mismo patrón que personal.ts) ═══════════════════

export function toVxePage<T>(raw: any) {
  const meta = (raw as any).page ?? raw;
  return { items: (raw.content ?? []) as T[], total: (meta.totalElements ?? 0) as number };
}

export interface JornadaParams {
  page:      number;
  size:      number;
  q?:        string;
  estado?:   EstadoJornada;
  tipo?:     TipoJornada;
  fechaIni?: string;
  fechaFin?: string;
}

// ══ Funciones API ══════════════════════════════════════════════════════════

// ══ API Functions ══════════════════════════════════════════════════════════

export async function getJornadas(p: JornadaParams) {
  const raw = await requestClient.get<any>('/api/v1/jornadas', { params: p });
  return toVxePage<JornadaDto>(raw);
}
export async function getJornadasActivas() {
  return requestClient.get<JornadaDto[]>('/api/v1/jornadas/activas');
}
export async function createJornada(data: JornadaRequest) {
  return requestClient.post<JornadaDto>('/api/v1/jornadas', data);
}
export async function updateJornada(id: number, data: JornadaRequest) {
  return requestClient.put<JornadaDto>(`/api/v1/jornadas/${id}`, data);
}
export async function cerrarJornada(id: number) {
  return requestClient.post<JornadaDto>(`/api/v1/jornadas/${id}/cerrar`, {});
}
export async function cancelarJornada(id: number) {
  return requestClient.post<JornadaDto>(`/api/v1/jornadas/${id}/cancelar`, {});
}

/**
 * Detiene (`pausado: true`) o reanuda el control GPS de un turno.
 *
 * No cambia el horario ni las asignaciones: mientras está detenido no se abren
 * sesiones ni se registran atrasos, faltas o abandonos, y nadie recibe avisos
 * de ubicación. Al reanudar, el sistema vuelve a evaluar a quien esté dentro de
 * su horario.
 */
export async function cambiarMonitoreoJornada(id: number, pausado: boolean) {
  return requestClient.put<JornadaDto>(`/api/v1/jornadas/${id}/monitoreo`, { pausado });
}

export async function getTitularesJornada(id: number) {
  return requestClient.get<TitularJornadaDto[]>(`/api/v1/jornadas/${id}/titulares`);
}
export async function asignarTitulares(id: number, data: AsignarTitularRequest) {
  return requestClient.post<TitularJornadaDto[]>(`/api/v1/jornadas/${id}/titulares`, data);
}
/**
 * Cambia el horario de un titular YA asignado, sin quitarlo de la jornada.
 * Antes había que removerlo y reasignarlo, lo que arrastra sus actividades.
 */
export async function actualizarHorarioTitular(
  id: number,
  idTJ: number,
  data: HorarioIndividual,
) {
  return requestClient.put<TitularJornadaDto>(
    `/api/v1/jornadas/${id}/titulares/${idTJ}/horario`,
    data,
  );
}
export async function cambiarEstadoTitular(id: number, idTJ: number, data: CambiarEstadoRequest) {
  return requestClient.post<TitularJornadaDto>(`/api/v1/jornadas/${id}/titulares/${idTJ}/estado`, data);
}
export async function removerTitular(id: number, idTJ: number) {
  return requestClient.delete<null>(`/api/v1/jornadas/${id}/titulares/${idTJ}`);
}

export async function getInternosJornada(id: number) {
  return requestClient.get<InternoJornadaDto[]>(`/api/v1/jornadas/${id}/internos`);
}
export async function asignarInternos(id: number, data: AsignarInternoRequest) {
  return requestClient.post<InternoJornadaDto[]>(`/api/v1/jornadas/${id}/internos`, data);
}
export async function cambiarEstadoInterno(id: number, idIJ: number, data: CambiarEstadoRequest) {
  return requestClient.post<InternoJornadaDto>(`/api/v1/jornadas/${id}/internos/${idIJ}/estado`, data);
}
export async function removerInterno(id: number, idIJ: number) {
  return requestClient.delete<null>(`/api/v1/jornadas/${id}/internos/${idIJ}`);
}

export async function getTitularesDisponibles() {
  return requestClient.get<TitularSelectDto[]>('/api/v1/jornadas/disponibles/titulares');
}
export async function getInternosDisponibles() {
  return requestClient.get<InternoSelectDto[]>('/api/v1/jornadas/disponibles/internos');
}
export async function getLugaresDisponibles() {
  return requestClient.get<LugarSelectDto[]>('/api/v1/jornadas/disponibles/lugares');
}

// ══ HorarioInterno ═════════════════════════════════════════════════════════

export interface DiaHorarioDto {
  idDiaHorario: number;
  fecha:        string;            // yyyy-MM-dd
  horaInicio:   null | string;     // HH:mm (null = usa hora de la jornada)
  horaFin:      null | string;     // HH:mm
  observaciones: null | string;
}

export interface HorarioInternoDto {
  idHorarioInterno:  number;
  idInternoJornada:  number;
  idJornada:         number;
  nombreJornada:     null | string;
  idInterno:         number;
  nombreInterno:     string;
  carrera:           null | string;
  mes:               number;
  anio:              number;
  observaciones:     null | string;
  activo:            boolean;
  dias:              DiaHorarioDto[];
  horaInicioJornada: null | string;
  horaFinJornada:    null | string;
}

export interface DiaHorarioRequest {
  fecha:        string;            // yyyy-MM-dd
  horaInicio?:  string;            // HH:mm (opcional)
  horaFin?:     string;            // HH:mm (opcional)
  observaciones?: string;
}

export interface HorarioInternoRequest {
  idInternoJornada: number;
  mes:              number;
  anio:             number;
  observaciones?:   string;
  dias:             DiaHorarioRequest[];
}

export async function guardarHorarioInterno(data: HorarioInternoRequest) {
  return requestClient.post<HorarioInternoDto>('/api/v1/horario-interno', data);
}
export async function getHorarioInternoByInternoJornada(idInternoJornada: number) {
  return requestClient.get<HorarioInternoDto[]>(`/api/v1/horario-interno/interno-jornada/${idInternoJornada}`);
}
export async function eliminarHorarioInterno(id: number) {
  return requestClient.delete<null>(`/api/v1/horario-interno/${id}`);
}

export async function getJornadasMes(year: number, mes: number) {
  const pad = (n: number) => String(n).padStart(2, '0');
  const lastDay = new Date(year, mes, 0).getDate();
  const raw = await requestClient.get<any>('/api/v1/jornadas', {
    params: {
      page: 0, size: 200,
      fechaIni: `${year}-${pad(mes)}-01`,
      fechaFin: `${year}-${pad(mes)}-${lastDay}`,
    },
  });
  return toVxePage<JornadaDto>(raw).items;
}

// ══ Historial de Cambios ════════════════════════════════════════════════════

export interface HistorialCambio {
  idHistorial:  number;
  entidad:      string;
  idEntidad:    number;
  campo:        string;
  valorAnterior: null | string;
  valorNuevo:    null | string;
  accion:       string;
  usuario:      string;
  fechaCambio:  string;
}

export function getHistorial(entidad: string, idEntidad: number) {
  return requestClient.get<HistorialCambio[]>(`/api/v1/historial/${entidad}/${idEntidad}`);
}

// ══ Reemplazos de Turno ════════════════════════════════════════════════════

export interface ReemplazoTurno {
  idReemplazo:         number;
  idJornada:           number;
  fechaReemplazo:      string;
  idOriginal:          number;
  nombreOriginal:      string;
  idReemplazante:      number;
  nombreReemplazo:     string;
  motivo:              string;
  estado:              string;
  autorizadoPorNombre: null | string;
  fechaAutorizacion:   null | string;
}

export interface ReemplazoTurnoRequest {
  idJornada:      number;
  fechaReemplazo: string;
  idOriginal:     number;
  idReemplazo:    number;
  motivo:         string;
}

export function getReemplazosPorJornada(idJornada: number) {
  return requestClient.get<ReemplazoTurno[]>(`/api/v1/reemplazos/jornada/${idJornada}`);
}

export function crearReemplazo(data: ReemplazoTurnoRequest) {
  return requestClient.post<ReemplazoTurno>('/api/v1/reemplazos', data);
}

export function aprobarReemplazo(id: number) {
  return requestClient.put<ReemplazoTurno>(`/api/v1/reemplazos/${id}/aprobar`);
}

export function rechazarReemplazo(id: number) {
  return requestClient.put<ReemplazoTurno>(`/api/v1/reemplazos/${id}/rechazar`);
}

export function eliminarReemplazo(id: number) {
  return requestClient.delete<null>(`/api/v1/reemplazos/${id}`);
}
