/**
 * useTurnoHoy — estado compartido (singleton) que responde a una pregunta clave:
 * ¿el funcionario tiene turno HOY?
 *
 * El backend define los días de turno (ROL TURNO). Una jornada puede estar
 * "ACTIVA" (asignación vigente en un rango de fechas) sin que hoy sea un día de
 * trabajo. Por eso no alcanza con `getMiJornada()`: hay que mirar el calendario.
 *
 * Esta composable lee `getMisTurnos()` del mes actual y deriva:
 *   - turnoHoy      → el turno de hoy, o null si hoy es día libre
 *   - proximoTurno  → el siguiente día de turno (este mes o el siguiente)
 *   - tieneTurnoHoy → boolean de conveniencia
 *
 * El estado es module-level para que app.vue (layout), home y turnos compartan
 * la misma verdad sin recargar tres veces.
 */
import { computed, ref } from 'vue';

import { type DiaTurnoCalendarioDto, getMisTurnos } from '#/api/sisu/app';

const turnoHoy     = ref<DiaTurnoCalendarioDto | null>(null);
const proximoTurno = ref<DiaTurnoCalendarioDto | null>(null);
const cargado      = ref(false);
const cargando     = ref(false);
/** Sube en cada carga exitosa del turno. Ver el incremento en cargarTurnoHoy(). */
const versionTurno = ref(0);

/**
 * Reloj reactivo. Sin esto, `enHorarioTurno` y `faseTurnoHoy` sólo se recalculaban
 * cuando cambiaba algún dato: al dar la hora de fin, la app seguía mostrando "en
 * turno" y el GPS seguía corriendo hasta que algo más forzara un recálculo. Un
 * computed no reacciona al paso del tiempo por sí solo.
 *
 * 30 s de resolución: suficiente para que el cambio se sienta inmediato sin
 * despertar la interfaz constantemente.
 */
const ahora = ref(Date.now());
let relojTimer: null | ReturnType<typeof setInterval> = null;

function arrancarReloj() {
  if (relojTimer) return;
  relojTimer = setInterval(() => { ahora.value = Date.now(); }, 30_000);
}

/**
 * Horario de respaldo tomado de `/app/mi-jornada`.
 *
 * El calendario (`getMisTurnos`) es la fuente rica —sabe de modalidad PARTIDA— pero
 * si no responde o no trae el día de hoy, todo caía a "estado desconocido" y la
 * pantalla se quedaba en el modo activo por seguridad. La jornada del día viene por
 * otro endpoint y sí carga; usarla como respaldo evita ese punto ciego.
 */
const horarioRespaldo = ref<null | { horaFin: string; horaInicio: string }>(null);

/** La registra el Home al cargar su jornada del día. Pasar null la limpia. */
export function registrarHorarioJornada(
  h: null | { horaFin: null | string; horaInicio: null | string },
): void {
  horarioRespaldo.value = h?.horaInicio && h?.horaFin
    ? { horaInicio: h.horaInicio, horaFin: h.horaFin }
    : null;
}

/** Minutos desde medianoche, o null si la cadena no es una hora válida. */
function aMinutos(s: null | string): null | number {
  if (!s) return null;
  const partes = s.split(':');
  const h = Number(partes[0]);
  const m = Number(partes[1]);
  return Number.isNaN(h) || Number.isNaN(m) ? null : h * 60 + m;
}

