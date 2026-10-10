<script lang="ts" setup>
import { computed, h, ref, watch } from 'vue';
import { useVbenModal } from '@vben/common-ui';
import { useAccessStore } from '@vben/stores';
import { usePreferences } from '@vben/preferences';
import { dialog, message } from '#/adapter/naive';
import {
  NButton, NCard, NDataTable, NDropdown, NEmpty,
  NSelect, NSpace, NSpin, NTag, NTooltip,
} from 'naive-ui';
import type { SelectOption } from 'naive-ui';
import type { JornadaDto, TitularJornadaDto, InternoJornadaDto } from '#/api/sisu/jornada';
import { getJornadasActivas, getTitularesJornada, getInternosJornada } from '#/api/sisu/jornada';
import type { ActividadJornadaAppDto, EstadoActividad } from '#/api/sisu/actividades';
import {
  getActividadesAsignacion,
  cambiarEstadoActividad,
  removerActividadJornada,
} from '#/api/sisu/actividades';
import { TIPO_ACTIVIDAD_META, ESTADO_ACTIVIDAD_META } from '../data';
import FormAsignarActividad from './form-asignar-actividad.vue';

const accessStore  = useAccessStore();
const { isDark }   = usePreferences();

// Colores adaptativos dark/light para badges de tipo de persona
const titularBg    = computed(() => isDark.value ? 'rgba(220,38,38,.18)' : '#fee2e2');
const titularColor = computed(() => isDark.value ? '#fca5a5'             : '#dc2626');
const internoBg    = computed(() => isDark.value ? 'rgba(29,78,216,.18)' : '#dbeafe');
const internoColor = computed(() => isDark.value ? '#93c5fd'             : '#1d4ed8');

// Colores adaptativos para chips de resumen de estados
const chipPendBg     = computed(() => isDark.value ? 'rgba(217,119,6,.15)'  : '#fef9ec');
const chipPendColor  = computed(() => isDark.value ? '#fbbf24'              : '#92400e');
const chipPendBorder = computed(() => isDark.value ? 'rgba(217,119,6,.35)'  : '#fde68a');
const chipInfoBg     = computed(() => isDark.value ? 'rgba(59,130,246,.15)' : '#eff6ff');
const chipInfoColor  = computed(() => isDark.value ? '#93c5fd'              : '#1e40af');
const chipInfoBorder = computed(() => isDark.value ? 'rgba(59,130,246,.35)' : '#bfdbfe');
const chipOkBg       = computed(() => isDark.value ? 'rgba(16,185,129,.15)' : '#f0fdf4');
const chipOkColor    = computed(() => isDark.value ? '#6ee7b7'              : '#065f46');
const chipOkBorder   = computed(() => isDark.value ? 'rgba(16,185,129,.35)' : '#bbf7d0');
const chipValBg      = computed(() => isDark.value ? 'rgba(16,185,129,.25)' : '#d1fae5');
const chipValColor   = computed(() => isDark.value ? '#6ee7b7'              : '#065f46');
const chipValBorder  = computed(() => isDark.value ? 'rgba(16,185,129,.5)'  : '#6ee7b7');

// Color semántico para texto vacío en columnas
const emptyColor = computed(() => isDark.value ? '#64748b' : '#94a3b8');
const evidColor  = computed(() => isDark.value ? '#5eead4' : '#0f766e');

// ── Estado principal ──────────────────────────────────────────────────────
const jornadasActivas    = ref<JornadaDto[]>([]);
const jornadaSel         = ref<number | null>(null);
const actividades        = ref<ActividadJornadaAppDto[]>([]);
const cargando           = ref(false);
const cargandoJornadas   = ref(false);
const titulares          = ref<TitularJornadaDto[]>([]);
const internos           = ref<InternoJornadaDto[]>([]);
const filtroEstado       = ref<string>('');
const personaSelVal      = ref<string | null>(null);

// ── Modal asignar ─────────────────────────────────────────────────────────
const [AsignarModal, asignarApi] = useVbenModal({
  connectedComponent: FormAsignarActividad,
  destroyOnClose: true,
});

// ── Opciones ──────────────────────────────────────────────────────────────
const jornadaOpts = computed<SelectOption[]>(() =>
  jornadasActivas.value.map((j) => ({
    label: `${j.nombre ?? `Jornada #${j.idJornada}`} — ${j.horaInicio?.substring(0,5)} a ${j.horaFin?.substring(0,5)}`,
    value: j.idJornada,
  })),
);

const personaOpts = computed<SelectOption[]>(() => {
  const opts: SelectOption[] = [];
  for (const t of titulares.value) {
    opts.push({ label: `👨‍⚕️ ${t.nombreCompleto} · Titular`, value: `T:${t.idTitularJornada}:${t.nombreCompleto}` });
  }
  for (const i of internos.value) {
    opts.push({ label: `🎓 ${i.nombreCompleto} · Interno`,  value: `I:${i.idInternoJornada}:${i.nombreCompleto}` });
  }
  return opts;
});

