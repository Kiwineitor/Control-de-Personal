<script lang="ts" setup>
import { h, ref, onMounted } from 'vue';
import { Page, useVbenModal } from '@vben/common-ui';
import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { usePreferences } from '@vben/preferences';
import { dialog, message } from '#/adapter/naive';
import type { SelectOption } from 'naive-ui';
import {
  NTabs, NTabPane, NButton, NInput, NSelect, NCard, NSpin, NEmpty,
  NStatistic, NGrid, NGridItem, NDataTable, NProgress,
} from 'naive-ui';
import type {
  ActividadDto, EstadoCatalogo, TipoActividad,
  EstadisticasActividadesDto, EstadoActividad,
} from '#/api/sisu/actividades';
import { getActividades, desactivarActividad, getEstadisticas } from '#/api/sisu/actividades';
import {
  useActividadColumns, TIPO_ACTIVIDAD_META, ESTADO_ACTIVIDAD_META,
  type ActividadActionParams,
} from './data';
import FormActividad from './modules/form-actividad.vue';
import SeguimientoJornada from './modules/seguimiento-jornada.vue';

const { isDark } = usePreferences();

// ── Modal catálogo ────────────────────────────────────────────────────────
const [FormActividadModal, formActividadApi] = useVbenModal({
  connectedComponent: FormActividad, destroyOnClose: true,
});

// ── Tabs ──────────────────────────────────────────────────────────────────
const tabActivo = ref<'catalogo' | 'seguimiento' | 'estadisticas'>('catalogo');

// ── Filtros catálogo ──────────────────────────────────────────────────────
const TIPO_OPTIONS: SelectOption[] = [
  { label: 'Todos los tipos', value: '' },
  { label: '🏥 Clínica',        value: 'CLINICA'        },
  { label: '📋 Administrativa', value: 'ADMINISTRATIVA' },
  { label: '🎓 Académica',      value: 'ACADEMICA'      },
  { label: '👁 Supervisión',    value: 'SUPERVISION'    },
  { label: '🚨 Emergencia',     value: 'EMERGENCIA'     },
  { label: '📌 Otra',           value: 'OTRA'           },
];
const ESTADO_OPTIONS: SelectOption[] = [
  { label: 'Todos',      value: ''         },
  { label: '✅ Activo',  value: 'ACTIVO'   },
  { label: '⏸ Inactivo', value: 'INACTIVO' },
];

const filtros = ref({ q: '', tipo: '', estado: '' });
function limpiarFiltros() {
  filtros.value = { q: '', tipo: '', estado: '' };
  gridApi.query();
}

// ── Grid catálogo ─────────────────────────────────────────────────────────
function onAction({ code, row }: ActividadActionParams) {
  if (code === 'edit')       formActividadApi.setData(row).open();
  if (code === 'desactivar') confirmarDesactivar(row);
}

const [Grid, gridApi] = useVbenVxeGrid<ActividadDto>({
  gridOptions: {
    columns:     useActividadColumns(onAction, isDark),
    maxHeight:   500,
    pagerConfig: { enabled: true, pageSize: 20 },
    proxyConfig: {
      autoLoad: true,
      ajax: {
        query: async ({ page }: { page: { currentPage: number; pageSize: number } }) =>
          getActividades({
            page:   page.currentPage - 1,
            size:   page.pageSize,
            q:      filtros.value.q      || undefined,
            tipo:   (filtros.value.tipo   || undefined) as TipoActividad  | undefined,
            estado: (filtros.value.estado || undefined) as EstadoCatalogo | undefined,
          }),
      },
    },
    toolbarConfig: { refresh: true, zoom: true, custom: true },
  },
});

function confirmarDesactivar(row: ActividadDto) {
  dialog.warning({
    title:        'Desactivar Actividad',
    content:      `¿Desactivar "${row.nombre}"? No podrá asignarse en nuevas jornadas, pero el historial existente se conserva.`,
    positiveText: 'Desactivar',
    negativeText: 'Cancelar',
    onPositiveClick: async () => {
      await desactivarActividad(row.idActividad);
      message.success('Actividad desactivada');
      gridApi.query();
      cargarEstadisticas();
    },
  });
}

