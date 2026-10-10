<script lang="ts" setup>
import { h, ref, computed, onMounted } from 'vue';
import type { DataTableColumns } from 'naive-ui';
import { Page, useVbenModal } from '@vben/common-ui';
import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { dialog, message } from '#/adapter/naive';
import type { SelectOption } from 'naive-ui';
import {
  NTabs, NTabPane, NButton, NInput, NSelect, NCard, NTag, NSpin, NEmpty,
  NDrawer, NDrawerContent, NDataTable, NProgress, NGrid, NGridItem,
  NStatistic, NSpace, NAvatar, NDivider,
} from 'naive-ui';

import type {
  RondaVerificacionDto, VerificacionPresenciaDto, RegistroAsistenciaDto,
  CodigoQrDto, EstadoRonda, EstadisticasMonitoreoDto,
} from '#/api/sisu/monitoreo';
import {
  getRondas, crearRonda, iniciarRonda, cerrarRonda, cancelarRonda,
  getVerificacionesByRonda, marcarAusente,
  listarQrs, crearQrParaLugar, regenerarQr, toggleQr,
  getAsistenciaJornada, inicializarRegistrosJornada,
  getEstadisticasMonitoreo,
} from '#/api/sisu/monitoreo';
import { getLugaresDisponibles } from '#/api/sisu/jornada';
import { getJornadasActivas } from '#/api/sisu/jornada';
import type { LugarSelectDto, JornadaDto } from '#/api/sisu/jornada';

import {
  useRondaColumns, ESTADO_RONDA_META, TIPO_RONDA_META,
  ESTADO_VERIF_META, ESTADO_ENTRADA_META, METODO_VERIF_META,
  type RondaActionParams,
} from './data';
import FormRonda              from './modules/form-ronda.vue';
import FormConfirmarPresencia from './modules/form-confirmar-presencia.vue';
import FormMovimiento         from './modules/form-movimiento.vue';
import ConfiguracionMonitoreo from './modules/configuracion-monitoreo.vue';
import PanelJustificaciones   from './modules/panel-justificaciones.vue';

// ══ Modales ══════════════════════════════════════════════════════════════════
const [FormRondaModal,    formRondaApi]    = useVbenModal({ connectedComponent: FormRonda,              destroyOnClose: true });
const [FormConfirmarModal, formConfirmarApi] = useVbenModal({ connectedComponent: FormConfirmarPresencia, destroyOnClose: true });
const [FormMovimientoModal, formMovimientoApi] = useVbenModal({ connectedComponent: FormMovimiento,        destroyOnClose: true });

// ══ Stats ══════════════════════════════════════════════════════════════════
const stats        = ref<EstadisticasMonitoreoDto | null>(null);
const loadingStats = ref(false);
async function cargarStats() {
  loadingStats.value = true;
  try { stats.value = await getEstadisticasMonitoreo(); }
  catch { /* silencioso */ }
  finally { loadingStats.value = false; }
}

// ══ TAB 1 — RONDAS ════════════════════════════════════════════════════════
const ESTADO_OPTIONS: SelectOption[] = [
  { label: 'Todos',       value: '' },
  { label: '⏳ Pendiente', value: 'PENDIENTE'  },
  { label: '▶ En curso',  value: 'EN_CURSO'   },
  { label: '✓ Completada',value: 'COMPLETADA' },
  { label: '✕ Cancelada', value: 'CANCELADA'  },
];

const filtros = ref({ q: '', estado: '', fecha: null as string | null });
function limpiarFiltros() { filtros.value = { q: '', estado: '', fecha: null }; gridApi.query(); }

function onRondaAction({ code, row }: RondaActionParams) {
  switch (code) {
    case 'view':    abrirDrawer(row); break;
    case 'iniciar': confirmarIniciar(row); break;
    case 'cerrar':  confirmarCerrar(row);  break;
    case 'cancelar': confirmarCancelar(row); break;
  }
}

