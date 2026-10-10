// Lógica compartida de la vista Vigilancia en Vivo.
//
// Punto único de verdad para "¿este turno está corriendo ahora?". Antes la
// respuesta estaba partida en dos mitades incompatibles: el backend
// (Jornada.estaEnHorarioAhora) resuelve horario PARTIDA y cruce de medianoche
// pero ignora diasDefault; el frontend miraba diasDefault pero trataba el
// horario como una ventana continua del día de hoy, así que una guardia
// 20:00–08:00 desaparecía de la vista a partir de medianoche — justo el turno
// que más importa vigilar. Acá se aplican las dos reglas juntas.

import type { InternoJornadaDto, JornadaDto, TitularJornadaDto } from '#/api/sisu/jornada';
import type { SesionMonitoreoDto } from '#/api/sisu/monitoreo';

// ── Umbrales ───────────────────────────────────────────────────────────────

/** Una sesión se considera "con señal" si envió GPS hace menos de esto. */
export const STALE_THRESHOLD_MIN = 4;

/** Un turno entra en POR_CERRAR cuando le queda menos de esto. */
export const POR_CERRAR_MIN = 30;

// ── Estado de presencia de una persona ─────────────────────────────────────

// EN_ZONA   → logueado + GPS activo + dentro del lugar
// AUSENTE   → logueado + GPS activo + FUERA del lugar (abandono detectado)
// SIN_SENAL → en su horario pero sin GPS / no logueado (no verificable)
export type EstadoPresencia =
  | 'AUSENTE'
  | 'EN_ZONA'
  | 'NO_HOY'
  | 'POR_EMPEZAR'
  | 'SIN_CONTROL'
  | 'SIN_SENAL'
  | 'TURNO_FINALIZADO';

export const ESTADO_META: Record<EstadoPresencia, {
  color: string;
  corto: string;
  label: string;
  type: 'default' | 'error' | 'info' | 'success' | 'warning';
}> = {
  AUSENTE:          { label: '🔴 Ausente — fuera del lugar',  corto: 'Ausente',    type: 'error',   color: '#e03131' },
  SIN_SENAL:        { label: '📴 Sin señal — no verificable', corto: 'Sin señal',  type: 'warning', color: '#f08c00' },
  EN_ZONA:          { label: '✅ En zona (GPS activo)',       corto: 'En zona',    type: 'success', color: '#18a058' },
  POR_EMPEZAR:      { label: '⏳ Por empezar',                corto: 'Por empezar', type: 'info',    color: '#1c7ed6' },
  SIN_CONTROL:      { label: '⏸ Control detenido',            corto: 'Sin control', type: 'default', color: '#a78bfa' },
  TURNO_FINALIZADO: { label: '✔ Turno finalizado',            corto: 'Finalizado', type: 'default', color: '#94a3b8' },
  NO_HOY:           { label: '📅 No le toca hoy',             corto: 'No hoy',     type: 'default', color: '#94a3b8' },
};

/** Orden de severidad: lo que necesita atención primero. */
export const ORDEN_PRESENCIA: Record<EstadoPresencia, number> = {
  AUSENTE: 0, SIN_SENAL: 1, EN_ZONA: 2,
  POR_EMPEZAR: 3, TURNO_FINALIZADO: 4, SIN_CONTROL: 5, NO_HOY: 6,
};

// ── Estado de un turno ─────────────────────────────────────────────────────

export type EstadoTurno =
  | 'AYER'
  | 'EN_CURSO'
  | 'FINALIZADO'
  | 'NO_PROGRAMADO'
  | 'PAUSADO'
  | 'POR_CERRAR'
  | 'PROXIMO';

export const TURNO_META: Record<EstadoTurno, {
  /** Si arranca expandido en el acordeón. */
  abierto: boolean;
  color: string;
  label: string;
  /** Orden de aparición en la lista. */
  orden: number;
}> = {
  EN_CURSO:      { label: 'EN CURSO',        color: '#18a058', orden: 0, abierto: true  },
  POR_CERRAR:    { label: 'POR CERRAR',      color: '#f08c00', orden: 1, abierto: true  },
  PROXIMO:       { label: 'PRÓXIMO',         color: '#1c7ed6', orden: 2, abierto: false },
  FINALIZADO:    { label: 'FINALIZADO HOY',  color: '#64748b', orden: 3, abierto: false },
  AYER:          { label: 'TURNO ANTERIOR',  color: '#7048e8', orden: 4, abierto: false },
  PAUSADO:       { label: 'CONTROL DETENIDO', color: '#a78bfa', orden: 5, abierto: false },
  NO_PROGRAMADO: { label: 'SIN ACTIVIDAD',   color: '#94a3b8', orden: 6, abierto: false },
};

// ── Modelos de fila ────────────────────────────────────────────────────────