// ── Estadísticas ──────────────────────────────────────────────────────────
const stats        = ref<EstadisticasActividadesDto | null>(null);
const loadingStats = ref(false);

async function cargarEstadisticas() {
  loadingStats.value = true;
  try   { stats.value = await getEstadisticas(); }
  catch { /* silencioso */ }
  finally { loadingStats.value = false; }
}

const columnasMasUsadas = [
  {
    title: '#', key: '_i', width: 45, align: 'center' as const,
    render: (_: any, i: number) =>
      h('span', { style: { color: 'hsl(var(--muted-foreground))', fontSize: '0.8rem' } }, i + 1),
  },
  {
    title: 'Actividad', key: 'nombre',
    render: (r: any) => {
      const meta = TIPO_ACTIVIDAD_META[r.tipo as TipoActividad];
      return h('div', [
        h('div', { style: { fontWeight: 600, fontSize: '0.83rem' } }, r.nombre),
        h('div', {
          style: {
            display: 'inline-block', marginTop: '2px',
            padding: '0 7px', borderRadius: '8px', fontSize: '0.7rem', fontWeight: 600,
            background: isDark.value ? (meta?.darkBg   ?? 'rgba(71,85,105,.18)') : (meta?.bg    ?? '#f1f5f9'),
            color:      isDark.value ? (meta?.darkColor ?? '#94a3b8')             : (meta?.color ?? '#475569'),
          },
        }, meta?.label ?? r.tipo),
      ]);
    },
  },
  {
    title: 'Veces usada', key: 'vecesUsada', width: 110, align: 'center' as const,
    render: (r: any) =>
      h('span', { style: { fontWeight: 700, color: isDark.value ? '#93c5fd' : '#1d4ed8', fontSize: '0.9rem' } },
        String(r.vecesUsada)),
  },
];

onMounted(cargarEstadisticas);
</script>