const [Grid, gridApi] = useVbenVxeGrid<RondaVerificacionDto>({
  gridOptions: {
    columns: useRondaColumns(onRondaAction),
    maxHeight: 480,
    pagerConfig: { enabled: true, pageSize: 20 },
    proxyConfig: {
      autoLoad: true,
      ajax: {
        query: async ({ page }: { page: { currentPage: number; pageSize: number } }) =>
          getRondas({
            page:   page.currentPage - 1,
            size:   page.pageSize,
            q:      filtros.value.q      || undefined,
            estado: (filtros.value.estado || undefined) as EstadoRonda | undefined,
            fecha:  filtros.value.fecha  ?? undefined,
          }),
      },
    },
    toolbarConfig: { refresh: true, zoom: true, custom: true },
  },
});

function confirmarIniciar(row: RondaVerificacionDto) {
  dialog.warning({
    title: 'Iniciar Ronda', content: `¿Iniciar "${row.nombre ?? 'Ronda'}"? Se notificará al personal.`,
    positiveText: 'Iniciar', negativeText: 'Cancelar',
    onPositiveClick: async () => {
      await iniciarRonda(row.idRondaVerificacion);
      message.success('Ronda iniciada');
      gridApi.query(); cargarStats();
      if (rondaSeleccionada.value?.idRondaVerificacion === row.idRondaVerificacion)
        await recargarDrawer(row.idRondaVerificacion);
    },
  });
}
function confirmarCerrar(row: RondaVerificacionDto) {
  dialog.warning({
    title: 'Cerrar Ronda',
    content: 'Las verificaciones PENDIENTES se marcarán como SIN_RESPUESTA. ¿Confirmar?',
    positiveText: 'Cerrar', negativeText: 'Cancelar',
    onPositiveClick: async () => {
      await cerrarRonda(row.idRondaVerificacion);
      message.success('Ronda cerrada');
      gridApi.query(); cargarStats();
      if (rondaSeleccionada.value?.idRondaVerificacion === row.idRondaVerificacion)
        drawerVisible.value = false;
    },
  });
}
function confirmarCancelar(row: RondaVerificacionDto) {
  dialog.warning({
    title: 'Cancelar Ronda', content: `¿Cancelar "${row.nombre ?? 'Ronda'}"?`,
    positiveText: 'Cancelar ronda', negativeText: 'No',
    onPositiveClick: async () => {
      await cancelarRonda(row.idRondaVerificacion);
      message.success('Ronda cancelada');
      gridApi.query(); cargarStats();
      if (rondaSeleccionada.value?.idRondaVerificacion === row.idRondaVerificacion)
        drawerVisible.value = false;
    },
  });
}

// ── Drawer detalle ronda ──────────────────────────────────────────────────
const drawerVisible      = ref(false);
const rondaSeleccionada  = ref<RondaVerificacionDto | null>(null);
const verificaciones     = ref<VerificacionPresenciaDto[]>([]);
const loadingDrawer      = ref(false);

async function abrirDrawer(row: RondaVerificacionDto) {
  rondaSeleccionada.value = row;
  drawerVisible.value     = true;
  await recargarDrawer(row.idRondaVerificacion);
}
async function recargarDrawer(id: number) {
  loadingDrawer.value = true;
  try { verificaciones.value = await getVerificacionesByRonda(id); }
  finally { loadingDrawer.value = false; }
}

function abrirConfirmar(v: VerificacionPresenciaDto) {
  formConfirmarApi.setData({
    idVerificacion: v.idVerificacionPresencia,
    nombrePersonal: v.nombrePersonal,
    tipoPersonal:   v.tipoPersonal,
    estadoActual:   v.estadoVerificacion,
  }).open();
}
async function onConfirmarSuccess() {
  if (rondaSeleccionada.value)
    await recargarDrawer(rondaSeleccionada.value.idRondaVerificacion);
  gridApi.query();
}

function confirmarMarcarAusente(v: VerificacionPresenciaDto) {
  dialog.warning({
    title: 'Marcar Ausente',
    content: `¿Marcar a ${v.nombrePersonal} como AUSENTE en esta ronda?`,
    positiveText: 'Marcar Ausente', negativeText: 'Cancelar',
    onPositiveClick: async () => {
      await marcarAusente(v.idVerificacionPresencia);
      message.warning(`${v.nombrePersonal} marcado como Ausente`);
      await recargarDrawer(rondaSeleccionada.value!.idRondaVerificacion);
      gridApi.query();
    },
  });
}