export interface PersonalRow {
  cargo:            null | string;
  estado:           EstadoPresencia;
  horaFin:          null | string;
  horaInicio:       null | string;
  /** Id de la asignación (TitularJornada / InternoJornada), no de la persona. */
  idAsignacion:     null | number;
  idPersona:        number;
  nombreCompleto:   string;
  sesion:           null | SesionMonitoreoDto;
  tipo:             'INTERNO' | 'TITULAR';
  turnoHoy:         boolean;
}

/**
 * A quién mira la ficha lateral. Vive acá y no en el componente porque un
 * `<script setup>` no puede exportar tipos: ambas vistas que abren la ficha
 * normalizan sus filas a esta forma.
 */
export interface FichaTarget {
  cargo:          null | string;
  /** Id de TitularJornada / InternoJornada, si el llamador lo conoce. */
  idAsignacion?:  null | number;
  idJornada?:     null | number;
  idPersona:      number;
  nombreCompleto: string;
  nombreJornada?: null | string;
  nombreLugar?:   null | string;
  sesion:         null | SesionMonitoreoDto;
  tipo:           'INTERNO' | 'TITULAR';
}

export interface TurnoGroup {
  estado:      EstadoTurno;
  /** Intervalos concretos que este turno abre entre ayer y mañana. */
  intervalos:  Intervalo[];
  jornada:     JornadaDto;
  personal:    PersonalRow[];
  /** Intervalo relevante para mostrar (el que corre, o el próximo, o el último). */
  referencia:  Intervalo | null;
  stats: {
    ausentes:   number;
    enZona:     number;
    noHoy:      number;
    sinSenal:   number;
    total:      number;
  };
}

/** Una ventana concreta de trabajo, ya resuelta a fechas absolutas. */
export interface Intervalo {
  /** Fecha programada a la que pertenece (yyyy-MM-dd), no necesariamente la de inicio. */
  diaPrograma: string;
  fin:         Date;
  inicio:      Date;
  /** true si es la franja de tarde de un horario PARTIDA. */
  tarde:       boolean;
}

// ── Helpers de fecha / hora ────────────────────────────────────────────────

export function fmtHora(h: null | string | undefined): null | string {
  return h ? String(h).slice(0, 5) : null;
}

export function parseMinutos(h: null | string | undefined): null | number {
  if (!h) return null;
  const partes = h.slice(0, 5).split(':').map(Number);
  return (partes[0] ?? 0) * 60 + (partes[1] ?? 0);
}

