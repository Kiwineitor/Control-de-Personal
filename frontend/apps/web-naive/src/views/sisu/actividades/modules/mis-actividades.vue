<script lang="ts" setup>
import { computed, h, onMounted, ref, watch } from 'vue';
import { Page } from '@vben/common-ui';
import { useAccessStore } from '@vben/stores';
import { usePreferences } from '@vben/preferences';
import { dialog, message } from '#/adapter/naive';
import {
  NButton, NCard, NDataTable, NEmpty, NSelect, NSpin,
  NSpace, NTag, NTooltip,
} from 'naive-ui';
import type { SelectOption } from 'naive-ui';
import {
  cancelarActividadApp,
  culminarActividadApp,
  getMisActividades,
  getMiJornada,
  type ActividadJornadaAppDto,
  type MiJornadaResponse,
} from '#/api/sisu/app';
import { useWebSocket } from '#/composables/useWebSocket';
import AutoReporteModal from '#/views/sisu/movil/actividades/modules/auto-reporte-modal.vue';
import SubirEvidenciaAppModal from '#/views/sisu/movil/actividades/modules/subir-evidencia-app-modal.vue';

const accessStore        = useAccessStore();
const { isDark }         = usePreferences();
const { notificaciones } = useWebSocket();

// ── State ─────────────────────────────────────────────────────────────────
const jornada       = ref<MiJornadaResponse | null>(null);
const lista         = ref<ActividadJornadaAppDto[]>([]);
const cargando      = ref(true);
const filtroEstado  = ref<string>('');
const filtroJornada = ref<number | null>(null);

const showRegistrar   = ref(false);
const showSubirEvid   = ref(false);
const actSeleccionada = ref<ActividadJornadaAppDto | null>(null);
const culminandoId    = ref<number | null>(null);
const cancelandoId    = ref<number | null>(null);

// ── Jornadas derivadas ────────────────────────────────────────────────────
const jornadasUnicas = computed(() => {
  const map = new Map<number, string>();
  for (const act of lista.value) {
    if (act.idJornada && !map.has(act.idJornada))
      map.set(act.idJornada, act.nombreJornada ?? `Jornada ${act.idJornada}`);
  }
  return Array.from(map.entries()).map(([id, nombre]) => ({ id, nombre }));
});
const multipleJornadas = computed(() => jornadasUnicas.value.length > 1);

// ── Filtrado ──────────────────────────────────────────────────────────────
const listaFiltrada = computed(() => {
  let items = lista.value;
  if (filtroJornada.value !== null)
    items = items.filter((a) => a.idJornada === filtroJornada.value);
  if (filtroEstado.value)
    items = items.filter((a) => a.estadoActividad === filtroEstado.value);
  return items;
});

const cntPendientes  = computed(() => lista.value.filter((a) => !a.culminada).length);
const cntCompletadas = computed(() => lista.value.filter((a) => a.culminada).length);

// ── Select options ────────────────────────────────────────────────────────
const ESTADO_OPTS: SelectOption[] = [
  { label: 'Todos los estados',  value: '' },
  { label: '⏳ Pendiente',        value: 'PENDIENTE'   },
  { label: '🔄 En progreso',      value: 'EN_PROGRESO'  },
  { label: '✅ Completada',       value: 'COMPLETADA'  },
  { label: '🏆 Validada',         value: 'VALIDADA'    },
  { label: '✕ Cancelada',        value: 'CANCELADA'   },
];

const jornadaOpts = computed<SelectOption[]>(() => [
  { label: 'Todas las jornadas', value: null as any },
  ...jornadasUnicas.value.map((j) => ({ label: j.nombre, value: j.id })),
]);

// ── Helpers ───────────────────────────────────────────────────────────────
const hFmt = (h: string | null) => h?.substring(0, 5) ?? '--:--';

const ESTADO_LABEL: Record<string, string> = {
  PENDIENTE:   'Pendiente',
  EN_PROGRESO: 'En progreso',
  COMPLETADA:  'Completada',
  VALIDADA:    'Validada',
  CANCELADA:   'Cancelada',
};

const TIPO_ICON: Record<string, string> = {
  CLINICA: '🏥', ADMINISTRATIVA: '📋', ACADEMICA: '🎓',
  SUPERVISION: '👁', EMERGENCIA: '🚨', OTRA: '📌',
};