const columnasVerificaciones = computed<DataTableColumns<VerificacionPresenciaDto>>(() => [
  {
    title: 'Personal', key: 'nombrePersonal',
    render: (row) => h('div', [
      h('div', { style: { fontWeight: 600, fontSize: '0.83rem' } }, row.nombrePersonal),
      h(NTag, {
        type: row.tipoPersonal === 'INTERNO' ? 'info' : 'error',
        size: 'tiny', round: true, style: { marginTop: '2px' },
      }, () => row.tipoPersonal),
    ]),
  },
  {
    title: 'Estado', key: 'estadoVerificacion', width: 125,
    render: (row) => {
      const m = ESTADO_VERIF_META[row.estadoVerificacion];
      return h(NTag, { type: m?.type ?? 'default', size: 'small', round: true }, () => m?.label);
    },
  },
  {
    title: 'Método', key: 'metodoVerificacion', width: 110,
    render: (row) => {
      if (!row.metodoVerificacion) return h('span', { style: { color: '#94a3b8' } }, '—');
      const m = METODO_VERIF_META[row.metodoVerificacion];
      return h('span', { style: { fontSize: '0.8rem', color: m?.color } }, m?.label);
    },
  },
  {
    title: 'Resp.', key: 'tiempoRespuestaMinutos', width: 70, align: 'center' as const,
    render: (row) => h('span', { style: { fontSize: '0.8rem' } },
      row.tiempoRespuestaMinutos != null ? `${row.tiempoRespuestaMinutos}m` : '—'),
  },
  {
    title: 'Acciones', key: '_acc', width: 130,
    render: (row) => row.estadoVerificacion === 'PENDIENTE'
      ? h(NSpace, { size: 4 }, () => [
          h(NButton, {
            size: 'tiny', type: 'success', ghost: true,
            onClick: () => abrirConfirmar(row),
          }, () => '✓ Presente'),
          h(NButton, {
            size: 'tiny', type: 'error', ghost: true,
            onClick: () => confirmarMarcarAusente(row),
          }, () => '✗'),
        ])
      : h('span', { style: { color: '#94a3b8', fontSize: '0.75rem' } }, 'Procesado'),
  },
]);

// ══ TAB 2 — QR CODES ══════════════════════════════════════════════════════
const lugares         = ref<LugarSelectDto[]>([]);
const qrs             = ref<CodigoQrDto[]>([]);
const loadingQr       = ref(false);

const lugaresConQr = computed(() =>
  lugares.value.map(l => ({
    ...l,
    qr: qrs.value.find(q => q.idLugar === l.idLugar) ?? null,
  }))
);

async function cargarQrs() {
  loadingQr.value = true;
  try {
    const [ls, qs] = await Promise.all([getLugaresDisponibles(), listarQrs()]);
    lugares.value = ls;
    qrs.value     = qs;
  } finally {
    loadingQr.value = false;
  }
}

async function handleCrearQr(idLugar: number) {
  try {
    await crearQrParaLugar(idLugar);
    message.success('QR creado correctamente');
    await cargarQrs();
  } catch (e: any) { message.error(e?.message ?? 'Error al crear QR'); }
}

async function handleRegenerarQr(idLugar: number, nombreLugar: string) {
  dialog.warning({
    title: 'Regenerar QR',
    content: `¿Regenerar el QR de "${nombreLugar}"? El código anterior quedará inválido.`,
    positiveText: 'Regenerar', negativeText: 'Cancelar',
    onPositiveClick: async () => {
      await regenerarQr(idLugar);
      message.success('QR regenerado');
      await cargarQrs();
    },
  });
}

async function handleToggleQr(idLugar: number) {
  await toggleQr(idLugar);
  await cargarQrs();
}

function copiarCodigo(codigo: string) {
  navigator.clipboard.writeText(codigo);
  message.success('Código copiado al portapapeles');
}

// ══ TAB 3 — ASISTENCIA ════════════════════════════════════════════════════
const jornadasOpts       = ref<SelectOption[]>([]);
const idJornadaAsist     = ref<number | null>(null);
const asistencia         = ref<RegistroAsistenciaDto[]>([]);
const loadingAsistencia  = ref(false);