export function isoFecha(d: Date): string {
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${mm}-${dd}`;
}

/** Combina una fecha "yyyy-MM-dd" con una hora "HH:mm[:ss]" en hora local. */
function combinar(fecha: string, hora: string): Date {
  const [a, m, d] = fecha.split('-').map(Number);
  const hm = hora.slice(0, 5).split(':').map(Number);
  return new Date(a ?? 0, (m ?? 1) - 1, d ?? 1, hm[0] ?? 0, hm[1] ?? 0, 0, 0);
}

export function fmtHoraCorta(d: Date): string {
  return d.toLocaleTimeString('es', { hour: '2-digit', minute: '2-digit' });
}

/** "2h 15m" / "45m" — duración legible desde minutos. */
export function fmtDuracion(min: number): string {
  const m = Math.max(0, Math.round(min));
  if (m < 60) return `${m}m`;
  const h = Math.floor(m / 60);
  const r = m % 60;
  return r === 0 ? `${h}h` : `${h}h ${r}m`;
}

export function minutosDesde(a: Date, b: Date): number {
  return (b.getTime() - a.getTime()) / 60_000;
}

// ── Cálculo de intervalos de una jornada ───────────────────────────────────

/**
 * Ventana de trabajo propia de una persona dentro del turno.
 *
 * Una jornada es un contenedor: su horario es solo el valor por defecto. Tres
 * personas del mismo turno pueden hacer 08–20, 08–14 y 14–20, y cada una tiene
 * que clasificarse contra la suya. Todo en null → sigue el horario del turno.
 */
export interface HorarioPersonal {
  /** CSV de días propios; null → los días del turno. */
  dias:            null | string;
  horaFin:         null | string;
  horaFinTarde:    null | string;
  horaInicio:      null | string;
  horaInicioTarde: null | string;
  trabajaManana:   boolean;
  trabajaTarde:    boolean;
}

/**
 * Extrae la ventana propia de una asignación.
 *
 * Los DTOs ya traen las horas "efectivas" resueltas por el backend (override de
 * la persona, o las de la jornada), así que acá no se repite esa precedencia.
 * Los internos no exponen franja de tarde ni días propios: quedan en null y
 * heredan los del turno.
 */
export function horarioPersonal(
  a: InternoJornadaDto | TitularJornadaDto,
): HorarioPersonal {
  const t = 'idTitularJornada' in a ? a : null;
  return {
    horaInicio:      a.horaInicioEfectiva ?? null,
    horaFin:         a.horaFinEfectiva    ?? null,
    horaInicioTarde: t?.horaInicioTardeEfectiva ?? null,
    horaFinTarde:    t?.horaFinTardeEfectiva    ?? null,
    trabajaManana:   t?.trabajaManana ?? true,
    trabajaTarde:    t?.trabajaTarde  ?? true,
    dias:            t?.diasTrabajoJson ?? null,
  };
}

/** Días explícitamente programados (CSV de fechas ISO), o null si aplica a todo el rango. */
function diasProgramados(csv: null | string | undefined): null | Set<string> {
  if (!csv || csv.trim() === '') return null;
  const set = new Set(
    csv.split(',').map(s => s.trim()).filter(Boolean),
  );
  return set.size > 0 ? set : null;
}

function estaProgramado(j: JornadaDto, fecha: string, dias: null | Set<string>): boolean {
  if (fecha < j.fechaInicio || fecha > j.fechaFin) return false;
  return dias ? dias.has(fecha) : true;
}

/**
 * Resuelve todas las ventanas de trabajo que se abren alrededor de ahora.
 *
 * Mira ayer, hoy y mañana: ayer porque un turno nocturno programado ayer sigue
 * corriendo de madrugada, y mañana para poder anunciar el próximo turno cuando
 * ya pasó el de hoy.
 *
 * `personal` acota el cálculo a la ventana de una persona concreta. Sin él se
 * usa el horario del turno, que es solo el valor por defecto: quien tenga
 * horario propio quedaba clasificado contra un horario que no es el suyo.
 */
export function calcularIntervalos(
  j: JornadaDto,
  ahora: Date,
  personal?: HorarioPersonal | null,
): Intervalo[] {
  const hIni = personal?.horaInicio ?? j.horaInicio;
  const hFin = personal?.horaFin    ?? j.horaFin;
  if (!hIni || !hFin) return [];

  const hIniTarde = personal?.horaInicioTarde ?? j.horaInicioTarde ?? null;
  const hFinTarde = personal?.horaFinTarde    ?? j.horaFinTarde    ?? null;
  const partida   = j.modalidad === 'PARTIDA' && !!hIniTarde && !!hFinTarde;

  // Los flags de franja solo existen en PARTIDA; en CONTINUA hay una sola
  // ventana y siempre se cubre.
  const haceManana = !partida || (personal?.trabajaManana ?? true);
  const haceTarde  =  partida && (personal?.trabajaTarde  ?? true);

  const dias = diasProgramados(personal?.dias ?? j.diasDefault);
  const out: Intervalo[] = [];

  for (const offset of [-1, 0, 1]) {
    const d = new Date(ahora.getFullYear(), ahora.getMonth(), ahora.getDate() + offset);
    const fecha = isoFecha(d);
    if (!estaProgramado(j, fecha, dias)) continue;

    // Franja principal (mañana si es partida).
    if (haceManana) {
      const ini = combinar(fecha, hIni);
      let fin   = combinar(fecha, hFin);
      // Horario continuo que cruza medianoche: termina al día siguiente.
      // En PARTIDA las dos franjas caen dentro del mismo día por definición.
      if (!partida && fin <= ini) fin = new Date(fin.getTime() + 86_400_000);
      out.push({ inicio: ini, fin, diaPrograma: fecha, tarde: false });
    }

    if (haceTarde) {
      out.push({
        inicio:      combinar(fecha, hIniTarde!),
        fin:         combinar(fecha, hFinTarde!),
        diaPrograma: fecha,
        tarde:       true,
      });
    }
  }

  return out.toSorted((a, b) => a.inicio.getTime() - b.inicio.getTime());
}

/**
 * Clasifica el turno según dónde cae "ahora" respecto de sus intervalos.
 *
 * Devuelve también el intervalo de referencia: el que corre si está en curso,
 * si no el próximo que empieza, si no el último que terminó. Es lo que la card
 * muestra como horario y lo que alimenta la barra de progreso.
 */
export function clasificarTurno(
  j: JornadaDto,
  intervalos: Intervalo[],
  ahora: Date,
): { estado: EstadoTurno; referencia: Intervalo | null } {
  if (j.estado !== 'ACTIVA' || intervalos.length === 0) {
    return { estado: 'NO_PROGRAMADO', referencia: intervalos[0] ?? null };
  }

  // Control detenido: el turno sigue programado y se muestra con su horario, pero
  // el backend no abre sesiones ni penaliza, así que la vista no debe pintar a su
  // gente como ausente o sin señal — no hay nada que verificar.
  if (j.monitoreoPausado) {
    const vigente = intervalos.find(i => i.inicio <= ahora && ahora <= i.fin);
    return { estado: 'PAUSADO', referencia: vigente ?? intervalos[0] ?? null };
  }

  // Entre las ventanas que contienen a "ahora" manda la que termina más tarde.
  // Cuando estos intervalos son la unión de un grupo con horarios distintos, el
  // turno sigue abierto hasta que sale el último: con `find` a secas, quien
  // cerraba a las 14:00 marcaba POR_CERRAR todo el turno mientras el resto
  // seguía hasta las 20:00. Para una sola persona hay a lo sumo una ventana
  // vigente, así que no cambia nada.
  const enCurso = intervalos
    .filter(i => i.inicio <= ahora && ahora <= i.fin)
    .toSorted((a, b) => b.fin.getTime() - a.fin.getTime())[0];
  if (enCurso) {
    const restan = minutosDesde(ahora, enCurso.fin);
    return {
      estado:     restan <= POR_CERRAR_MIN ? 'POR_CERRAR' : 'EN_CURSO',
      referencia: enCurso,
    };
  }

  const hoy = isoFecha(ahora);

  // Próximo que empieza hoy (no anunciamos el de mañana como "próximo": eso es
  // otro día y confunde al operador que vigila el turno actual).
  const proximo = intervalos.find(i => i.inicio > ahora && isoFecha(i.inicio) === hoy);
  if (proximo) return { estado: 'PROXIMO', referencia: proximo };

  // Ya terminó: el último intervalo cerrado manda.
  const cerrados = intervalos.filter(i => i.fin < ahora);
  const ultimo   = cerrados.at(-1);
  if (ultimo) {
    // Arrancó ayer → es el turno anterior, aunque haya cerrado esta madrugada.
    const estado: EstadoTurno = ultimo.diaPrograma < hoy ? 'AYER' : 'FINALIZADO';
    return { estado, referencia: ultimo };
  }

  return { estado: 'NO_PROGRAMADO', referencia: intervalos[0] ?? null };
}

/** Progreso 0–100 dentro del intervalo de referencia. */
export function progresoTurno(ref: Intervalo | null, ahora: Date): number {
  if (!ref) return 0;
  const total = minutosDesde(ref.inicio, ref.fin);
  if (total <= 0) return 0;
  const hecho = minutosDesde(ref.inicio, ahora);
  return Math.min(100, Math.max(0, Math.round((hecho / total) * 100)));
}

// ── Estado de presencia de una persona ─────────────────────────────────────

export function esSesionVigente(sesion: SesionMonitoreoDto): boolean {
  if (!sesion.ultimaActualizacion) return false;
  if (sesion.estadoZona === 'DESCONOCIDO') return false;
  const sinSenal = (Date.now() - new Date(sesion.ultimaActualizacion).getTime()) / 60_000;
  return sinSenal <= STALE_THRESHOLD_MIN;
}

/** Minutos desde la última señal GPS, o null si nunca envió. */
export function minutosSinSenal(sesion: null | SesionMonitoreoDto): null | number {
  if (!sesion?.ultimaActualizacion) return null;
  return Math.round((Date.now() - new Date(sesion.ultimaActualizacion).getTime()) / 60_000);
}

/**
 * Estado de una persona dentro de su turno.
 *
 * El estado del turno entra como parámetro en vez de recalcularse acá: la
 * persona no puede estar "por empezar" si su turno ya cerró, y derivarlo de la
 * ventana horaria propia producía combinaciones imposibles en los nocturnos.
 */
export function computarPresencia(
  turnoHoy:      boolean,
  sesion:        null | SesionMonitoreoDto,
  estadoTurno:   EstadoTurno,
): EstadoPresencia {
  // 0. Control detenido: no se penaliza ni se exige ubicación. Va antes que la
  //    señal GPS porque aunque siga llegando un fix, "fuera de zona" no debe
  //    pintarse como ausencia mientras el turno esté sin control.
  if (estadoTurno === 'PAUSADO') return 'SIN_CONTROL';

  // 1. Señal GPS reciente y válida → la ubicación manda sobre todo lo demás.
  if (sesion && esSesionVigente(sesion)) {
    return sesion.estadoZona === 'FUERA' ? 'AUSENTE' : 'EN_ZONA';
  }

  // 2. Sin señal vigente: no logueado, GPS apagado o señal perdida.
  if (!turnoHoy) return 'NO_HOY';

  switch (estadoTurno) {
    case 'AYER':
    case 'FINALIZADO': {
      return 'TURNO_FINALIZADO';
    }
    case 'NO_PROGRAMADO': {
      return 'NO_HOY';
    }
    case 'PROXIMO': {
      return 'POR_EMPEZAR';
    }
    // EN_CURSO / POR_CERRAR: debería estar trabajando y no se puede verificar.
    default: {
      return 'SIN_SENAL';
    }
  }
}