// Badge colors (dark / light aware)
const badgeWarnBg     = computed(() => isDark.value ? 'rgba(217,119,6,.18)'  : '#fef9ec');
const badgeWarnText   = computed(() => isDark.value ? '#fbbf24'              : '#92400e');
const badgeWarnBorder = computed(() => isDark.value ? 'rgba(217,119,6,.4)'   : '#fde68a');
const badgeInfoBg     = computed(() => isDark.value ? 'rgba(59,130,246,.18)' : '#eff6ff');
const badgeInfoText   = computed(() => isDark.value ? '#93c5fd'              : '#1e40af');
const badgeInfoBorder = computed(() => isDark.value ? 'rgba(59,130,246,.4)'  : '#bfdbfe');
const badgeOkBg       = computed(() => isDark.value ? 'rgba(16,185,129,.18)' : '#f0fdf4');
const badgeOkText     = computed(() => isDark.value ? '#6ee7b7'              : '#065f46');
const badgeOkBorder   = computed(() => isDark.value ? 'rgba(16,185,129,.4)'  : '#bbf7d0');
const evidOkColor     = computed(() => isDark.value ? '#6ee7b7'              : '#059669');

function estadoBadgeStyle(e: string): Record<string, string> {
  const map: Record<string, () => Record<string, string>> = {
    PENDIENTE:   () => ({ background: badgeWarnBg.value, color: badgeWarnText.value, borderColor: badgeWarnBorder.value }),
    EN_PROGRESO: () => ({ background: badgeInfoBg.value, color: badgeInfoText.value, borderColor: badgeInfoBorder.value }),
    COMPLETADA:  () => ({ background: badgeOkBg.value,   color: badgeOkText.value,   borderColor: badgeOkBorder.value }),
    VALIDADA:    () => ({ background: badgeOkBg.value,   color: badgeOkText.value,   borderColor: badgeOkBorder.value }),
  };
  return map[e]?.() ?? { background: 'hsl(var(--muted))', color: 'hsl(var(--muted-foreground))' };
}

// ── Carga ─────────────────────────────────────────────────────────────────
async function cargar(silencioso = false) {
  if (!silencioso) cargando.value = true;
  try {
    const [j, acts] = await Promise.all([getMiJornada(), getMisActividades()]);
    jornada.value = j ?? null;
    lista.value   = acts ?? [];
    if (jornadasUnicas.value.length === 1 && filtroJornada.value === null)
      filtroJornada.value = jornadasUnicas.value[0]!.id;
  } catch {
    if (!silencioso) message.error('Error al cargar actividades');
  } finally {
    if (!silencioso) cargando.value = false;
  }
}

watch(
  () => notificaciones.value[0],
  (newest) => {
    if (!newest) return;
    if (newest.tipo === 'ACTIVIDAD_ASIGNADA' || newest.tipo === 'JORNADA_ASIGNADA')
      cargar(true);
  },
);

// ── Acciones ──────────────────────────────────────────────────────────────
async function culminar(act: ActividadJornadaAppDto) {
  culminandoId.value = act.idActividadJornada;
  try {
    const updated = await culminarActividadApp(act.idActividadJornada);
    const idx = lista.value.findIndex((a) => a.idActividadJornada === act.idActividadJornada);
    if (idx >= 0 && updated) lista.value[idx] = updated as ActividadJornadaAppDto;
    message.success('Actividad culminada ✓');
  } catch (e: any) {
    message.error(e?.message ?? 'Error al culminar');
  } finally {
    culminandoId.value = null;
  }
}

function confirmarCancelar(act: ActividadJornadaAppDto) {
  dialog.warning({
    title:        'Suspender actividad',
    content:      `¿Suspender "${act.nombreActividad}"? Quedará marcada como cancelada.`,
    positiveText: 'Suspender',
    negativeText: 'Volver',
    onPositiveClick: async () => {
      cancelandoId.value = act.idActividadJornada;
      try {
        await cancelarActividadApp(act.idActividadJornada, 'Suspendida por el usuario');
        const idx = lista.value.findIndex((a) => a.idActividadJornada === act.idActividadJornada);
        if (idx >= 0) lista.value[idx] = { ...lista.value[idx]!, estadoActividad: 'CANCELADA', culminada: false };
        message.success('Actividad suspendida');
      } catch (e: any) {
        message.error(e?.message ?? 'Error al suspender');
      } finally {
        cancelandoId.value = null;
      }
    },
  });
}