const ESTADO_FILTER_OPTS: SelectOption[] = [
  { label: 'Todos los estados',  value: '' },
  { label: '⏳ Pendiente',        value: 'PENDIENTE'   },
  { label: '🔄 En progreso',      value: 'EN_PROGRESO'  },
  { label: '✅ Completada',       value: 'COMPLETADA'  },
  { label: '🏆 Validada',         value: 'VALIDADA'    },
  { label: '✕ Cancelada',        value: 'CANCELADA'   },
];

const CAMBIO_ESTADO_OPTS = (actual: string) =>
  ([
    { label: '⏳ Pendiente',   key: 'PENDIENTE'   },
    { label: '🔄 En progreso', key: 'EN_PROGRESO'  },
    { label: '✅ Completada',  key: 'COMPLETADA'  },
    { label: '🏆 Validada',    key: 'VALIDADA'    },
    { label: '✕ Cancelada',   key: 'CANCELADA'   },
  ] as const).filter((o) => o.key !== actual);

// ── Filtrado local ────────────────────────────────────────────────────────
const actividadesFiltradas = computed(() =>
  filtroEstado.value
    ? actividades.value.filter((a) => a.estadoActividad === filtroEstado.value)
    : actividades.value,
);

const resumenEstados = computed(() => {
  const m: Record<string, number> = {};
  for (const a of actividades.value) {
    m[a.estadoActividad] = (m[a.estadoActividad] ?? 0) + 1;
  }
  return m;
});

// ── Carga al seleccionar jornada ──────────────────────────────────────────
watch(jornadaSel, async (id) => {
  if (!id) { actividades.value = []; titulares.value = []; internos.value = []; return; }
  cargando.value    = true;
  personaSelVal.value = null;
  try {
    const [result, tits, ints] = await Promise.all([
      getActividadesAsignacion(id, { size: 300 }),
      getTitularesJornada(id),
      getInternosJornada(id),
    ]);
    actividades.value = result.items;
    titulares.value   = tits ?? [];
    internos.value    = ints ?? [];
  } catch {
    message.error('Error al cargar el seguimiento de la jornada');
  } finally {
    cargando.value = false;
  }
});

// ── Inicialización ────────────────────────────────────────────────────────
async function init() {
  cargandoJornadas.value = true;
  try { jornadasActivas.value = (await getJornadasActivas()) ?? []; }
  catch { message.error('Error al cargar jornadas activas'); }
  finally { cargandoJornadas.value = false; }
}
init();

// ── Acciones ──────────────────────────────────────────────────────────────
async function recargar() {
  if (!jornadaSel.value) return;
  cargando.value = true;
  try {
    const result = await getActividadesAsignacion(jornadaSel.value, { size: 300 });
    actividades.value = result.items;
  } catch { message.error('Error al recargar'); }
  finally { cargando.value = false; }
}

async function cambiarEstado(act: ActividadJornadaAppDto, estado: EstadoActividad) {
  try {
    await cambiarEstadoActividad(act.idActividadJornada, { estado });
    const idx = actividades.value.findIndex((a) => a.idActividadJornada === act.idActividadJornada);
    if (idx >= 0) actividades.value[idx] = { ...actividades.value[idx]!, estadoActividad: estado };
    message.success('Estado actualizado');
  } catch (e: any) {
    message.error(e?.message ?? 'Error al cambiar estado');
  }
}

