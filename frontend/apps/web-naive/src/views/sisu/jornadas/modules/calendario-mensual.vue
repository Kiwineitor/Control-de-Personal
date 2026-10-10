<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue';
import { NSelect, NButton, NSpin, NTag, NEmpty } from 'naive-ui';
import type { JornadaDto } from '#/api/sisu/jornada';
import { getJornadasMes } from '#/api/sisu/jornada';
import { TIPO_META } from '../data';

// ── Emits ─────────────────────────────────────────────────────────────────

const emit = defineEmits<{ 'open-detalle': [row: JornadaDto] }>();

// ── Estado ────────────────────────────────────────────────────────────────

const mesActual  = ref(new Date().getMonth() + 1);
const anioActual = ref(new Date().getFullYear());
const cargando   = ref(false);
const jornadas   = ref<JornadaDto[]>([]);

const HOY = new Date().toISOString().slice(0, 10);

// ── Opciones de selectores ────────────────────────────────────────────────

const MES_LABELS = [
  'Enero','Febrero','Marzo','Abril','Mayo','Junio',
  'Julio','Agosto','Septiembre','Octubre','Noviembre','Diciembre',
];
const MES_OPTS  = MES_LABELS.map((label, i) => ({ label, value: i + 1 }));
const ANIO_OPTS = [2024, 2025, 2026, 2027, 2028].map(y => ({ label: String(y), value: y }));
const DIAS_HDR  = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];

// ── Carga de datos ────────────────────────────────────────────────────────

async function cargar() {
  cargando.value = true;
  try {
    jornadas.value = await getJornadasMes(anioActual.value, mesActual.value);
  } catch {
    jornadas.value = [];
  } finally {
    cargando.value = false;
  }
}

function navMes(delta: number) {
  let m = mesActual.value + delta;
  let a = anioActual.value;
  if (m < 1)  { m = 12; a--; }
  if (m > 12) { m = 1;  a++; }
  mesActual.value  = m;
  anioActual.value = a;
}

watch([mesActual, anioActual], cargar);
onMounted(cargar);

// ── Título del mes ────────────────────────────────────────────────────────

const tituloMes = computed(() => `${MES_LABELS[mesActual.value - 1]} ${anioActual.value}`);

// ── Lógica de días del turno ──────────────────────────────────────────────

function jornadasDelDia(fecha: string): JornadaDto[] {
  return jornadas.value.filter(j => {
    if (j.fechaInicio > fecha || j.fechaFin < fecha) return false;
    if (j.diasDefault) {
      return j.diasDefault.split(',').map(s => s.trim()).includes(fecha);
    }
    return true;
  });
}

// ── Grilla del calendario ─────────────────────────────────────────────────

interface DiaCell {
  fecha:    string;
  diaNum:   number;
  esHoy:    boolean;
  jorns:    JornadaDto[];
}

const calendarGrid = computed(() => {
  const year = anioActual.value;
  const mes  = mesActual.value;
  const totalDias = new Date(year, mes, 0).getDate();

  // JS getDay(): 0=Dom,1=Lun...6=Sáb → queremos Lun=0
  let primerDow = new Date(year, mes - 1, 1).getDay();
  primerDow = primerDow === 0 ? 6 : primerDow - 1;

  const celdas: (DiaCell | null)[] = [];
  for (let i = 0; i < primerDow; i++) celdas.push(null);

  for (let d = 1; d <= totalDias; d++) {
    const fecha = `${year}-${String(mes).padStart(2,'0')}-${String(d).padStart(2,'0')}`;
    celdas.push({ fecha, diaNum: d, esHoy: fecha === HOY, jorns: jornadasDelDia(fecha) });
  }
  while (celdas.length % 7 !== 0) celdas.push(null);

  const semanas: (DiaCell | null)[][] = [];
  for (let i = 0; i < celdas.length; i += 7) semanas.push(celdas.slice(i, i + 7));
  return semanas;
});

// ── Leyenda: solo tipos presentes este mes ────────────────────────────────

const leyenda = computed(() => {
  const vistos = new Set(jornadas.value.map(j => j.tipoJornada));
  return [...vistos]
    .filter(t => t !== 'GUARDIA' && TIPO_META[t])
    .map(t => ({ tipo: t, meta: TIPO_META[t]! }));
});

