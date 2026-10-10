<script lang="ts" setup>
// Vigilancia en Vivo — control de turnos del día.
//
// Antes esta vista tenía dos pestañas ("Por Jornadas" y "GPS en vivo") que
// cargaban los mismos datos por separado, cada una con su propio intervalo de
// 20s: con diez jornadas vigentes eran más de veinte requests cada 20 segundos.
// El mapa y la tabla GPS se mudaron a /sisu/mapa-gps y acá quedó un solo loop
// de datos enfocado en los turnos.

import type { EstadoPresencia, FichaTarget, Intervalo, PersonalRow, TurnoGroup } from './data';

import type { InternoJornadaDto, JornadaDto, TitularJornadaDto } from '#/api/sisu/jornada';
import type { SesionMonitoreoDto, UsuarioConectadoDto } from '#/api/sisu/monitoreo';

import { computed, onMounted, onUnmounted, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';

import { NButton, NEmpty, NSpin } from 'naive-ui';

import { message } from '#/adapter/naive';
import { getInternosJornada, getJornadasActivas, getTitularesJornada } from '#/api/sisu/jornada';
import { getSesionesActivas, getUsuariosConectados } from '#/api/sisu/monitoreo';
import { useWebSocket } from '#/composables/useWebSocket';

import {
  calcularIntervalos, clasificarTurno, computarPresencia, ESTADO_META,
  horarioPersonal, isoFecha, ORDEN_PRESENCIA, TURNO_META,
} from './data';
import FichaPersona from './modules/ficha-persona.vue';
import TimelineDia from './modules/timeline-dia.vue';
import TurnoCard from './modules/turno-card.vue';

const LS_ABIERTOS = 'sisu.vigilancia.abiertos';

/**
 * Vida del caché de asignaciones (titulares/internos por jornada).
 *
 * Quién está asignado a una jornada cambia cuando un admin edita el turno, no
 * cada 20 segundos. Antes se re-pedía en cada refresco: con 10 jornadas eran 20
 * requests extra cada 20s (y otros tantos por cada señal del WebSocket), que es
 * lo que mantenía la vista en estado "cargando" casi todo el tiempo.
 */
const TTL_ASIGNACIONES_MS = 5 * 60 * 1000;

interface AsignacionesJornada {
  cargadoEn: number;
  internos:  InternoJornadaDto[];
  titulares: TitularJornadaDto[];
}

// ── Estado ─────────────────────────────────────────────────────────────────
/** Solo cubre la pantalla en la primera carga; los refrescos son silenciosos. */
const cargandoInicial = ref(true);
/** Refresco en curso (indicador sutil, no bloquea la lectura). */
const refrescando = ref(false);
const turnos    = ref<TurnoGroup[]>([]);
const conectados = ref<UsuarioConectadoDto[]>([]);
const ultimaActualizacion = ref<Date | null>(null);

/** Caché de asignaciones por idJornada, con su marca de tiempo. */
const cacheAsignaciones = new Map<number, AsignacionesJornada>();
/** Evita que dos cargas solapadas (timer + WebSocket) se pisen. */
let cargaEnCurso: null | Promise<void> = null;

// "ahora" es reactivo con tick propio de 30s: sin esto las cuentas regresivas
// y el marcador del timeline quedaban congelados entre recargas de datos.
const ahora = ref(new Date());

const filtroGlobal = ref<null | string>(null);
const mostrarInactivos = ref(false);

/** Turnos expandidos, por id de jornada. Se persiste entre visitas. */
const abiertos = ref<Set<number>>(new Set());
/** Jornadas que el operador cerró a mano: no se vuelven a auto-abrir. */
const cerradosManual = ref<Set<number>>(new Set());

const FECHA_LABEL = computed(() => ahora.value.toLocaleDateString('es-PE', {
  weekday: 'long', day: 'numeric', month: 'long', year: 'numeric',
}));

// ── Ficha lateral ──────────────────────────────────────────────────────────
const fichaVisible = ref(false);
const fichaTarget  = ref<FichaTarget | null>(null);

function abrirFicha(row: PersonalRow, g: TurnoGroup) {
  fichaTarget.value = {
    idPersona:      row.idPersona,
    tipo:           row.tipo,
    nombreCompleto: row.nombreCompleto,
    cargo:          row.cargo,
    sesion:         row.sesion,
    idAsignacion:   row.idAsignacion,
    idJornada:      g.jornada.idJornada,
    nombreJornada:  g.jornada.nombre,
    nombreLugar:    row.sesion?.nombreLugar ?? g.jornada.nombreLugar,
  };
  fichaVisible.value = true;
}

// ── WebSocket: refresco instantáneo ───────────────────────────────────────
// Una ráfaga de alertas (varios funcionarios saliendo de zona a la vez) llegaba
// como una recarga completa por cada mensaje. El guard de concurrencia colapsa
// las simultáneas; este throttle además evita encadenarlas una tras otra.
const MIN_MS_ENTRE_CARGAS_WS = 3000;
let ultimaCargaWs = 0;

function cargarDesdeWs() {
  const ahoraMs = Date.now();
  if (ahoraMs - ultimaCargaWs < MIN_MS_ENTRE_CARGAS_WS) return;
  ultimaCargaWs = ahoraMs;
  cargar();
}

const { alertasMonitoreo, presenciaSignal } = useWebSocket();
watch(alertasMonitoreo, () => cargarDesdeWs(), { deep: true });
watch(presenciaSignal, () => cargarDesdeWs());

let refreshTimer: null | ReturnType<typeof setInterval> = null;
let tickTimer:    null | ReturnType<typeof setInterval> = null;

// ── Resumen global ─────────────────────────────────────────────────────────
const resumen = computed(() => {
  const todo = turnos.value.flatMap(g => g.personal);
  const cuenta = (e: string) => todo.filter(p => p.estado === e).length;
  return {
    total:      todo.length,
    enZona:     cuenta('EN_ZONA'),
    ausentes:   cuenta('AUSENTE'),
    sinSenal:   cuenta('SIN_SENAL'),
    porEmpezar: cuenta('POR_EMPEZAR'),
    enCurso:    turnos.value.filter(g => g.estado === 'EN_CURSO' || g.estado === 'POR_CERRAR').length,
    proximos:   turnos.value.filter(g => g.estado === 'PROXIMO').length,
  };
});

const conexion = computed(() => ({
  total:  conectados.value.length,
  conGps: conectados.value.filter(u => u.tieneGps).length,
  sinGps: conectados.value.filter(u => !u.tieneGps).length,
}));

/** Turnos activos/próximos/cerrados hoy; los sin actividad van aparte. */
const turnosVisibles = computed(() =>
  turnos.value.filter(g => g.estado !== 'NO_PROGRAMADO'),
);
const turnosInactivos = computed(() =>
  turnos.value.filter(g => g.estado === 'NO_PROGRAMADO'),
);

const listaMostrada = computed(() =>
  mostrarInactivos.value
    ? [...turnosVisibles.value, ...turnosInactivos.value]
    : turnosVisibles.value,
);

// ── Acordeón ───────────────────────────────────────────────────────────────
function cargarAbiertosGuardados(): Set<number> {
  try {
    const raw = localStorage.getItem(LS_ABIERTOS);
    if (!raw) return new Set();
    const arr = JSON.parse(raw);
    return Array.isArray(arr) ? new Set(arr.filter((n: unknown) => typeof n === 'number')) : new Set();
  } catch {
    return new Set();
  }
}

function guardarAbiertos() {
  try {
    localStorage.setItem(LS_ABIERTOS, JSON.stringify([...abiertos.value]));
  } catch {
    // localStorage lleno o bloqueado: el acordeón sigue funcionando en memoria.
  }
}

function estaAbierto(id: number) {
  return abiertos.value.has(id);
}

function setAbierto(id: number, v: boolean) {
  const s = new Set(abiertos.value);
  if (v) {
    s.add(id);
    cerradosManual.value.delete(id);
  } else {
    s.delete(id);
    cerradosManual.value.add(id);
  }
  abiertos.value = s;
  guardarAbiertos();
}

function expandirTodos() {
  abiertos.value = new Set(listaMostrada.value.map(g => g.jornada.idJornada));
  cerradosManual.value = new Set();
  guardarAbiertos();
}

function colapsarTodos() {
  cerradosManual.value = new Set(listaMostrada.value.map(g => g.jornada.idJornada));
  abiertos.value = new Set();
  guardarAbiertos();
}

/**
 * Abre solo lo que merece atención: turno en curso, por cerrar, o con alguien
 * ausente / sin señal. Respeta lo que el operador cerró a mano.
 */
function aplicarAperturaAuto(grupos: TurnoGroup[]) {
  const s = new Set(abiertos.value);
  for (const g of grupos) {
    const id = g.jornada.idJornada;
    if (cerradosManual.value.has(id)) continue;
    const problema = g.stats.ausentes > 0 || g.stats.sinSenal > 0;
    if (TURNO_META[g.estado].abierto || problema) s.add(id);
  }
  abiertos.value = s;
}

function irATurno(idJornada: number) {
  setAbierto(idJornada, true);
  // Se espera al render del cuerpo expandido antes de desplazar.
  requestAnimationFrame(() => {
    document.querySelector(`#turno-${idJornada}`)
      ?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  });
}

function toggleFiltro(clave: string) {
  filtroGlobal.value = filtroGlobal.value === clave ? null : clave;
}

// ── Carga ──────────────────────────────────────────────────────────────────

/**
 * Asignaciones de una jornada, desde caché si siguen frescas.
 *
 * Si la petición falla se conserva lo cacheado (aunque esté vencido) antes que
 * vaciar el turno: mostrar "sin personal asignado" por un error de red sería
 * peor que mostrar datos de hace unos minutos.
 */
async function asignacionesDe(idJornada: number): Promise<AsignacionesJornada> {
  const vigente = cacheAsignaciones.get(idJornada);
  if (vigente && Date.now() - vigente.cargadoEn < TTL_ASIGNACIONES_MS) return vigente;

  try {
    const [titulares, internos] = await Promise.all([
      getTitularesJornada(idJornada),
      getInternosJornada(idJornada),
    ]);
    const fresco = { titulares, internos, cargadoEn: Date.now() };
    cacheAsignaciones.set(idJornada, fresco);
    return fresco;
  } catch {
    return vigente ?? { titulares: [], internos: [], cargadoEn: 0 };
  }
}

/** Fuerza el refetch de asignaciones en el próximo ciclo (botón Actualizar). */
function invalidarCache() {
  cacheAsignaciones.clear();
}

/**
 * Tras detener o reanudar un turno hay que releer las jornadas: el flag vive en
 * la JornadaDto y sin recargar la card seguiría mostrando el estado anterior.
 */
function recargarTrasCambioMonitoreo() {
  invalidarCache();
  return cargar();
}

/** Punto de entrada: colapsa llamadas solapadas en la que ya está corriendo. */
function cargar(): Promise<void> {
  cargaEnCurso ??= cargarInterno().finally(() => { cargaEnCurso = null; });
  return cargaEnCurso;
}

async function cargarInterno() {
  refrescando.value = true;
  try {
    const ahoraD = new Date();
    ahora.value  = ahoraD;
    const hoy    = isoFecha(ahoraD);
    const ayer   = isoFecha(new Date(ahoraD.getFullYear(), ahoraD.getMonth(), ahoraD.getDate() - 1));

    const [todasJornadas, sesActivas, conect] = await Promise.all([
      getJornadasActivas(),
      getSesionesActivas(),
      getUsuariosConectados().catch(() => [] as UsuarioConectadoDto[]),
    ]);

    conectados.value = conect;

    // Clave compuesta tipo+id: idTitular e idInterno son espacios separados
    // (ambos arrancan en 1) y colisionarían en un mapa indexado solo por número,
    // haciendo que un interno "herede" la sesión GPS de un titular con el mismo id.
    const sesMap = new Map<string, SesionMonitoreoDto>();
    for (const s of sesActivas) {
      if (s.idPersonal != null) sesMap.set(`${s.tipoPersonal}-${s.idPersonal}`, s);
    }

    // El rango incluye ayer: una guardia nocturna programada ayer sigue corriendo
    // de madrugada, y filtrando solo por hoy desaparecía justo cuando más importa.
    const vigentes = todasJornadas.filter(j => j.fechaInicio <= hoy && j.fechaFin >= ayer);

    if (vigentes.length === 0) {
      turnos.value = [];
      ultimaActualizacion.value = new Date();
      return;
    }

    // Con el caché caliente esto no dispara ninguna petición: los refrescos de
    // 20s y las señales del WebSocket cuestan solo las 4 llamadas de arriba.
    const detalles = await Promise.all(vigentes.map(j => asignacionesDe(j.idJornada)));

    const grupos: TurnoGroup[] = vigentes.map((j: JornadaDto, i) => {
      const tits: TitularJornadaDto[] = detalles[i]?.titulares ?? [];
      const ints: InternoJornadaDto[] = detalles[i]?.internos  ?? [];

      // Cada persona se clasifica contra SU ventana, no contra la del turno.
      // El horario de la jornada es solo el valor por defecto: en un mismo turno
      // conviven 08–20, 08–14 y 14–20, y usar el general marcaba "por empezar" a
      // quien ya llevaba una hora trabajando. "Le toca hoy" también sale de acá
      // (hay un intervalo programado para hoy) y no de si está en turno en este
      // instante, que dejaba a todo el que aún no empieza como "no le toca hoy".
      const ventanas: Intervalo[] = [];

      function fila(
        a: InternoJornadaDto | TitularJornadaDto,
        base: Pick<PersonalRow, 'cargo' | 'idAsignacion' | 'idPersona' | 'nombreCompleto' | 'tipo'>,
      ): PersonalRow {
        const sesion = sesMap.get(`${base.tipo}-${base.idPersona}`) ?? null;
        const ivs    = calcularIntervalos(j, ahoraD, horarioPersonal(a));
        ventanas.push(...ivs);
        const leToca = ivs.some(iv => iv.diaPrograma === hoy);
        return {
          ...base,
          turnoHoy:   leToca,
          sesion,
          horaInicio: a.horaInicioEfectiva ?? j.horaInicio ?? null,
          horaFin:    a.horaFinEfectiva    ?? j.horaFin    ?? null,
          estado:     computarPresencia(
            leToca, sesion, clasificarTurno(j, ivs, ahoraD).estado,
          ),
        };
      }

      const personal: PersonalRow[] = [
        ...tits.map(t => fila(t, {
          idPersona:      t.idTitular,
          idAsignacion:   t.idTitularJornada,
          tipo:           'TITULAR',
          nombreCompleto: t.nombreCompleto,
          cargo:          t.cargo,
        })),
        ...ints.map(it => fila(it, {
          idPersona:      it.idInterno,
          idAsignacion:   it.idInternoJornada,
          tipo:           'INTERNO',
          nombreCompleto: it.nombreCompleto,
          cargo:          it.carrera,
        })),
      ];

      // El turno agrupa ventanas distintas: su estado sale de la unión de las de
      // su gente. Sin nadie asignado no hay unión, y cae al horario general.
      const intervalos = ventanas.length > 0
        ? ventanas.toSorted((a, b) => a.inicio.getTime() - b.inicio.getTime())
        : calcularIntervalos(j, ahoraD);
      const { estado, referencia } = clasificarTurno(j, intervalos, ahoraD);

      personal.sort((a, b) => ORDEN_PRESENCIA[a.estado] - ORDEN_PRESENCIA[b.estado]);

      const cuenta = (e: string) => personal.filter(p => p.estado === e).length;

      return {
        jornada: j,
        estado,
        referencia,
        intervalos,
        personal,
        stats: {
          total:    personal.length,
          enZona:   cuenta('EN_ZONA'),
          ausentes: cuenta('AUSENTE'),
          sinSenal: cuenta('SIN_SENAL'),
          noHoy:    personal.filter(p => p.estado === 'NO_HOY' || p.estado === 'TURNO_FINALIZADO').length,
        },
      };
    });

    // Orden: por estado del turno y, dentro del mismo estado, lo problemático arriba.
    grupos.sort((a, b) => {
      const da = TURNO_META[a.estado].orden - TURNO_META[b.estado].orden;
      if (da !== 0) return da;
      const pa = a.stats.ausentes + a.stats.sinSenal;
      const pb = b.stats.ausentes + b.stats.sinSenal;
      if (pa !== pb) return pb - pa;
      return b.stats.enZona - a.stats.enZona;
    });

    turnos.value = grupos;
    aplicarAperturaAuto(grupos);
    ultimaActualizacion.value = new Date();
  } catch (error: any) {
    message.error(error?.message ?? 'Error al cargar los turnos');
  } finally {
    refrescando.value    = false;
    cargandoInicial.value = false;
  }
}

/** Botón Actualizar: vuelve a pedir también las asignaciones. */
function refrescarTodo() {
  invalidarCache();
  cargar();
}

onMounted(() => {
  abiertos.value = cargarAbiertosGuardados();
  cargar();
  // Refresco de datos cada 20s (las transiciones de zona y conexiones llegan al
  // instante por WS; esto cubre cambios de estado por tiempo/staleness).
  refreshTimer = setInterval(cargar, 20_000);
  // Tick de reloj: mueve cuentas regresivas y timeline sin pedir datos.
  tickTimer = setInterval(() => { ahora.value = new Date(); }, 30_000);
});

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer);
  if (tickTimer) clearInterval(tickTimer);
});

