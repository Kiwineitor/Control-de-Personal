<script lang="ts" setup>
import { ref, computed, watch } from 'vue';
import { Page } from '@vben/common-ui';
import { NSelect, NEmpty, NCard, NSpin, NTag, NCollapse, NCollapseItem } from 'naive-ui';
import type { SelectOption } from 'naive-ui';
import type {
  JornadaDto, TitularJornadaDto, InternoJornadaDto,
} from '#/api/sisu/jornada';
import { getJornadasActivas, getTitularesJornada, getInternosJornada } from '#/api/sisu/jornada';
import type { ActividadJornadaDto } from '#/api/sisu/actividades';
import { getActividadesJornada } from '#/api/sisu/actividades';
import type { BitacoraEntrada } from '#/api/sisu/bitacora';
import { bitacoraJornadaAdmin } from '#/api/sisu/bitacora';
import type { SesionMonitoreoDto, EventoAbandonoDto, AusenciaJornadaDto } from '#/api/sisu/monitoreo';
import { getSesionesJornada, getAusenciasJornada, getIncidenciasJornada } from '#/api/sisu/monitoreo';
import { message } from '#/adapter/naive';
import TimelineDia from './modules/timeline-dia.vue';

// ── Estado ──────────────────────────────────────────────────────────────────

const jornadasActivas = ref<JornadaDto[]>([]);
const jornadaSel = ref<number | null>(null);
const cargandoJornadas = ref(false);
const cargandoDatos = ref(false);

const jornadaActual = ref<JornadaDto | null>(null);
const titulares = ref<TitularJornadaDto[]>([]);
const internos = ref<InternoJornadaDto[]>([]);
const actividades = ref<ActividadJornadaDto[]>([]);
const bitacoras = ref<BitacoraEntrada[]>([]);
const sesiones = ref<SesionMonitoreoDto[]>([]);
const ausencias = ref<AusenciaJornadaDto[]>([]);
const incidencias = ref<EventoAbandonoDto[]>([]);

const jornadaOpts = computed<SelectOption[]>(() =>
  jornadasActivas.value.map((j) => ({
    label: `${j.nombre ?? `Turno #${j.idJornada}`} — ${j.horaInicio?.substring(0, 5)} a ${j.horaFin?.substring(0, 5)} (${j.tipoJornada})`,
    value: j.idJornada,
  })),
);

// ── Cálculo de días ─────────────────────────────────────────────────────────

function parseDiasCsv(csv: string | null | undefined): string[] {
  if (!csv || !csv.trim()) return [];
  return csv.split(',').map(d => d.trim()).filter(Boolean);
}

function generarRangoFechas(inicio: string, fin: string): string[] {
  const dias: string[] = [];
  const cur = new Date(inicio);
  const end = new Date(fin);
  while (cur <= end) {
    const yyyy = cur.getFullYear();
    const mm = String(cur.getMonth() + 1).padStart(2, '0');
    const dd = String(cur.getDate()).padStart(2, '0');
    dias.push(`${yyyy}-${mm}-${dd}`);
    cur.setDate(cur.getDate() + 1);
  }
  return dias;
}

const diasJornada = computed(() => {
  const j = jornadaActual.value;
  if (!j) return [];
  const csvDias = parseDiasCsv(j.diasDefault);
  if (csvDias.length > 0) return csvDias;
  return generarRangoFechas(j.fechaInicio, j.fechaFin);
});

// ── Helpers fecha ───────────────────────────────────────────────────────────

const DIAS_SEMANA = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];
const MESES = ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'];

function formatearFechaCorta(fechaISO: string): { diaSemana: string; dia: number; mes: string; anio: number; label: string } {
  const d = new Date(fechaISO + 'T12:00:00');
  const ds = DIAS_SEMANA[d.getDay()] ?? '';
  const ms = MESES[d.getMonth()] ?? '';
  return {
    diaSemana: ds,
    dia: d.getDate(),
    mes: ms,
    anio: d.getFullYear(),
    label: `${ds} ${d.getDate()} ${ms} ${d.getFullYear()}`,
  };
}

// ── Mapas para acceso rápido por día ────────────────────────────────────────

const sesionesPorDia = computed(() => {
  const map: Record<string, SesionMonitoreoDto[]> = {};
  for (const s of sesiones.value) {
    const fecha = s.timestampInicio?.substring(0, 10);
    if (!fecha) continue;
    if (!map[fecha]) map[fecha] = [];
    map[fecha].push(s);
  }
  return map;
});