async function verEvidencia(act: ActividadJornadaAppDto) {
  if (!act.evidenciaDownloadUrl) return;
  try {
    const resp = await fetch(act.evidenciaDownloadUrl, {
      headers: { Authorization: `Bearer ${accessStore.accessToken}` },
    });
    if (!resp.ok) throw new Error();
    const blob = await resp.blob();
    const url  = URL.createObjectURL(blob);
    window.open(url, '_blank');
    setTimeout(() => URL.revokeObjectURL(url), 15_000);
  } catch {
    message.error('No se pudo abrir la evidencia');
  }
}

function abrirSubirEvidencia(act: ActividadJornadaAppDto) {
  actSeleccionada.value = act;
  showSubirEvid.value   = true;
}

function onActividadCreada(act: ActividadJornadaAppDto) { lista.value.unshift(act); }
function onEvidenciaSubida(act: ActividadJornadaAppDto) {
  const idx = lista.value.findIndex((a) => a.idActividadJornada === act.idActividadJornada);
  if (idx >= 0) lista.value[idx] = act;
  actSeleccionada.value = null;
}

onMounted(cargar);

// ── Columnas de la tabla (escritorio) ─────────────────────────────────────
const columnas = computed(() => {
  const cols: any[] = [
    {
      title: 'Estado',
      key:   'estadoActividad',
      width: 130,
      render: (row: ActividadJornadaAppDto) =>
        h('span', { class: 'tbl-badge', style: estadoBadgeStyle(row.estadoActividad) },
          ESTADO_LABEL[row.estadoActividad] ?? row.estadoActividad),
    },
    {
      title:    'Actividad',
      key:      'nombreActividad',
      minWidth: 210,
      render: (row: ActividadJornadaAppDto) =>
        h('div', { class: 'tbl-act' }, [
          h('div', { class: 'tbl-act__nombre' }, row.nombreActividad),
          h('span', { class: 'tbl-act__tipo' },
            `${TIPO_ICON[row.tipoActividad] ?? '📌'} ${row.tipoActividad}`),
        ]),
    },
  ];

  if (multipleJornadas.value) {
    cols.push({
      title: 'Jornada',
      key:   'nombreJornada',
      width: 160,
      render: (row: ActividadJornadaAppDto) =>
        h('span', { style: { fontSize: '0.82rem' } }, row.nombreJornada ?? '—'),
    });
  }

  cols.push(
    {
      title:  'Horario',
      key:    'horario',
      width:  115,
      render: (row: ActividadJornadaAppDto) =>
        row.horaInicio
          ? h('span', { style: { fontSize: '0.82rem', fontVariantNumeric: 'tabular-nums' } },
              `${row.horaInicio.substring(0, 5)} — ${row.horaFin?.substring(0, 5) ?? '?'}`)
          : h('span', { style: { color: '#94a3b8' } }, '—'),
    },
    {
      title:  'Evidencia',
      key:    'evidencia',
      width:  105,
      align:  'center' as const,
      render: (row: ActividadJornadaAppDto) =>
        row.evidenciaUrl
          ? h('span', {
              class:   'tbl-evid-link',
              onClick: () => verEvidencia(row),
            }, '📎 Ver')
          : h('span', { class: 'tbl-evid-none' }, 'Sin adjunto'),
    },
    {
      title:  'Observaciones',
      key:    'observaciones',
      width:  170,
      render: (row: ActividadJornadaAppDto) =>
        row.observaciones
          ? h('span', { class: 'tbl-obs' }, row.observaciones)
          : h('span', { style: { color: '#94a3b8' } }, '—'),
    },
    {
      title:  'Acciones',
      key:    'acciones',
      width:  230,
      align:  'center' as const,
      fixed:  'right' as const,
      render: (row: ActividadJornadaAppDto) => {
        const canAct = !row.culminada
          && row.estadoActividad !== 'VALIDADA'
          && row.estadoActividad !== 'CANCELADA';
        return h(NSpace, { size: 4, justify: 'center' }, () => [
          row.estadoActividad !== 'VALIDADA' && row.estadoActividad !== 'CANCELADA'
            ? h(NButton, {
                size: 'tiny', secondary: true, type: 'primary',
                onClick: () => abrirSubirEvidencia(row),
              }, () => row.evidenciaUrl ? 'Cambiar evid.' : 'Subir evid.')
            : null,
          canAct
            ? h(NButton, {
                size:    'tiny',
                type:    'primary',
                loading: culminandoId.value === row.idActividadJornada,
                onClick: () => culminar(row),
              }, () => '✓ Culminar')
            : null,
          canAct
            ? h(NButton, {
                size:    'tiny',
                type:    'error',
                ghost:   true,
                loading: cancelandoId.value === row.idActividadJornada,
                onClick: () => confirmarCancelar(row),
              }, () => 'Suspender')
            : null,
          row.estadoActividad === 'VALIDADA'
            ? h(NTag, { type: 'success', size: 'small', round: true }, () => '✅ Validada')
            : null,
        ]);
      },
    },
  );

  return cols;
});
</script>