// ── Resumen del mes ───────────────────────────────────────────────────────

const resumen = computed(() => {
  const sinPersonal = jornadas.value.filter(j => j.totalTitulares === 0).length;
  return { total: jornadas.value.length, sinPersonal };
});

// ── Helper: etiqueta de jornada ───────────────────────────────────────────

function etiqueta(j: JornadaDto): string {
  return j.nombre || TIPO_META[j.tipoJornada]?.label.replace(/^\S+\s*/, '') || j.tipoJornada;
}

function iconTipo(j: JornadaDto): string {
  const iconos: Record<string, string> = {
    GUARDIA_12H: '🛡️', GUARDIA_24H: '🔒', NOCTURNA: '🌙',
    EMERGENCIA: '🚨', ADMINISTRATIVA: '🏢', LLAMADA: '📞',
    ROTACION: '🔄', COBERTURA: '📌',
  };
  return iconos[j.tipoJornada] ?? '📋';
}
</script>

<template>
  <!-- ── Navegación mes ──────────────────────────────────────────────────── -->
  <div class="cal-nav">
    <NButton circle size="small" ghost @click="navMes(-1)">◀</NButton>

    <div class="cal-nav__selectors">
      <NSelect v-model:value="mesActual"  :options="MES_OPTS"  size="small" style="width:138px" />
      <NSelect v-model:value="anioActual" :options="ANIO_OPTS" size="small" style="width:82px"  />
    </div>

    <NButton circle size="small" ghost @click="navMes(1)">▶</NButton>
    <NButton size="small" ghost :loading="cargando" style="margin-left:8px" @click="cargar">↻</NButton>
  </div>

  <!-- ── Leyenda tipos activos ───────────────────────────────────────────── -->
  <div v-if="leyenda.length > 0" class="cal-leyenda">
    <span
      v-for="item in leyenda"
      :key="item.tipo"
      class="leyenda-chip"
      :style="{ background: item.meta.color + '18', borderColor: item.meta.color + '55', color: item.meta.color }"
    >
      {{ item.meta.label }}
    </span>
  </div>

  <!-- ── Calendario ─────────────────────────────────────────────────────── -->
  <NSpin :show="cargando" style="min-height: 200px">

    <NEmpty
      v-if="!cargando && jornadas.length === 0"
      :description="`Sin jornadas registradas en ${tituloMes}`"
      class="py-10"
    />

    <template v-else>
      <!-- Encabezados de días de la semana -->
      <div class="cal-grid cal-grid--header">
        <div v-for="d in DIAS_HDR" :key="d" class="cal-hdr-cell">{{ d }}</div>
      </div>

      <!-- Semanas -->
      <div
        v-for="(semana, si) in calendarGrid"
        :key="si"
        class="cal-grid"
      >
        <div
          v-for="(dia, di) in semana"
          :key="di"
          class="cal-cell"
          :class="{
            'cal-cell--hoy':  dia?.esHoy,
            'cal-cell--vacio': !dia,
          }"
        >
          <template v-if="dia">
            <!-- Número del día -->
            <div
              class="cal-dia-num"
              :class="{ 'cal-dia-num--hoy': dia.esHoy }"
            >
              {{ dia.diaNum }}
            </div>

            <!-- Chips de jornadas (máx. 2 visibles) -->
            <div
              v-for="j in dia.jorns.slice(0, 2)"
              :key="j.idJornada"
              class="jorn-chip"
              :style="{
                borderLeftColor: TIPO_META[j.tipoJornada]?.color ?? '#64748b',
                background:     (TIPO_META[j.tipoJornada]?.color ?? '#64748b') + '15',
              }"
              :title="`${etiqueta(j)} · ${j.horaInicio?.slice(0,5) ?? ''}–${j.horaFin?.slice(0,5) ?? ''} · ${j.totalTitulares}T ${j.totalInternos > 0 ? j.totalInternos + 'I' : ''}`"
              @click="emit('open-detalle', j)"
            >
              <span class="jorn-chip__icon">{{ iconTipo(j) }}</span>
              <span class="jorn-chip__label">{{ etiqueta(j) }}</span>
              <span class="jorn-chip__cnt">
                {{ j.totalTitulares }}T<template v-if="j.totalInternos > 0">·{{ j.totalInternos }}I</template>
              </span>
            </div>

            <!-- Overflow "+N más" -->
            <div
              v-if="dia.jorns.length > 2"
              class="jorn-chip jorn-chip--mas"
              :title="dia.jorns.slice(2).map(j => etiqueta(j)).join(', ')"
            >
              +{{ dia.jorns.length - 2 }} más
            </div>
          </template>
        </div>
      </div>

      <!-- ── Resumen del mes ────────────────────────────────────────────── -->
      <div class="cal-resumen">
        <NTag type="info" size="small" round>
          📅 {{ resumen.total }} jornada{{ resumen.total !== 1 ? 's' : '' }} en {{ tituloMes }}
        </NTag>
        <NTag v-if="resumen.sinPersonal > 0" type="warning" size="small" round>
          ⚠ {{ resumen.sinPersonal }} sin personal asignado
        </NTag>
      </div>
    </template>

  </NSpin>