// Estados sobre los que la barra superior puede filtrar.
const FILTROS: { clave: EstadoPresencia; label: string }[] = [
  { clave: 'AUSENTE',   label: 'Ausentes' },
  { clave: 'SIN_SENAL', label: 'Sin señal' },
  { clave: 'EN_ZONA',   label: 'En zona' },
];
</script>

<template>
  <Page auto-content-height>
    <!-- ══ Barra superior ══════════════════════════════════════════════════ -->
    <div class="vg-top">
      <div class="vg-fecha">
        <div class="vg-fecha__dia">
          <span class="vg-fecha__num">{{ ahora.getDate() }}</span>
          <span class="vg-fecha__mes">
            {{ ahora.toLocaleDateString('es', { month: 'short' }).toUpperCase() }}
          </span>
        </div>
        <div class="vg-fecha__info">
          <div class="vg-fecha__label">{{ FECHA_LABEL }}</div>
          <div class="vg-fecha__sub">
            Control de turnos
            <!-- Indicador discreto: el refresco no debe tapar lo que se está leyendo -->
            <span v-if="refrescando && !cargandoInicial" class="vg-refresh">· actualizando…</span>
            <span v-else-if="ultimaActualizacion">
              · actualizado {{ ultimaActualizacion.toLocaleTimeString('es', { hour: '2-digit', minute: '2-digit' }) }}
            </span>
          </div>
        </div>
      </div>

      <div class="vg-top__right">
        <!-- Estado de turnos -->
        <div class="vg-pill vg-pill--on">
          <span class="vg-dot vg-dot--pulse"></span>
          {{ resumen.enCurso }} en curso
        </div>
        <div v-if="resumen.proximos > 0" class="vg-pill vg-pill--next">
          {{ resumen.proximos }} por empezar
        </div>
        <!-- Conexión -->
        <div class="vg-pill vg-pill--conn" :title="`${conexion.conGps} con GPS · ${conexion.sinGps} sin GPS`">
          👥 {{ conexion.total }} en línea
        </div>
      </div>
    </div>

    <!-- ══ Semáforo global (también filtra) ════════════════════════════════ -->
    <div class="vg-resumen">
      <button
        v-for="f in FILTROS"
        :key="f.clave"
        class="vg-stat"
        :class="{ 'vg-stat--on': filtroGlobal === f.clave }"
        :style="{ '--vg-c': ESTADO_META[f.clave].color }"
        @click="toggleFiltro(f.clave)"
      >
        <span class="vg-stat__num">
          {{ f.clave === 'AUSENTE' ? resumen.ausentes
            : f.clave === 'SIN_SENAL' ? resumen.sinSenal : resumen.enZona }}
        </span>
        <span class="vg-stat__lbl">{{ f.label }}</span>
      </button>

      <div class="vg-stat vg-stat--plain">
        <span class="vg-stat__num">{{ resumen.total }}</span>
        <span class="vg-stat__lbl">Total personal</span>
      </div>

      <span v-if="filtroGlobal" class="vg-filtro-aviso">
        Filtrando por <strong>{{ FILTROS.find(f => f.clave === filtroGlobal)?.label }}</strong>
        <button class="vg-filtro-x" @click="filtroGlobal = null">✕ quitar</button>
      </span>

      <div class="vg-acciones">
        <NButton size="tiny" quaternary @click="expandirTodos">⤢ Expandir</NButton>
        <NButton size="tiny" quaternary @click="colapsarTodos">⤡ Colapsar</NButton>
        <NButton size="small" ghost :loading="refrescando" @click="refrescarTodo">↻ Actualizar</NButton>
      </div>
    </div>

    <!-- ══ Línea de tiempo ════════════════════════════════════════════════ -->
    <TimelineDia :ahora="ahora" :turnos="turnosVisibles" @ir-a="irATurno" />

    <!-- ══ Leyenda de estados de turno ════════════════════════════════════ -->
    <div class="vg-leyenda">
      <span
        v-for="(m, est) in TURNO_META"
        :key="est"
        class="vg-ley"
        :style="{ color: m.color, borderColor: m.color }"
      >{{ m.label }}</span>
    </div>

    <!-- ══ Lista de turnos ════════════════════════════════════════════════ -->
    <NSpin :show="cargandoInicial" style="min-height: 200px">
      <NEmpty
        v-if="!cargandoInicial && turnos.length === 0"
        description="Sin turnos vigentes para hoy"
        class="py-12"
      />

      <div v-for="g in listaMostrada" :id="`turno-${g.jornada.idJornada}`" :key="g.jornada.idJornada">
        <TurnoCard
          :abierto="estaAbierto(g.jornada.idJornada)"
          :ahora="ahora"
          :filtro="filtroGlobal"
          :turno="g"
          @monitoreo-cambiado="recargarTrasCambioMonitoreo"
          @update:abierto="(v: boolean) => setAbierto(g.jornada.idJornada, v)"
          @ver-ficha="(row) => abrirFicha(row, g)"
        />
      </div>

      <!-- Turnos sin actividad hoy, detrás de un click -->
      <button
        v-if="turnosInactivos.length > 0"
        class="vg-inactivos"
        @click="mostrarInactivos = !mostrarInactivos"
      >
        {{ mostrarInactivos ? '▴ Ocultar' : '▾ Ver' }}
        {{ turnosInactivos.length }}
        {{ turnosInactivos.length === 1 ? 'turno sin actividad hoy' : 'turnos sin actividad hoy' }}
      </button>
    </NSpin>

    <!-- ══ Ficha lateral ══════════════════════════════════════════════════ -->
    <FichaPersona v-model:show="fichaVisible" :target="fichaTarget" />
  </Page>