<template>
  <Page auto-content-height>

    <!-- ── Barra de título + acciones ─────────────────────────────────────── -->
    <div class="page-header">
      <div class="page-header__info">
        <h2 class="page-header__title">Mis Actividades</h2>
        <p v-if="jornada" class="page-header__sub">
          <span class="live-dot" />
          {{ jornada.nombre }}
          &nbsp;·&nbsp;{{ hFmt(jornada.horaInicio) }} — {{ hFmt(jornada.horaFin) }}
          <span v-if="jornada.nombreLugar">&nbsp;·&nbsp;{{ jornada.nombreLugar }}</span>
        </p>
        <p v-else class="page-header__sub">Sin jornada activa hoy</p>
      </div>
      <div class="page-header__right">
        <NTooltip trigger="hover">
          <template #trigger>
            <NButton secondary circle @click="cargar(true)">
              <template #icon>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="width:16px;height:16px">
                  <path d="M23 4v6h-6"/><path d="M1 20v-6h6"/>
                  <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
                </svg>
              </template>
            </NButton>
          </template>
          Actualizar
        </NTooltip>
        <NButton
          v-if="jornada"
          type="primary"
          :style="{ background: '#0f766e', borderColor: '#0f766e' }"
          @click="showRegistrar = true"
        >
          + Registrar actividad
        </NButton>
      </div>
    </div>

    <!-- ── Resumen rápido ─────────────────────────────────────────────────── -->
    <div v-if="lista.length > 0" class="summary-row">
      <div class="summary-chip summary-chip--total">
        <span class="summary-chip__num">{{ lista.length }}</span>
        <span class="summary-chip__label">Total</span>
      </div>
      <div class="summary-chip summary-chip--warn">
        <span class="summary-chip__num">{{ cntPendientes }}</span>
        <span class="summary-chip__label">Pendientes</span>
      </div>
      <div class="summary-chip summary-chip--ok">
        <span class="summary-chip__num">{{ cntCompletadas }}</span>
        <span class="summary-chip__label">Completadas</span>
      </div>
    </div>

    <!-- ── Filtros ─────────────────────────────────────────────────────────── -->
    <NCard v-if="jornada" size="small" class="filter-card">
      <div class="filter-row">
        <NSelect
          v-if="multipleJornadas"
          v-model:value="filtroJornada"
          :options="jornadaOpts"
          placeholder="Jornada"
          style="min-width: 220px; flex: 1"
        />
        <NSelect
          v-model:value="filtroEstado"
          :options="ESTADO_OPTS"
          placeholder="Estado"
          style="min-width: 180px; flex: 1"
        />
        <NButton
          v-if="filtroEstado || filtroJornada !== null"
          secondary
          @click="filtroEstado = ''; filtroJornada = null"
        >
          Limpiar filtros
        </NButton>
      </div>
    </NCard>

    <!-- ── Contenido principal ────────────────────────────────────────────── -->
    <NSpin :show="cargando">

      <!-- Sin jornada -->
      <div v-if="!cargando && !jornada" class="empty-wrap">
        <NEmpty description="No tienes jornada activa para hoy">
          <template #icon><span style="font-size:48px">📋</span></template>
          <template #extra>
            <NButton secondary @click="cargar()">Reintentar</NButton>
          </template>
        </NEmpty>
      </div>

      <template v-else-if="!cargando && jornada">

        <!-- Empty filtrado -->
        <div v-if="listaFiltrada.length === 0" class="empty-wrap">
          <NEmpty description="Ninguna actividad en este filtro">
            <template #icon><span style="font-size:44px">🗂</span></template>
          </NEmpty>
        </div>

        <!-- ── Tabla desktop ──────────────────────────────────────────────── -->
        <div v-else class="table-wrap">
          <NDataTable
            :columns="columnas"
            :data="listaFiltrada"
            :bordered="false"
            :striped="true"
            size="small"
            :scroll-x="multipleJornadas ? 1050 : 900"
            :pagination="{ pageSize: 20, showSizePicker: true, pageSizes: [10, 20, 50] }"
          />
        </div>

        <!-- ── Cards móvil (< 640 px) ─────────────────────────────────────── -->
        <div v-if="listaFiltrada.length > 0" class="card-list">
          <div
            v-for="act in listaFiltrada"
            :key="act.idActividadJornada"
            class="m-card"
          >
            <div class="m-card__head">
              <span class="m-card__badge" :style="estadoBadgeStyle(act.estadoActividad)">
                {{ ESTADO_LABEL[act.estadoActividad] ?? act.estadoActividad }}
              </span>
              <span class="m-card__tipo">
                {{ TIPO_ICON[act.tipoActividad] ?? '📌' }} {{ act.tipoActividad }}
              </span>
            </div>
            <p class="m-card__nombre">{{ act.nombreActividad }}</p>
            <p v-if="act.horaInicio" class="m-card__meta">
              ⏰ {{ hFmt(act.horaInicio) }} — {{ hFmt(act.horaFin) ?? '?' }}
            </p>
            <p v-if="multipleJornadas && act.nombreJornada" class="m-card__meta">
              📅 {{ act.nombreJornada }}
            </p>
            <p v-if="act.observaciones" class="m-card__obs">{{ act.observaciones }}</p>
            <div class="m-card__evid">
              <span v-if="!act.evidenciaUrl" class="m-evid-none">Sin evidencia</span>
              <button v-else class="m-evid-link" @click="verEvidencia(act)">📎 Ver evidencia</button>
            </div>
            <div class="m-card__actions">
              <NButton
                v-if="act.estadoActividad !== 'VALIDADA' && act.estadoActividad !== 'CANCELADA'"
                block
                @click="abrirSubirEvidencia(act)"
              >
                📎 {{ act.evidenciaUrl ? 'Cambiar evidencia' : 'Subir evidencia' }}
              </NButton>
              <NButton
                v-if="!act.culminada && act.estadoActividad !== 'VALIDADA' && act.estadoActividad !== 'CANCELADA'"
                type="primary"
                block
                :loading="culminandoId === act.idActividadJornada"
                @click="culminar(act)"
              >
                ✓ Culminar actividad
              </NButton>
              <NButton
                v-if="!act.culminada && act.estadoActividad !== 'VALIDADA' && act.estadoActividad !== 'CANCELADA'"
                type="error"
                secondary
                block
                :loading="cancelandoId === act.idActividadJornada"
                @click="confirmarCancelar(act)"
              >
                Suspender
              </NButton>
            </div>
          </div>
        </div>

      </template>
    </NSpin>

    <!-- ── Modales ─────────────────────────────────────────────────────────── -->
    <AutoReporteModal v-model:show="showRegistrar" @creada="onActividadCreada" />
    <SubirEvidenciaAppModal
      v-if="actSeleccionada"
      v-model:show="showSubirEvid"
      :actividad="actSeleccionada"
      @subida="onEvidenciaSubida"
    />

  </Page>