async function cargarJornadasParaAsistencia() {
  try {
    const lista = await getJornadasActivas();
    jornadasOpts.value = lista.map((j: JornadaDto) => ({
      label: `${j.nombre ?? j.tipoJornada} · ${j.fechaInicio}`,
      value: j.idJornada,
    }));
  } catch { jornadasOpts.value = []; }
}

async function cargarAsistencia() {
  if (!idJornadaAsist.value) { message.warning('Selecciona una jornada'); return; }
  loadingAsistencia.value = true;
  try { asistencia.value = await getAsistenciaJornada(idJornadaAsist.value); }
  finally { loadingAsistencia.value = false; }
}

async function handleInicializarRegistros() {
  if (!idJornadaAsist.value) { message.warning('Selecciona una jornada'); return; }
  await inicializarRegistrosJornada(idJornadaAsist.value);
  message.success('Registros inicializados');
  await cargarAsistencia();
}

function abrirMovimiento(
  tipo: 'entrada' | 'salida',
  row: RegistroAsistenciaDto,
) {
  formMovimientoApi.setData({
    idInternoJornada: row.tipoPersonal === 'INTERNO'
      ? (row as any).idInternoJornada : undefined,
    idTitularJornada: row.tipoPersonal === 'TITULAR'
      ? (row as any).idTitularJornada : undefined,
    nombrePersonal: row.nombrePersonal,
    tipoPersonal:   row.tipoPersonal,
    tipoMovimiento: tipo,
  }).open();
}

const columnasAsistencia = computed<DataTableColumns<RegistroAsistenciaDto>>(() => [
  {
    title: 'Personal', key: 'nombrePersonal',
    render: (row) => h('div', [
      h('div', { style: { fontWeight: 600, fontSize: '0.83rem' } }, row.nombrePersonal),
      h(NTag, {
        type: row.tipoPersonal === 'INTERNO' ? 'info' : 'error',
        size: 'tiny', round: true, style: { marginTop: '2px' },
      }, () => row.tipoPersonal),
    ]),
  },
  {
    title: 'Entrada esp.', key: 'horaEntradaEsperada', width: 105, align: 'center' as const,
    render: (row) => h('span', { style: { fontSize: '0.82rem' } },
      row.horaEntradaEsperada ? String(row.horaEntradaEsperada).slice(0,5) : '—'),
  },
  {
    title: 'Entrada real', key: 'horaEntradaReal', width: 115, align: 'center' as const,
    render: (row) => {
      if (!row.horaEntradaReal) return h('span', { style: { color: '#ef4444', fontSize: '0.82rem' } }, '—');
      const hora = String(row.horaEntradaReal).slice(0, 5);
      const m = row.estadoEntrada ? ESTADO_ENTRADA_META[row.estadoEntrada] : null;
      return h('div', [
        h('div', { style: { fontSize: '0.82rem', fontWeight: 600 } }, hora),
        m ? h(NTag, { type: m.type, size: 'tiny', round: true }, () => m.label) : null,
        row.minutosTardanza > 0
          ? h('div', { style: { fontSize: '0.72rem', color: '#f59e0b' } }, `+${row.minutosTardanza}min`)
          : null,
      ]);
    },
  },
  {
    title: 'Salida real', key: 'horaSalidaReal', width: 100, align: 'center' as const,
    render: (row) => {
      if (!row.horaSalidaReal) return h('span', { style: { color: '#94a3b8', fontSize: '0.82rem' } }, '—');
      return h('span', { style: { fontSize: '0.82rem', fontWeight: 600 } },
        String(row.horaSalidaReal).slice(0, 5));
    },
  },
  {
    title: 'Acciones', key: '_acc', width: 160,
    render: (row) => h(NSpace, { size: 4 }, () => [
      !row.tieneEntrada
        ? h(NButton, {
            size: 'tiny', type: 'success', ghost: true,
            onClick: () => abrirMovimiento('entrada', row),
          }, () => '🟢 Entrada')
        : null,
      row.tieneEntrada && !row.tieneSalida
        ? h(NButton, {
            size: 'tiny', type: 'warning', ghost: true,
            onClick: () => abrirMovimiento('salida', row),
          }, () => '🔴 Salida')
        : null,
      row.tieneEntrada && row.tieneSalida
        ? h('span', { style: { fontSize: '0.75rem', color: '#22c55e' } }, '✓ Completo')
        : null,
    ]),
  },
]);