</template>

<style scoped>
/* ── Barra superior ─────────────────────────────────────────────────────── */
.vg-top {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  padding: 11px 15px;
  margin-bottom: 12px;
  background: hsl(var(--card));
  border: 1px solid hsl(var(--border));
  border-radius: 14px;
}

.vg-fecha { display: flex; gap: 12px; align-items: center; }
.vg-fecha__dia {
  display: flex; flex-direction: column; align-items: center;
  padding: 5px 12px; border-radius: 10px; flex-shrink: 0;
  background: hsl(var(--primary));
  color: hsl(var(--primary-foreground));
}
.vg-fecha__num { font-size: 1.7rem; font-weight: 900; line-height: 1; }
.vg-fecha__mes { font-size: 0.63rem; font-weight: 700; letter-spacing: 1px; opacity: 0.85; }

.vg-fecha__info  { min-width: 0; }
.vg-fecha__label { font-size: 0.9rem; font-weight: 600; color: hsl(var(--foreground)); text-transform: capitalize; }
.vg-fecha__sub   { font-size: 0.72rem; color: hsl(var(--muted-foreground)); margin-top: 2px; }
.vg-refresh      { font-style: italic; opacity: 0.75; animation: vg-fade 1.2s ease-in-out infinite; }

@keyframes vg-fade {
  0%, 100% { opacity: 0.75; }
  50%      { opacity: 0.35; }
}