</template>

<style scoped>
/* ── Cabecera ─────────────────────────────────────────────────────────────── */
.page-header {
  display: flex; align-items: flex-start; justify-content: space-between;
  gap: 12px; margin-bottom: 16px; flex-wrap: wrap;
}
.page-header__info  { display: flex; flex-direction: column; gap: 4px; }
.page-header__title { font-size: 20px; font-weight: 700; margin: 0; color: hsl(var(--foreground)); }
.page-header__sub   {
  font-size: 13px; color: hsl(var(--muted-foreground)); margin: 0;
  display: flex; align-items: center; gap: 6px; flex-wrap: wrap;
}
.page-header__right { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }

.live-dot {
  display: inline-block; width: 8px; height: 8px; border-radius: 50%;
  background: #22c55e; flex-shrink: 0;
  animation: pulse-dot 2s ease-in-out infinite;
}
@keyframes pulse-dot { 0%,100% { opacity:1; } 50% { opacity:.35; } }

/* ── Resumen chips ────────────────────────────────────────────────────────── */
.summary-row {
  display: flex; gap: 10px; margin-bottom: 14px; flex-wrap: wrap;
}
.summary-chip {
  display: flex; align-items: baseline; gap: 6px;
  padding: 6px 16px; border-radius: 10px;
  border: 1px solid hsl(var(--border));
  background: hsl(var(--card));
}
.summary-chip--total { border-color: hsl(var(--border)); }
.summary-chip--warn  { border-color: v-bind(badgeWarnBorder); background: v-bind(badgeWarnBg); }
.summary-chip--ok    { border-color: v-bind(badgeOkBorder);   background: v-bind(badgeOkBg); }
.summary-chip__num   { font-size: 22px; font-weight: 800; color: hsl(var(--card-foreground)); line-height:1; }
.summary-chip--warn .summary-chip__num { color: v-bind(badgeWarnText); }
.summary-chip--ok   .summary-chip__num { color: v-bind(badgeOkText); }
.summary-chip__label { font-size: 12px; font-weight: 500; color: hsl(var(--muted-foreground)); }