const ausenciasPorDia = computed(() => {
  const map: Record<string, AusenciaJornadaDto[]> = {};
  for (const a of ausencias.value) {
    const fecha = a.inicioAusencia?.substring(0, 10);
    if (!fecha) continue;
    if (!map[fecha]) map[fecha] = [];
    map[fecha].push(a);
  }
  return map;
});

const incidenciasPorDia = computed(() => {
  const map: Record<string, EventoAbandonoDto[]> = {};
  for (const i of incidencias.value) {
    const fecha = i.timestampSalida?.substring(0, 10);
    if (!fecha) continue;
    if (!map[fecha]) map[fecha] = [];
    map[fecha].push(i);
  }
  return map;
});

const bitacorasPorDia = computed(() => {
  const map: Record<string, BitacoraEntrada[]> = {};
  for (const b of bitacoras.value) {
    const fecha = b.fechaHoraEvento?.substring(0, 10);
    if (!fecha) continue;
    if (!map[fecha]) map[fecha] = [];
    map[fecha].push(b);
  }
  return map;
});

// ── Qué personas trabajan cada día ──────────────────────────────────────────

interface PersonaAsignada {
  tipo: 'TITULAR' | 'INTERNO';
  id: number;
  idAsignacion: number;
  nombre: string;
  horaInicio: string;
  horaFin: string;
  estado: string;
}

function personasDelDia(fechaISO: string): PersonaAsignada[] {
  const personas: PersonaAsignada[] = [];

  for (const t of titulares.value) {
    const diasTrabajo = parseDiasCsv(t.diasTrabajoJson);
    if (diasTrabajo.length > 0 && !diasTrabajo.includes(fechaISO)) continue;
    if (diasTrabajo.length === 0) {
      const j = jornadaActual.value;
      if (j && (fechaISO < j.fechaInicio || fechaISO > j.fechaFin)) continue;
    }
    personas.push({
      tipo: 'TITULAR',
      id: t.idTitular,
      idAsignacion: t.idTitularJornada,
      nombre: t.nombreCompleto,
      horaInicio: t.horaInicioEfectiva?.substring(0, 5) ?? '--:--',
      horaFin: t.horaFinEfectiva?.substring(0, 5) ?? '--:--',
      estado: t.estado,
    });
  }

  for (const i of internos.value) {
    const j = jornadaActual.value;
    if (j && (fechaISO < j.fechaInicio || fechaISO > j.fechaFin)) continue;
    personas.push({
      tipo: 'INTERNO',
      id: i.idInterno,
      idAsignacion: i.idInternoJornada,
      nombre: i.nombreCompleto,
      horaInicio: i.horaInicioEfectiva?.substring(0, 5) ?? '--:--',
      horaFin: i.horaFinEfectiva?.substring(0, 5) ?? '--:--',
      estado: i.estado,
    });
  }

  return personas;
}

// ── Datos por día ───────────────────────────────────────────────────────────

interface DiaTimeline {
  fecha: string;
  info: ReturnType<typeof formatearFechaCorta>;
  personas: PersonaAsignada[];
  sesiones: SesionMonitoreoDto[];
  ausencias: AusenciaJornadaDto[];
  incidencias: EventoAbandonoDto[];
  bitacoras: BitacoraEntrada[];
  actividades: ActividadJornadaDto[];
}

const timeline = computed<DiaTimeline[]>(() => {
  return diasJornada.value.map(fecha => ({
    fecha,
    info: formatearFechaCorta(fecha),
    personas: personasDelDia(fecha),
    sesiones: sesionesPorDia.value[fecha] ?? [],
    ausencias: ausenciasPorDia.value[fecha] ?? [],
    incidencias: incidenciasPorDia.value[fecha] ?? [],
    bitacoras: bitacorasPorDia.value[fecha] ?? [],
    actividades: actividades.value,
  })).reverse();
});

const hoy = new Date().toISOString().substring(0, 10);

// ── Carga de datos ──────────────────────────────────────────────────────────

async function cargarJornadas() {
  cargandoJornadas.value = true;
  try {
    jornadasActivas.value = (await getJornadasActivas()) ?? [];
  } catch {
    message.error('Error al cargar jornadas activas');
  } finally {
    cargandoJornadas.value = false;
  }
}

async function cargarDatosJornada(id: number) {
  cargandoDatos.value = true;
  try {
    jornadaActual.value = jornadasActivas.value.find(j => j.idJornada === id) ?? null;

    const [tits, ints, acts, bits, sss, aaa, iii] = await Promise.all([
      getTitularesJornada(id),
      getInternosJornada(id),
      getActividadesJornada(id).catch(() => []),
      bitacoraJornadaAdmin(id).catch(() => []),
      getSesionesJornada(id).catch(() => []),
      getAusenciasJornada(id).catch(() => []),
      getIncidenciasJornada(id).catch(() => []),
    ]);

    titulares.value = tits ?? [];
    internos.value = ints ?? [];
    actividades.value = acts ?? [];
    bitacoras.value = bits ?? [];
    sesiones.value = sss ?? [];
    ausencias.value = aaa ?? [];
    incidencias.value = iii ?? [];
  } catch {
    message.error('Error al cargar datos de la jornada');
  } finally {
    cargandoDatos.value = false;
  }
}