<template>
  <Page auto-content-height>
    <FormActividadModal @success="() => { gridApi.query(); cargarEstadisticas(); }" />

    <!-- Cabecera -->
    <div class="mb-4 flex items-center justify-between flex-wrap gap-2">
      <div>
        <h2 class="text-lg font-bold mb-0">GESTIÓN DE ACTIVIDADES</h2>
        <p class="text-sm text-muted-foreground mt-0.5">
          Catálogo maestro · Seguimiento por jornada · Estadísticas
        </p>
      </div>
    </div>

    <NTabs v-model:value="tabActivo" type="bar" animated>

      <!-- ══ TAB 1: CATÁLOGO ════════════════════════════════════════════ -->
      <NTabPane name="catalogo" tab="📚 Catálogo" display-directive="show:lazy">
        <NCard size="small" class="mb-3">
          <div class="flex flex-wrap items-center gap-2">
            <NInput
              v-model:value="filtros.q"
              placeholder="Buscar actividad…"
              clearable style="width:210px"
              @keyup.enter="gridApi.query()"
            />
            <NSelect v-model:value="filtros.tipo"   :options="TIPO_OPTIONS"   style="width:190px" />
            <NSelect v-model:value="filtros.estado" :options="ESTADO_OPTIONS" style="width:130px" />
            <NButton type="primary" @click="gridApi.query()">Filtrar</NButton>
            <NButton @click="limpiarFiltros">Limpiar</NButton>
            <div class="ml-auto">
              <NButton
                type="primary"
                :style="{ background: '#0f766e', borderColor: '#0f766e' }"
                @click="formActividadApi.setData(null).open()"
              >
                + Nueva Actividad
              </NButton>
            </div>
          </div>
        </NCard>
        <Grid table-title="Catálogo de Actividades" />
      </NTabPane>

      <!-- ══ TAB 2: SEGUIMIENTO ══════════════════════════════════════════ -->
      <NTabPane name="seguimiento" tab="📌 Seguimiento por Jornada" display-directive="show:lazy">
        <SeguimientoJornada />
      </NTabPane>

      <!-- ══ TAB 3: ESTADÍSTICAS ════════════════════════════════════════ -->
      <NTabPane name="estadisticas" tab="📊 Estadísticas" display-directive="show:lazy">
        <NSpin :show="loadingStats">
          <NEmpty v-if="!stats && !loadingStats" description="Sin datos de actividades aún" class="py-12" />

          <template v-else-if="stats">

            <!-- Cards de totales -->
            <NGrid :cols="5" :x-gap="12" :y-gap="12" class="mb-5" responsive="screen" :collapsed-cols="2">
              <NGridItem v-for="(item, key) in [
                { label:'Total',      value: stats.totalRegistradas, color:'#64748b' },
                { label:'Completadas',value: stats.totalCompletadas, color:'#22c55e' },
                { label:'En progreso',value: stats.totalEnProgreso,  color:'#3b82f6' },
                { label:'Pendientes', value: stats.totalPendientes,  color:'#f59e0b' },
                { label:'Canceladas', value: stats.totalCanceladas,  color:'#ef4444' },
              ]" :key="key">
                <NCard
                  size="small"
                  style="text-align:center"
                  :style="{ borderTop: `3px solid ${item.color}` }"
                >
                  <NStatistic :label="item.label" :value="item.value" />
                </NCard>
              </NGridItem>
            </NGrid>

            <NGrid :cols="2" :x-gap="16" :y-gap="12" responsive="screen" :collapsed-cols="1">

              <!-- Por estado -->
              <NGridItem>
                <NCard title="📈 Por estado de ejecución" size="small">
                  <div class="space-y-3 py-1">
                    <div
                      v-for="item in stats.porEstado"
                      :key="item.clave"
                      class="flex items-center gap-3"
                    >
                      <div class="text-xs font-medium text-right" style="width:100px; flex-shrink:0">
                        {{ ESTADO_ACTIVIDAD_META[item.clave as EstadoActividad]?.label ?? item.clave }}
                      </div>
                      <div class="flex-1">
                        <NProgress
                          type="line"
                          :percentage="item.porcentaje"
                          :color="ESTADO_ACTIVIDAD_META[item.clave as EstadoActividad]?.color ?? '#94a3b8'"
                          :height="10"
                          :show-indicator="false"
                        />
                      </div>
                      <div class="text-xs font-bold text-right" style="width:70px; flex-shrink:0">
                        {{ item.cantidad }} ({{ item.porcentaje }}%)
                      </div>
                    </div>
                    <NEmpty v-if="stats.porEstado.length === 0" size="small" description="Sin datos" />
                  </div>
                </NCard>
              </NGridItem>

              <!-- Por tipo -->
              <NGridItem>
                <NCard title="🏷 Por tipo de actividad" size="small">
                  <div class="space-y-3 py-1">
                    <div
                      v-for="item in stats.porTipo"
                      :key="item.clave"
                      class="flex items-center gap-3"
                    >
                      <div class="text-xs font-medium text-right" style="width:100px; flex-shrink:0">
                        {{ TIPO_ACTIVIDAD_META[item.clave as TipoActividad]?.label ?? item.clave }}
                      </div>
                      <div class="flex-1">
                        <NProgress
                          type="line"
                          :percentage="item.porcentaje"
                          :color="TIPO_ACTIVIDAD_META[item.clave as TipoActividad]?.color ?? '#1d4ed8'"
                          :height="10"
                          :show-indicator="false"
                        />
                      </div>
                      <div class="text-xs font-bold text-right" style="width:70px; flex-shrink:0">
                        {{ item.cantidad }} ({{ item.porcentaje }}%)
                      </div>
                    </div>
                    <NEmpty v-if="stats.porTipo.length === 0" size="small" description="Sin datos" />
                  </div>
                </NCard>
              </NGridItem>

              <!-- Top 5 más usadas -->
              <NGridItem :span="2">
                <NCard title="🏆 Top 5 — Actividades más utilizadas en jornadas" size="small">
                  <NEmpty v-if="stats.masUtilizadas.length === 0" size="small" description="Sin datos aún" />
                  <NDataTable
                    v-else
                    :columns="columnasMasUsadas"
                    :data="stats.masUtilizadas"
                    size="small"
                    :bordered="false"
                    :striped="true"
                  />
                </NCard>
              </NGridItem>

            </NGrid>
          </template>
        </NSpin>
      </NTabPane>

    </NTabs>
  </Page>
</template>