function confirmarEliminar(act: ActividadJornadaAppDto) {
  dialog.warning({
    title:        'Eliminar asignación',
    content:      `¿Eliminar la actividad "${act.nombreActividad}"? Esta acción no se puede deshacer.`,
    positiveText: 'Eliminar',
    negativeText: 'Cancelar',
    onPositiveClick: async () => {
      try {
        await removerActividadJornada(act.idActividadJornada);
        actividades.value = actividades.value.filter(
          (a) => a.idActividadJornada !== act.idActividadJornada,
        );
        message.success('Actividad eliminada');
      } catch (e: any) {
        message.error(e?.message ?? 'Error al eliminar');
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

function abrirAsignar() {
  if (!personaSelVal.value) { message.warning('Selecciona una persona primero'); return; }
  const parts  = personaSelVal.value.split(':');
  const tipo   = parts[0] as 'T' | 'I';
  const id     = Number(parts[1]);
  const nombre = parts.slice(2).join(':');
  const ctx = tipo === 'T'
    ? { idTitularJornada: id, nombrePersona: nombre, tipoPersona: 'TITULAR' as const }
    : { idInternoJornada: id, nombrePersona: nombre, tipoPersona: 'INTERNO' as const };
  asignarApi.setData(ctx).open();
}

// ── Columnas de la tabla ──────────────────────────────────────────────────
const columnas = computed(() => [
  {
    title: 'Persona',
    key:   'persona',
    width: 175,
    render: (row: ActividadJornadaAppDto) => {
      const nombre = row.nombreTitular ?? row.nombreInterno ?? '—';
      const tipo   = row.nombreTitular ? 'Titular' : 'Interno';
      return h('div', [
        h('div', { style: { fontWeight: 600, fontSize: '0.84rem' } }, nombre),
        h('span', {
          style: {
            display:      'inline-block',
            marginTop:    '2px',
            padding:      '0 7px',
            borderRadius: '8px',
            fontSize:     '0.7rem',
            fontWeight:   600,
            background:   tipo === 'Titular' ? titularBg.value : internoBg.value,
            color:        tipo === 'Titular' ? titularColor.value : internoColor.value,
          },
        }, tipo),
      ]);
    },
  },
  {
    title:    'Actividad',
    key:      'nombreActividad',
    minWidth: 200,
    render: (row: ActividadJornadaAppDto) => {
      const meta = TIPO_ACTIVIDAD_META[row.tipoActividad];
      return h('div', [
        h('div', { style: { fontWeight: 600, fontSize: '0.84rem' } }, row.nombreActividad),
        h('span', {
          style: {
            display:      'inline-block',
            marginTop:    '3px',
            padding:      '1px 8px',
            borderRadius: '8px',
            fontSize:     '0.7rem',
            fontWeight:   600,
            background:   meta?.bg    ?? '#f1f5f9',
            color:        meta?.color ?? '#475569',
          },
        }, meta?.label ?? row.tipoActividad),
      ]);
    },
  },
  {
    title:  'Estado',
    key:    'estadoActividad',
    width:  130,
    render: (row: ActividadJornadaAppDto) => {
      const m = ESTADO_ACTIVIDAD_META[row.estadoActividad as keyof typeof ESTADO_ACTIVIDAD_META];
      return h(NTag, { type: m?.type ?? 'default', size: 'small', round: true }, () => m?.label ?? row.estadoActividad);
    },
  },
  {
    title:  'Horario',
    key:    'horario',
    width:  110,
    render: (row: ActividadJornadaAppDto) =>
      row.horaInicio
        ? h('span', { style: { fontSize: '0.82rem' } },
            `${row.horaInicio.substring(0,5)} — ${row.horaFin?.substring(0,5) ?? '?'}`)
        : h('span', { style: { color: emptyColor.value } }, '—'),
  },
  {
    title:  'Evidencia',
    key:    'evidencia',
    width:  105,
    align:  'center' as const,
    render: (row: ActividadJornadaAppDto) =>
      row.evidenciaUrl
        ? h('span', {
            style:   { cursor: 'pointer', color: evidColor.value, fontWeight: 600, fontSize: '0.82rem' },
            onClick: () => verEvidencia(row),
          }, '📎 Ver')
        : h('span', { style: { color: emptyColor.value, fontSize: '0.8rem' } }, 'Sin adjunto'),
  },
  {
    title:  'Observaciones',
    key:    'observaciones',
    width:  160,
    render: (row: ActividadJornadaAppDto) =>
      row.observaciones
        ? h('span', { style: { fontSize: '0.8rem', color: 'hsl(var(--muted-foreground))' } }, row.observaciones)
        : h('span', { style: { color: emptyColor.value } }, '—'),
  },
  {
    title: 'Acciones',
    key:   'acciones',
    width: 155,
    align: 'center' as const,
    fixed: 'right' as const,
    render: (row: ActividadJornadaAppDto) =>
      h(NSpace, { size: 4, justify: 'center' }, () => [
        h(
          NDropdown,
          {
            trigger: 'click',
            options: CAMBIO_ESTADO_OPTS(row.estadoActividad),
            onSelect: (key: string) => cambiarEstado(row, key as EstadoActividad),
          },
          () => h(NButton, { size: 'tiny', ghost: true }, () => 'Estado ▾'),
        ),
        h(
          NButton,
          { size: 'tiny', type: 'error', ghost: true, onClick: () => confirmarEliminar(row) },
          () => 'Remover',
        ),
      ]),
  },
]);
</script>

<template>
  <div class="seg-wrap">

    <!-- ── Barra de control ─────────────────────────────────────────────── -->
    <NCard size="small" class="mb-3">
      <div class="seg-toolbar">

        <!-- Selector de jornada -->
        <div class="seg-toolbar__group">
          <span class="seg-toolbar__label">Jornada activa</span>
          <NSelect
            v-model:value="jornadaSel"
            :options="jornadaOpts"
            :loading="cargandoJornadas"
            placeholder="Seleccionar jornada…"
            clearable
            style="width: 320px"
          />
        </div>

        <!-- Filtro de estado -->
        <div class="seg-toolbar__group">
          <span class="seg-toolbar__label">Filtrar estado</span>
          <NSelect
            v-model:value="filtroEstado"
            :options="ESTADO_FILTER_OPTS"
            style="width: 180px"
          />
        </div>

        <!-- Asignar actividad a persona -->
        <div class="seg-toolbar__group" style="flex: 1">
          <span class="seg-toolbar__label">Asignar a</span>
          <div style="display: flex; gap: 8px; align-items: center; flex: 1">
            <NSelect
              v-model:value="personaSelVal"
              :options="personaOpts"
              placeholder="Seleccionar persona de la jornada…"
              :disabled="!jornadaSel"
              clearable
              filterable
              style="flex: 1; min-width: 200px"
            />
            <NButton
              type="primary"
              :disabled="!personaSelVal"
              :style="{ background: '#0f766e', borderColor: '#0f766e' }"
              @click="abrirAsignar"
            >
              + Asignar actividad
            </NButton>
          </div>
        </div>

        <!-- Recargar -->
        <NTooltip trigger="hover">
          <template #trigger>
            <NButton secondary circle :disabled="!jornadaSel" @click="recargar">
              <template #icon>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="width:15px;height:15px">
                  <path d="M23 4v6h-6"/><path d="M1 20v-6h6"/>
                  <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
                </svg>
              </template>
            </NButton>
          </template>
          Recargar
        </NTooltip>
      </div>
    </NCard>

    <!-- ── Resumen de estados ───────────────────────────────────────────── -->
    <div v-if="actividades.length > 0" class="estado-chips mb-3">
      <span
        v-for="(cnt, estado) in resumenEstados"
        :key="estado"
        class="estado-chip"
        :class="`chip--${estado}`"
      >
        {{ ESTADO_ACTIVIDAD_META[estado as keyof typeof ESTADO_ACTIVIDAD_META]?.label ?? estado }}
        <strong>{{ cnt }}</strong>
      </span>
    </div>

    <!-- ── Sin jornada seleccionada ─────────────────────────────────────── -->
    <div v-if="!jornadaSel" class="seg-empty">
      <NEmpty description="Selecciona una jornada activa para ver el seguimiento">
        <template #icon><span style="font-size: 44px">📋</span></template>
      </NEmpty>
    </div>

    <!-- ── Tabla ─────────────────────────────────────────────────────────── -->
    <NSpin v-else :show="cargando">
      <NDataTable
        :columns="columnas"
        :data="actividadesFiltradas"
        :bordered="false"
        :striped="true"
        size="small"
        :scroll-x="950"
        :pagination="{ pageSize: 20, showSizePicker: true, pageSizes: [10, 20, 50] }"
        :empty-text="filtroEstado ? 'Ninguna actividad con ese estado' : 'Sin actividades en esta jornada'"
      />
    </NSpin>

    <!-- ── Modal asignación ──────────────────────────────────────────────── -->
    <AsignarModal @success="recargar" />

  </div>
</template>

<style scoped>
.seg-wrap { display: flex; flex-direction: column; }

/* Toolbar */
.seg-toolbar {
  display: flex; flex-wrap: wrap; align-items: flex-end; gap: 16px;
}
.seg-toolbar__group {
  display: flex; flex-direction: column; gap: 4px;
}
.seg-toolbar__label {
  font-size: 11.5px; font-weight: 600;
  color: hsl(var(--muted-foreground)); letter-spacing: 0.3px;
  text-transform: uppercase;
}

/* Estado chips resumen */
.estado-chips { display: flex; flex-wrap: wrap; gap: 6px; }
.estado-chip  {
  font-size: 12px; font-weight: 500;
  padding: 3px 12px; border-radius: 20px;
  border: 1px solid transparent;
  display: inline-flex; align-items: center; gap: 5px;
}
.chip--PENDIENTE   { background: v-bind(chipPendBg);  color: v-bind(chipPendColor);  border-color: v-bind(chipPendBorder); }
.chip--EN_PROGRESO { background: v-bind(chipInfoBg);  color: v-bind(chipInfoColor);  border-color: v-bind(chipInfoBorder); }
.chip--COMPLETADA  { background: v-bind(chipOkBg);    color: v-bind(chipOkColor);    border-color: v-bind(chipOkBorder);   }
.chip--VALIDADA    { background: v-bind(chipValBg);   color: v-bind(chipValColor);   border-color: v-bind(chipValBorder);  }
.chip--CANCELADA   { background: hsl(var(--muted));   color: hsl(var(--muted-foreground)); }

/* Empty */
.seg-empty { padding: 48px 0; }
</style>