.vg-top__right { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }

.vg-pill {
  display: flex; align-items: center; gap: 6px;
  padding: 5px 12px; border-radius: 20px;
  font-size: 0.75rem; font-weight: 700;
  border: 1.5px solid transparent;
  white-space: nowrap;
}
.vg-pill--on {
  color: #18a058;
  background: color-mix(in srgb, #18a058 12%, transparent);
  border-color: color-mix(in srgb, #18a058 35%, transparent);
}
.vg-pill--next {
  color: #1c7ed6;
  background: color-mix(in srgb, #1c7ed6 10%, transparent);
  border-color: color-mix(in srgb, #1c7ed6 30%, transparent);
}
.vg-pill--conn {
  color: hsl(var(--muted-foreground));
  background: hsl(var(--muted));
  border-color: hsl(var(--border));
}

.vg-dot { width: 7px; height: 7px; border-radius: 50%; flex-shrink: 0; background: #18a058; }
.vg-dot--pulse { animation: vg-pulse 2s ease-in-out infinite; }

@keyframes vg-pulse {
  0%, 100% { opacity: 1; box-shadow: 0 0 0 0 rgb(24 160 88 / 40%); }
  50%      { opacity: 0.7; box-shadow: 0 0 0 4px rgb(24 160 88 / 0%); }
}

/* ── Semáforo global ────────────────────────────────────────────────────── */
.vg-resumen {
  display: flex; gap: 8px; align-items: center; flex-wrap: wrap;
  margin-bottom: 12px;
}

.vg-stat {
  display: flex; flex-direction: column; align-items: center;
  min-width: 76px; padding: 5px 14px;
  font: inherit;
  cursor: pointer;
  background: hsl(var(--card));
  border: 1px solid color-mix(in srgb, var(--vg-c, hsl(var(--border))) 32%, transparent);
  border-radius: 10px;
  transition: background 0.14s, border-color 0.14s;
}
.vg-stat:hover { background: hsl(var(--accent) / 50%); }
.vg-stat--on {
  border-color: var(--vg-c);
  background: color-mix(in srgb, var(--vg-c) 12%, transparent);
  box-shadow: inset 0 0 0 1px var(--vg-c);
}
.vg-stat--plain { cursor: default; border-color: hsl(var(--border)); }
.vg-stat--plain:hover { background: hsl(var(--card)); }

.vg-stat__num { font-size: 1.35rem; font-weight: 800; line-height: 1.1; color: var(--vg-c, hsl(var(--foreground))); }
.vg-stat--plain .vg-stat__num { color: hsl(var(--foreground)); }
.vg-stat__lbl { font-size: 0.64rem; color: hsl(var(--muted-foreground)); text-align: center; margin-top: 1px; }

.vg-filtro-aviso { font-size: 0.72rem; color: hsl(var(--muted-foreground)); }
.vg-filtro-x {
  margin-left: 6px; padding: 1px 7px;
  font-size: 0.68rem; font-weight: 600;
  color: hsl(var(--muted-foreground));
  cursor: pointer;
  background: transparent;
  border: 1px solid hsl(var(--border));
  border-radius: 10px;
}
.vg-filtro-x:hover { color: hsl(var(--foreground)); background: hsl(var(--accent)); }

.vg-acciones { display: flex; gap: 4px; align-items: center; margin-left: auto; }

/* ── Leyenda ────────────────────────────────────────────────────────────── */
.vg-leyenda { display: flex; gap: 5px; flex-wrap: wrap; margin-bottom: 12px; }
.vg-ley {
  padding: 1px 8px; border-radius: 4px;
  font-size: 0.62rem; font-weight: 800; letter-spacing: 0.6px;
  background: color-mix(in srgb, currentColor 10%, transparent);
  border: 1px solid color-mix(in srgb, currentColor 32%, transparent);
}

/* ── Inactivos ──────────────────────────────────────────────────────────── */
.vg-inactivos {
  width: 100%; padding: 8px;
  font-size: 0.75rem; font-weight: 600;
  color: hsl(var(--muted-foreground));
  cursor: pointer;
  background: transparent;
  border: 1px dashed hsl(var(--border));
  border-radius: 10px;
}
.vg-inactivos:hover { color: hsl(var(--foreground)); background: hsl(var(--accent) / 40%); }
</style>