// ══ Ciclo de vida ══════════════════════════════════════════════════════════
onMounted(() => {
  cargarStats();
  cargarQrs();
  cargarJornadasParaAsistencia();
});
</script>

<template>
  <Page auto-content-height>

    <!-- Modales -->
    <FormRondaModal           @success="() => { gridApi.query(); cargarStats(); }" />
    <FormConfirmarModal       @success="onConfirmarSuccess" />
    <FormMovimientoModal      @success="cargarAsistencia" />

    <!-- Cabecera + stats ────────────────────────────────────────────────── -->
    <div class="mb-4 flex items-center justify-between flex-wrap gap-3">
      <div>
        <h2 class="text-lg font-bold text-gray-800 mb-0">Herramientas de Monitoreo</h2>
        <p class="text-sm text-gray-500 mt-0.5">
          Rondas de verificación, control QR, asistencia y estadísticas de presencia
        </p>
      </div>
      <div v-if="stats" class="flex gap-3 flex-wrap">
        <div class="px-4 py-2 rounded-xl border" style="background:#f0fdf4; border-color:#bbf7d0">
          <div class="text-xs text-green-600">Rondas totales</div>
          <div class="text-xl font-bold text-green-700">{{ stats.totalRondas }}</div>
        </div>
        <div class="px-4 py-2 rounded-xl border" style="background:#eff6ff; border-color:#bfdbfe">
          <div class="text-xs text-blue-600">Verificaciones</div>
          <div class="text-xl font-bold text-blue-700">{{ stats.verificacionesTotales }}</div>
        </div>
        <div class="px-4 py-2 rounded-xl border" style="background:#fefce8; border-color:#fef08a">
          <div class="text-xs text-yellow-600">Tardanzas hoy</div>
          <div class="text-xl font-bold text-yellow-700">{{ stats.tardanzasHoy }}</div>
        </div>
      </div>
    </div>

    <NTabs type="bar" animated>

      <!-- ══ TAB 1 — HISTORIAL RONDAS ══════════════════════════════════ -->
      <NTabPane name="rondas" tab="🔍 Historial Rondas" display-directive="show:lazy">

        <NCard size="small" class="mb-3">
          <div class="flex flex-wrap items-center gap-2">
            <NInput
              v-model:value="filtros.q"
              placeholder="Buscar ronda…"
              clearable style="width:200px"
              @keyup.enter="gridApi.query()"
            />
            <NSelect v-model:value="filtros.estado" :options="ESTADO_OPTIONS" style="width:150px" />
            <NInput
              v-model:value="filtros.fecha"
              placeholder="Fecha (yyyy-MM-dd)"
              maxlength="10" clearable style="width:170px"
            />
            <NButton type="primary" @click="gridApi.query()">Filtrar</NButton>
            <NButton @click="limpiarFiltros">Limpiar</NButton>
            <div class="ml-auto">
              <NButton
                type="primary"
                :style="{ background: '#7c3aed', borderColor: '#7c3aed' }"
                @click="formRondaApi.setData(null).open()"
              >
                + Nueva Ronda
              </NButton>
            </div>
          </div>
        </NCard>

        <Grid table-title="Rondas de Verificación" />
      </NTabPane>

      <!-- ══ TAB 2 — QR CODES ══════════════════════════════════════════ -->
      <NTabPane name="qr" tab="📱 Códigos QR" display-directive="show:lazy">
        <NSpin :show="loadingQr">
          <div class="flex items-center justify-between mb-4">
            <p class="text-sm text-gray-500 m-0">
              QR impreso y pegado en cada lugar físico — el personal lo escanea para verificar presencia.
            </p>
            <NButton size="small" @click="cargarQrs">↺ Actualizar</NButton>
          </div>

          <NEmpty v-if="!loadingQr && lugaresConQr.length === 0" description="Sin lugares configurados" class="py-12" />

          <NGrid :cols="3" :x-gap="16" :y-gap="16" responsive="screen" :collapsed-cols="1">
            <NGridItem v-for="item in lugaresConQr" :key="item.idLugar">
              <NCard size="small" :bordered="true" hoverable>
                <!-- Header lugar -->
                <div class="flex items-start gap-3 mb-3">
                  <NAvatar
                    :size="40" round
                    :style="{
                      background: item.qr ? '#0f766e' : '#94a3b8',
                      fontSize: '1.1rem', fontWeight: 700, flexShrink: 0,
                    }"
                  >
                    📍
                  </NAvatar>
                  <div class="flex-1 min-w-0">
                    <div class="font-bold text-sm truncate">{{ item.nombre }}</div>
                    <div class="text-xs text-gray-400 truncate">{{ item.descripcion ?? 'Sin descripción' }}</div>
                  </div>
                </div>

                <!-- Sin QR -->
                <template v-if="!item.qr">
                  <NEmpty size="small" description="Sin QR asignado" class="my-2" />
                  <NButton
                    type="primary" size="small" block
                    :style="{ background: '#0f766e', borderColor: '#0f766e' }"
                    @click="handleCrearQr(item.idLugar)"
                  >
                    + Crear QR
                  </NButton>
                </template>

                <!-- Con QR -->
                <template v-else>
                  <div class="flex items-center justify-between mb-2">
                    <NTag
                      :type="item.qr.esValido ? 'success' : 'error'"
                      size="small" round
                    >
                      {{ item.qr.esValido ? '● Activo y válido' : '✕ Inactivo/Expirado' }}
                    </NTag>
                  </div>

                  <!-- Código QR (UUID) -->
                  <div
                    class="rounded-lg p-2 mb-3 font-mono text-xs break-all cursor-pointer"
                    style="background:#f8fafc; border:1px dashed #cbd5e1; color:#475569"
                    :title="item.qr.codigo"
                    @click="copiarCodigo(item.qr.codigo)"
                  >
                    {{ item.qr.codigo.slice(0, 18) }}…
                    <span class="text-blue-400 ml-1">📋</span>
                  </div>

                  <!-- Imagen QR si existe -->
                  <div v-if="item.qr.imagenUrl" class="flex justify-center mb-3">
                    <img
                      :src="item.qr.imagenUrl"
                      alt="QR"
                      style="width:80px; height:80px; object-fit:contain; border:1px solid #e2e8f0; border-radius:8px"
                    />
                  </div>

                  <NSpace :size="6">
                    <NButton
                      size="small" type="primary" ghost
                      :style="{ color: '#0f766e', borderColor: '#0f766e' }"
                      @click="handleRegenerarQr(item.idLugar, item.nombre)"
                    >
                      ↺ Regenerar
                    </NButton>
                    <NButton
                      size="small"
                      :type="item.qr.activo ? 'warning' : 'success'"
                      ghost
                      @click="handleToggleQr(item.idLugar)"
                    >
                      {{ item.qr.activo ? 'Desactivar' : 'Activar' }}
                    </NButton>
                  </NSpace>
                </template>
              </NCard>
            </NGridItem>
          </NGrid>
        </NSpin>
      </NTabPane>

      <!-- ══ TAB 3 — ASISTENCIA ════════════════════════════════════════ -->
      <NTabPane name="asistencia" tab="📋 Asistencia" display-directive="show:lazy">

        <NCard size="small" class="mb-3">
          <div class="flex flex-wrap items-center gap-2">
            <NSelect
              v-model:value="idJornadaAsist"
              :options="jornadasOpts"
              placeholder="Seleccionar jornada…"
              filterable clearable
              style="width:320px"
            />
            <NButton type="primary" @click="cargarAsistencia">Cargar</NButton>
            <NButton
              type="info" ghost
              :disabled="!idJornadaAsist"
              @click="handleInicializarRegistros"
            >
              Inicializar registros
            </NButton>
          </div>
        </NCard>

        <NSpin :show="loadingAsistencia">
          <NEmpty
            v-if="asistencia.length === 0 && !loadingAsistencia"
            description="Selecciona una jornada y presiona Cargar"
            class="py-12"
          />
          <NDataTable
            v-else
            :columns="columnasAsistencia"
            :data="asistencia"
            size="small"
            :bordered="false"
            :striped="true"
          />
        </NSpin>
      </NTabPane>

      <!-- ══ TAB 4 — ESTADÍSTICAS ══════════════════════════════════════ -->
      <NTabPane name="estadisticas" tab="📊 Estadísticas" display-directive="show:lazy">
        <NSpin :show="loadingStats">
          <NEmpty v-if="!stats && !loadingStats" description="Sin datos" class="py-12" />

          <template v-else-if="stats">
            <NGrid :cols="4" :x-gap="12" :y-gap="12" class="mb-5" responsive="screen" :collapsed-cols="2">
              <NGridItem v-for="item in [
                { label:'Total rondas',       value: stats.totalRondas,              color:'#7c3aed' },
                { label:'Verificaciones',     value: stats.verificacionesTotales,    color:'#3b82f6' },
                { label:'Presencia confirm.', value: stats.presenciaConfirmada,      color:'#22c55e' },
                { label:'Sin respuesta',      value: stats.sinRespuesta,             color:'#f59e0b' },
              ]" :key="item.label">
                <NCard size="small" style="text-align:center" :style="{ borderTop: `3px solid ${item.color}` }">
                  <NStatistic :label="item.label" :value="item.value" />
                </NCard>
              </NGridItem>
            </NGrid>

            <NGrid :cols="2" :x-gap="16" :y-gap="12" responsive="screen" :collapsed-cols="1">
              <NGridItem>
                <NCard title="📋 Asistencia" size="small">
                  <NGrid :cols="2" :x-gap="12">
                    <NGridItem>
                      <NCard size="small" style="text-align:center; border-top:3px solid #22c55e">
                        <NStatistic label="Registros hoy" :value="stats.registrosAsistenciaHoy" />
                      </NCard>
                    </NGridItem>
                    <NGridItem>
                      <NCard size="small" style="text-align:center; border-top:3px solid #f59e0b">
                        <NStatistic label="Tardanzas hoy" :value="stats.tardanzasHoy" />
                      </NCard>
                    </NGridItem>
                  </NGrid>
                </NCard>
              </NGridItem>

              <NGridItem>
                <NCard title="✅ Tasa de presencia global" size="small">
                  <div class="py-2">
                    <div class="flex items-center justify-between mb-2">
                      <span class="text-sm text-gray-600">Confirmados vs. Total</span>
                      <span class="font-bold text-lg" :style="{
                        color: stats.porcentajePresenciaGlobal >= 80 ? '#22c55e'
                             : stats.porcentajePresenciaGlobal >= 50 ? '#f59e0b' : '#ef4444'
                      }">
                        {{ stats.porcentajePresenciaGlobal }}%
                      </span>
                    </div>
                    <NProgress
                      type="line"
                      :percentage="stats.porcentajePresenciaGlobal"
                      :color="stats.porcentajePresenciaGlobal >= 80 ? '#22c55e'
                            : stats.porcentajePresenciaGlobal >= 50 ? '#f59e0b' : '#ef4444'"
                      :height="14"
                      :show-indicator="false"
                    />
                    <div class="flex justify-between text-xs text-gray-400 mt-1">
                      <span>{{ stats.presenciaConfirmada }} presentes</span>
                      <span>{{ stats.ausencias }} ausentes</span>
                      <span>{{ stats.sinRespuesta }} sin respuesta</span>
                    </div>
                  </div>
                </NCard>
              </NGridItem>
            </NGrid>
          </template>
        </NSpin>
      </NTabPane>

      <!-- ══ TAB 5 — JUSTIFICACIONES ════════════════════════════════════ -->
      <NTabPane name="justificaciones" tab="📝 Justificaciones" display-directive="show:lazy">
        <PanelJustificaciones />
      </NTabPane>

      <!-- ══ TAB 6 — CONFIGURACIÓN ══════════════════════════════════════ -->
      <NTabPane name="configuracion" tab="⚙️ Configuración" display-directive="show:lazy">
        <ConfiguracionMonitoreo />
      </NTabPane>

    </NTabs>

    <!-- ════════════════════════════════════════════════════════════════════
         DRAWER — DETALLE RONDA
         ════════════════════════════════════════════════════════════════════ -->
    <NDrawer v-model:show="drawerVisible" :width="660" placement="right">
      <NDrawerContent :native-scrollbar="false" closable>

        <template #header>
          <div v-if="rondaSeleccionada">
            <div class="font-bold text-base">
              {{ rondaSeleccionada.nombre ?? `Ronda ${rondaSeleccionada.tipoRonda}` }}
            </div>
            <div class="flex gap-2 mt-1 flex-wrap">
              <NTag
                :type="ESTADO_RONDA_META[rondaSeleccionada.estado]?.type"
                size="small" round
              >
                {{ ESTADO_RONDA_META[rondaSeleccionada.estado]?.label }}
              </NTag>
              <NTag type="default" size="small" round>
                {{ TIPO_RONDA_META[rondaSeleccionada.tipoRonda]?.label }}
              </NTag>
              <span class="text-xs text-gray-400 self-center">
                ⏱ {{ rondaSeleccionada.timeoutMinutos }}min timeout
              </span>
            </div>
          </div>
        </template>

        <NSpin :show="loadingDrawer">
          <template v-if="rondaSeleccionada">

            <!-- Barra de progreso presencia -->
            <NCard size="small" class="mb-4" :bordered="true">
              <div class="flex items-center justify-between mb-2">
                <span class="text-sm font-semibold">Tasa de presencia</span>
                <span class="font-bold text-base" :style="{
                  color: rondaSeleccionada.porcentajePresencia >= 80 ? '#22c55e'
                       : rondaSeleccionada.porcentajePresencia >= 50 ? '#f59e0b' : '#ef4444'
                }">
                  {{ rondaSeleccionada.porcentajePresencia }}%
                </span>
              </div>
              <NProgress
                type="line"
                :percentage="rondaSeleccionada.porcentajePresencia"
                :color="rondaSeleccionada.porcentajePresencia >= 80 ? '#22c55e'
                      : rondaSeleccionada.porcentajePresencia >= 50 ? '#f59e0b' : '#ef4444'"
                :height="12" :show-indicator="false"
              />
              <div class="flex gap-4 mt-2 text-xs">
                <span class="text-green-600 font-semibold">✓ {{ rondaSeleccionada.totalPresentes }} presentes</span>
                <span class="text-red-500">✗ {{ rondaSeleccionada.totalAusentes }} ausentes</span>
                <span class="text-yellow-500">⚠ {{ rondaSeleccionada.totalSinRespuesta }} sin respuesta</span>
                <span class="text-gray-400">⏳ {{ rondaSeleccionada.totalPendientes }} pendientes</span>
              </div>
            </NCard>

            <!-- Acciones de la ronda -->
            <div
              v-if="rondaSeleccionada.estado === 'PENDIENTE' || rondaSeleccionada.estado === 'EN_CURSO'"
              class="flex gap-2 mb-4"
            >
              <NButton
                v-if="rondaSeleccionada.estado === 'PENDIENTE'"
                size="small" type="info" ghost
                @click="confirmarIniciar(rondaSeleccionada)"
              >▶ Iniciar ronda</NButton>
              <NButton
                v-if="rondaSeleccionada.estado === 'EN_CURSO'"
                size="small" type="warning" ghost
                @click="confirmarCerrar(rondaSeleccionada)"
              >🔒 Cerrar ronda</NButton>
              <NButton
                size="small" type="error" ghost
                @click="confirmarCancelar(rondaSeleccionada)"
              >✕ Cancelar</NButton>
            </div>

            <NDivider style="margin: 8px 0" />

            <!-- Tabla de verificaciones -->
            <div class="flex items-center justify-between mb-3">
              <span class="text-sm font-semibold text-gray-600">
                Verificaciones ({{ verificaciones.length }})
              </span>
            </div>

            <NEmpty
              v-if="verificaciones.length === 0"
              description="Sin verificaciones generadas aún"
              size="small" class="py-6"
            />
            <NDataTable
              v-else
              :columns="columnasVerificaciones"
              :data="verificaciones"
              size="small"
              :bordered="false"
              :striped="true"
            />
          </template>
        </NSpin>

      </NDrawerContent>
    </NDrawer>

  </Page>
</template>