/* ── Filtros ──────────────────────────────────────────────────────────────── */
.filter-card  { margin-bottom: 16px; }
.filter-row   { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }

/* ── Empty ────────────────────────────────────────────────────────────────── */
.empty-wrap { padding: 48px 0; }

/* ── Tabla desktop — visible solo en pantallas anchas ────────────────────── */
.table-wrap { /* siempre visible en desktop */ }

/* ── Celdas de la tabla ──────────────────────────────────────────────────── */
.tbl-badge {
  font-size: 11.5px; font-weight: 600;
  padding: 3px 10px; border-radius: 20px;
  border: 1px solid transparent; white-space: nowrap;
}
.tbl-act { display: flex; flex-direction: column; gap: 3px; }
.tbl-act__nombre {
  font-weight: 700; font-size: 0.85rem;
  color: hsl(var(--card-foreground));
}
.tbl-act__tipo {
  font-size: 0.72rem; padding: 1px 8px; border-radius: 8px;
  background: hsl(var(--muted)); color: hsl(var(--muted-foreground));
  display: inline-block;
}
.tbl-evid-link {
  cursor: pointer; color: v-bind(evidOkColor);
  font-weight: 600; font-size: 0.82rem;
  background: none; border: none; padding: 0;
}
.tbl-evid-link:hover { text-decoration: underline; }
.tbl-evid-none { color: #94a3b8; font-size: 0.8rem; }
.tbl-obs {
  font-size: 0.8rem; color: hsl(var(--muted-foreground));
  display: -webkit-box; -webkit-line-clamp: 2; line-clamp: 2;
  -webkit-box-orient: vertical; overflow: hidden;
}

/* ── Cards móvil — ocultas en escritorio, visibles en móvil ─────────────── */
.card-list { display: none; }

@media (max-width: 640px) {
  .table-wrap { display: none; }     /* ocultar tabla en móvil */
  .card-list  { display: flex; flex-direction: column; gap: 12px; } /* mostrar cards */

  /* Ajustes header en móvil */
  .page-header { margin-bottom: 12px; }
  .page-header__right { width: 100%; justify-content: flex-end; }
  .summary-row { gap: 6px; }
  .summary-chip { flex: 1; min-width: 80px; justify-content: center; flex-direction: column; align-items: center; gap: 2px; }
  .summary-chip__num { font-size: 18px; }
}

/* ── Card móvil ───────────────────────────────────────────────────────────── */
.m-card {
  display: flex; flex-direction: column; gap: 8px;
  padding: 14px; border-radius: 14px;
  background: hsl(var(--card)); border: 1px solid hsl(var(--border));
}
.m-card__head  { display: flex; align-items: center; gap: 7px; flex-wrap: wrap; }
.m-card__badge {
  font-size: 11px; font-weight: 600;
  padding: 2px 9px; border-radius: 20px; border: 1px solid transparent;
}
.m-card__tipo  {
  font-size: 11.5px; padding: 2px 8px;
  background: hsl(var(--muted)); color: hsl(var(--muted-foreground));
  border-radius: 6px;
}
.m-card__nombre { font-size: 15px; font-weight: 700; color: hsl(var(--card-foreground)); margin: 0; }
.m-card__meta   { font-size: 12.5px; color: hsl(var(--muted-foreground)); margin: 0; }
.m-card__obs    {
  font-size: 12.5px; color: hsl(var(--muted-foreground)); margin: 0;
  display: -webkit-box; -webkit-line-clamp: 2; line-clamp: 2;
  -webkit-box-orient: vertical; overflow: hidden;
}
.m-card__evid  { font-size: 12.5px; }
.m-evid-none   { color: hsl(var(--muted-foreground)); }
.m-evid-link   {
  color: v-bind(evidOkColor); font-weight: 500; font-size: 13px;
  background: none; border: none; cursor: pointer; padding: 0;
}
.m-card__actions {
  display: flex; flex-direction: column; gap: 8px;
  padding-top: 8px; border-top: 1px solid hsl(var(--border));
}
.m-card__actions :deep(.n-button--block) { min-height: 42px; font-size: 14px; }
</style>