</template>

<style scoped>
/* ── Navegación ────────────────────────────────────────────────────────── */
.cal-nav {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}
.cal-nav__selectors {
  display: flex;
  gap: 6px;
  flex: 1;
  justify-content: center;
}

/* ── Leyenda ──────────────────────────────────────────────────────────── */
.cal-leyenda {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-bottom: 14px;
}
.leyenda-chip {
  font-size: 0.70rem;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 20px;
  border: 1px solid;
  white-space: nowrap;
}

/* ── Grilla ───────────────────────────────────────────────────────────── */
.cal-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 3px;
  margin-bottom: 3px;
}
.cal-grid--header {
  margin-bottom: 4px;
}

/* Encabezados Lun-Dom */
.cal-hdr-cell {
  text-align: center;
  font-size: 0.70rem;
  font-weight: 700;
  color: hsl(var(--muted-foreground));
  padding: 4px 0 6px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

/* Celda de día */
.cal-cell {
  min-height: 80px;
  border: 1px solid hsl(var(--border));
  border-radius: 8px;
  padding: 5px 4px 4px;
  background: hsl(var(--card));
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 2px;
  transition: box-shadow 0.12s;
}
.cal-cell:hover:not(.cal-cell--vacio) {
  box-shadow: 0 0 0 2px hsl(var(--ring) / 0.4);
}
.cal-cell--hoy {
  border-color: #18a058;
  background: #f0fdf4;
}
.cal-cell--vacio {
  background: transparent;
  border-color: transparent;
}

/* Número del día */
.cal-dia-num {
  font-size: 0.78rem;
  font-weight: 600;
  color: hsl(var(--foreground));
  line-height: 1;
  margin-bottom: 2px;
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  flex-shrink: 0;
}
.cal-dia-num--hoy {
  background: #18a058;
  color: #fff;
}

/* Chips de jornada */
.jorn-chip {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 2px 4px;
  border-radius: 4px;
  border-left: 3px solid #64748b;
  cursor: pointer;
  transition: filter 0.12s;
  overflow: hidden;
  min-width: 0;
}
.jorn-chip:hover {
  filter: brightness(0.9);
}
.jorn-chip__icon {
  font-size: 0.65rem;
  flex-shrink: 0;
}
.jorn-chip__label {
  font-size: 0.63rem;
  font-weight: 600;
  color: hsl(var(--foreground));
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}
.jorn-chip__cnt {
  font-size: 0.60rem;
  color: hsl(var(--muted-foreground));
  flex-shrink: 0;
  white-space: nowrap;
}
.jorn-chip--mas {
  font-size: 0.60rem;
  font-weight: 600;
  color: hsl(var(--muted-foreground));
  background: hsl(var(--muted));
  border-left-color: hsl(var(--border));
  padding: 2px 6px;
  cursor: default;
}

/* ── Resumen ───────────────────────────────────────────────────────────── */
.cal-resumen {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 14px;
  padding-top: 10px;
  border-top: 1px solid hsl(var(--border));
}
</style>