watch(jornadaSel, (id) => {
  if (!id) {
    jornadaActual.value = null;
    titulares.value = [];
    internos.value = [];
    actividades.value = [];
    bitacoras.value = [];
    sesiones.value = [];
    ausencias.value = [];
    incidencias.value = [];
    return;
  }
  cargarDatosJornada(id);
});

cargarJornadas();
</script>

<template>
  <Page auto-content-height>
    <div class="mb-4 flex items-center justify-between flex-wrap gap-2">
      <div>
        <h2 class="text-lg font-bold mb-0">SEGUIMIENTO DE TURNOS</h2>
        <p class="text-sm text-muted-foreground mt-0.5">
          Seguimiento por días asignados — actividades, bitácora, GPS y faltas
        </p>
      </div>
    </div>

    <NCard size="small" class="mb-3">
      <div class="flex flex-wrap items-center gap-4">
        <div class="flex flex-col gap-1">
          <span class="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            Jornada activa
          </span>
          <NSelect
            v-model:value="jornadaSel"
            :options="jornadaOpts"
            :loading="cargandoJornadas"
            placeholder="Seleccionar jornada activa…"
            clearable
            style="width: 420px"
          />
        </div>

        <template v-if="jornadaActual">
          <div class="flex flex-wrap gap-2 text-xs">
            <NTag size="small" round :bordered="false">
              {{ jornadaActual.tipoJornada }}
            </NTag>
            <NTag size="small" round :bordered="false" type="info">
              {{ jornadaActual.horaInicio.substring(0, 5) }} – {{ jornadaActual.horaFin.substring(0, 5) }}
            </NTag>
            <NTag size="small" round :bordered="false" type="success">
              {{ titulares.length }} titular(es)
            </NTag>
            <NTag v-if="internos.length > 0" size="small" round :bordered="false" type="warning">
              {{ internos.length }} interno(s)
            </NTag>
            <NTag size="small" round :bordered="false">
              {{ diasJornada.length }} día(s)
            </NTag>
          </div>
        </template>
      </div>
    </NCard>

    <div v-if="!jornadaSel" class="pt-12">
      <NEmpty description="Selecciona una jornada activa para ver el seguimiento por días">
        <template #icon><span style="font-size: 44px;">📋</span></template>
      </NEmpty>
    </div>

    <NSpin v-else-if="cargandoDatos" size="large" class="pt-12">
      <template #description>
        <span class="text-sm text-muted-foreground">Cargando datos de la jornada…</span>
      </template>
    </NSpin>

    <div v-else-if="timeline.length === 0" class="pt-12">
      <NEmpty description="No hay días asignados en esta jornada" />
    </div>

    <NCollapse v-else :default-expanded-names="timeline.length < 30 ? timeline.map(d => d.fecha) : [hoy]">
      <NCollapseItem
        v-for="dia in timeline"
        :key="dia.fecha"
        :name="dia.fecha"
      >
        <template #header>
          <div class="flex items-center gap-3">
            <span
              v-if="dia.fecha === hoy"
              class="inline-block w-2 h-2 rounded-full flex-shrink-0"
              style="background: #22c55e; animation: pulse 2s infinite"
            />
            <span class="font-semibold text-sm">
              {{ dia.info.diaSemana }} {{ dia.info.dia }} {{ dia.info.mes }} {{ dia.info.anio }}
            </span>
            <NTag
              v-if="dia.fecha === hoy"
              size="tiny"
              type="success"
              round
              :bordered="false"
            >
              HOY
            </NTag>
            <span class="text-xs text-muted-foreground">
              · {{ dia.personas.length }} persona(s)
            </span>
            <span v-if="dia.ausencias.length > 0" class="text-xs" style="color: #ef4444">
              · {{ dia.ausencias.length }} falta(s)
            </span>
            <span v-if="dia.incidencias.length > 0" class="text-xs" style="color: #f59e0b">
              · {{ dia.incidencias.length }} incidencia(s)
            </span>
          </div>
        </template>

        <TimelineDia :dia="dia" />
      </NCollapseItem>
    </NCollapse>
  </Page>
</template>

<style scoped>
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}
</style>