export function useTurnoHoy() {
  arrancarReloj();

  /**
   * Ventana horaria efectiva de hoy: la del calendario si está, si no la de la
   * jornada. Null cuando no hay ninguna de las dos.
   */
  const horarioEfectivo = computed(() => {
    const t = turnoHoy.value;
    if (t) {
      return {
        ini:      aMinutos(t.horaInicio),
        fin:      aMinutos(t.horaFin),
        iniTarde: t.modalidad === 'PARTIDA' ? aMinutos(t.horaInicioTarde) : null,
        finTarde: t.modalidad === 'PARTIDA' ? aMinutos(t.horaFinTarde)    : null,
        textoInicio: t.horaInicio,
        textoFin:    t.modalidad === 'PARTIDA' ? (t.horaFinTarde ?? t.horaFin) : t.horaFin,
      };
    }
    const r = horarioRespaldo.value;
    if (r) {
      return {
        ini: aMinutos(r.horaInicio), fin: aMinutos(r.horaFin),
        iniTarde: null, finTarde: null,
        textoInicio: r.horaInicio, textoFin: r.horaFin,
      };
    }
    return null;
  });

  /** ¿Hoy corresponde turno? Considera calendario y respaldo. */
  const tieneTurnoHoy = computed(
    () => turnoHoy.value !== null || horarioRespaldo.value !== null,
  );

  /**
   * ¿Estamos dentro del horario de trabajo ahora?
   * Compara la hora actual contra horaInicio–horaFin del turno de hoy.
   * Soporta modalidad PARTIDA (ventana mañana + tarde) y turnos nocturnos
   * que cruzan medianoche.
   */
  const enHorarioTurno = computed(() => {
    const h = horarioEfectivo.value;
    if (!h || h.ini == null || h.fin == null) return false;

    // Depender de `ahora` es lo que hace que esto se recalcule con el paso del
    // tiempo y no sólo cuando cambian los datos.
    const now = new Date(ahora.value);
    const curMin = now.getHours() * 60 + now.getMinutes();

    const enVentana = (ini: number, fin: number) =>
      ini <= fin
        ? curMin >= ini && curMin <= fin       // mismo día
        : curMin >= ini || curMin <= fin;      // cruza medianoche

    // Ventana principal (CONTINUA o mañana de PARTIDA)
    if (enVentana(h.ini, h.fin)) return true;

    // Tarde de PARTIDA
    if (h.iniTarde != null && h.finTarde != null && enVentana(h.iniTarde, h.finTarde)) {
      return true;
    }

    return false;
  });

  /**
   * En qué punto del día está el turno de hoy.
   *
   * `tieneTurnoHoy` sólo dice que hoy es día de turno, no que estemos dentro de él.
   * Confundir ambas cosas hacía que, terminado el turno, el Home siguiera mostrando
   * la tarjeta de GPS con "Sin conexión con el sistema" / "Conectando…" — el usuario
   * leía un fallo de red donde en realidad su jornada simplemente había acabado.
   *
   *   sin-turno → hoy es día libre (o aún no sabemos)
   *   antes     → hoy hay turno pero todavía no empieza
   *   durante   → estamos dentro del horario
   *   despues   → el turno de hoy ya terminó
   */
  const faseTurnoHoy = computed<'antes' | 'despues' | 'durante' | 'sin-turno'>(() => {
    const h = horarioEfectivo.value;
    if (!h || h.ini == null || h.fin == null) return 'sin-turno';
    if (enHorarioTurno.value) return 'durante';

    const now = new Date(ahora.value);
    const curMin = now.getHours() * 60 + now.getMinutes();

    // En PARTIDA el turno acaba al cerrar la ventana de la tarde, no la de la mañana.
    const fin = h.finTarde ?? h.fin;

    // Turno nocturno (cruza medianoche): estar fuera de la ventana sólo puede
    // significar que el de hoy todavía no arranca; el de anoche ya lo cubrió
    // `enHorarioTurno`, que trata ese cruce.
    if (fin < h.ini) return 'antes';

    return curMin < h.ini ? 'antes' : 'despues';
  });

  /** Hora a la que termina el turno de hoy, lista para mostrar (o null). */
  const horaFinTurnoHoy = computed<null | string>(
    () => horarioEfectivo.value?.textoFin ?? null,
  );

  /** Hora a la que empieza el turno de hoy, lista para mostrar (o null). */
  const horaInicioTurnoHoy = computed<null | string>(
    () => horarioEfectivo.value?.textoInicio ?? null,
  );

  /**
   * Carga (o recarga) el estado de turno de hoy.
   * @param force  recarga aunque ya esté cargado (usar al volver a foreground o
   *               al cambiar de día).
   */
  async function cargarTurnoHoy(force = false) {
    if (cargando.value) return;
    if (cargado.value && !force) return;

    cargando.value = true;
    try {
      const ahora = new Date();
      const mes   = ahora.getMonth() + 1;
      const anio  = ahora.getFullYear();

      const turnos = await getMisTurnos(mes, anio);
      turnoHoy.value = turnos.find((t) => t.esHoy) ?? null;

      // Próximo turno: primer día futuro de este mes (la lista viene ordenada).
      let prox = turnos.find((t) => !t.esPasado && !t.esHoy) ?? null;

      // Si no hay próximos este mes, buscar el primero del mes siguiente.
      if (!prox) {
        const mesSig  = mes === 12 ? 1 : mes + 1;
        const anioSig = mes === 12 ? anio + 1 : anio;
        try {
          const turnosSig = await getMisTurnos(mesSig, anioSig);
          prox = turnosSig.find((t) => !t.esPasado) ?? null;
        } catch {
          /* sin próximos: dejamos null */
        }
      }
      proximoTurno.value = prox;
      cargado.value = true;
      // Señal para que quien dependa del turno recargue lo suyo. La jornada del día
      // (nombre, horario, lugar) viene de OTRO endpoint, y sin este aviso se quedaba
      // con los datos viejos: la fase cambiaba pero la tarjeta seguía mostrando el
      // horario anterior.
      versionTurno.value += 1;
    } catch {
      // Ante error de red no bloqueamos: turnoHoy queda null (modo conservador).
    } finally {
      cargando.value = false;
    }
  }

  /**
   * Limpia el estado (singleton module-level). DEBE llamarse en el logout: sin
   * esto, el siguiente usuario en el mismo dispositivo hereda el turno del anterior
   * (cargado=true hace que cargarTurnoHoy() retorne sin recargar).
   */
  function reset() {
    turnoHoy.value     = null;
    proximoTurno.value = null;
    horarioRespaldo.value = null;
    cargado.value      = false;
    cargando.value     = false;
    if (relojTimer) {
      clearInterval(relojTimer);
      relojTimer = null;
    }
  }

  return {
    turnoHoy,
    proximoTurno,
    tieneTurnoHoy,
    enHorarioTurno,
    faseTurnoHoy,
    horaFinTurnoHoy,
    horaInicioTurnoHoy,
    versionTurno,
    cargado,
    cargando,
    cargarTurnoHoy,
    reset,
  };
}
