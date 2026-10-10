<script lang="ts" setup>
/**
 * Vista Principal — Gestión de Personal SISU
 * apps/web-naive/src/views/sisu/personal/index.vue
 *
 * FIX aplicados:
 *  1. VxeTable query params: tipo explícito para evitar implicit any
 *  2. NSelect: filtros boolean → string ('true'/'false'/'') + conversión al llamar API
 */
import type {
  TitularDto, InternoDto,
  ResponsableResumenDto, InternoAsignadoDto,
} from '#/api/sisu/personal';
import type { ActionParams, TitularActionCode, InternoActionCode } from './data';
import { ref, onMounted, computed } from 'vue';
import { Page, useVbenModal } from '@vben/common-ui';
import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { dialog, message } from '#/adapter/naive';
import type { SelectOption } from 'naive-ui';
import {
  NTabs, NTabPane, NButton, NSpace, NInput, NSelect,
  NCard, NTag, NSpin, NEmpty, NAvatar, NCheckbox,
  NDescriptions, NDescriptionsItem, NDivider,
  NSkeleton, NAlert,
} from 'naive-ui';
import {
  getTitulares, getInternos,
  toggleTitular, toggleInterno,
  syncTitularLondra, regenerarQrInterno,
  getTitularesActivos, getTitularesCargos, getTitularesEspecialidades,
  getInternosCarreras, getInternosUniversidades, getInternosActivos,
  getResponsableActivo, getResponsableHistorial,
  asignarResponsable, desasignarInterno,
} from '#/api/sisu/personal';
import { useTitularColumns, useInternoColumns } from './data';
import FormTitular from './modules/form-titular.vue';
import FormInterno from './modules/form-interno.vue';

// ═══════════════════════════════════════════════════════════════════════════
//  ESTADO GLOBAL
// ═══════════════════════════════════════════════════════════════════════════
// message y dialog vienen del adapter (instancias globales sin Provider)
const tabActivo      = ref<'titulares' | 'internos' | 'responsable'>('titulares');
const filtersLoading = ref(true);   // skeleton en filtros mientras cargan opciones
const pageReady      = ref(false);  // fade-in al terminar carga inicial

// ─────────────────────────────────────────────────────────────────────────
// ✅ FIX 2: opciones NSelect usan string, NO boolean
// NSelect de Naive UI solo acepta Value = string | number
// ─────────────────────────────────────────────────────────────────────────
const opcionesEstado: SelectOption[] = [
  { label: 'Todos',    value: ''      },
  { label: 'Activos',  value: 'true'  },
  { label: 'Inactivos',value: 'false' },
];

const opcionesRotacion: SelectOption[] = [
  { label: 'Todos',             value: ''      },
  { label: 'En rotación',       value: 'true'  },
  { label: 'Fuera de rotación', value: 'false' },
];

/** Convierte string de select → boolean | undefined para la API */
function parseBool(val: string): boolean | undefined {
  if (val === 'true')  return true;
  if (val === 'false') return false;
  return undefined;
}

// ═══════════════════════════════════════════════════════════════════════════
//  TAB 1 — TITULARES
// ═══════════════════════════════════════════════════════════════════════════

// ✅ FIX 2: activo es string, no boolean
const filtrosTitular = ref({
  q:            '',
  cargo:        '',
  especialidad: '',
  activo:       '' as string,  // '' | 'true' | 'false'
});

const cargos         = ref<SelectOption[]>([]);
const especialidades = ref<SelectOption[]>([]);

async function cargarFiltrosTitular() {
  const [c, e] = await Promise.all([getTitularesCargos(), getTitularesEspecialidades()]);
  cargos.value         = c.map(v => ({ label: v, value: v }));
  especialidades.value = e.map(v => ({ label: v, value: v }));
}

// Modals
const [FormTitularModal, formTitularApi] = useVbenModal({
  connectedComponent: FormTitular,
  destroyOnClose: true,
});

function abrirNuevoTitular()              { formTitularApi.setData(null).open(); }
function abrirEditarTitular(row: TitularDto) { formTitularApi.setData(row).open(); }

function onTitularAction({ code, row }: ActionParams<TitularDto, TitularActionCode>) {
  switch (code) {
    case 'edit':   abrirEditarTitular(row); break;
    case 'toggle': confirmarToggleTitular(row); break;
    case 'sync':   sincronizarTitular(row); break;
  }
}

function confirmarToggleTitular(row: TitularDto) {
  dialog.warning({
    title:        row.activo ? 'Desactivar Titular' : 'Activar Titular',
    content:      `¿${row.activo ? 'Desactivar' : 'Activar'} a ${row.nombreCompleto}?`,
    positiveText: 'Confirmar',
    negativeText: 'Cancelar',
    onPositiveClick: async () => {
      await toggleTitular(row.idTitular);
      message.success(`Titular ${row.activo ? 'desactivado' : 'activado'}`);
      gridTitularApi.query();
    },
  });
}

async function sincronizarTitular(row: TitularDto) {
  try {
    await syncTitularLondra(row.idTitular);
    message.success('Sincronizado desde Londra');
    gridTitularApi.query();
  } catch {
    message.error('Londra no disponible. Intente más tarde.');
  }
}

// ✅ FIX 1: tipo explícito en query params para evitar "implicit any"
const [GridTitular, gridTitularApi] = useVbenVxeGrid<TitularDto>({
  gridOptions: {
    columns: useTitularColumns(onTitularAction),
    maxHeight: 550,
    pagerConfig: { enabled: true, pageSize: 20 },
    proxyConfig: {
      autoLoad: true,
      ajax: {
        query: async ({ page }: { page: { currentPage: number; pageSize: number } }) => {
          return getTitulares({
            page:         page.currentPage - 1,
            size:         page.pageSize,
            q:            filtrosTitular.value.q            || undefined,
            cargo:        filtrosTitular.value.cargo        || undefined,
            especialidad: filtrosTitular.value.especialidad || undefined,
            activo:       parseBool(filtrosTitular.value.activo),
          });
        },
      },
    },
    toolbarConfig: { refresh: true, zoom: true, custom: true },
  },
});

// ═══════════════════════════════════════════════════════════════════════════
//  TAB 2 — INTERNOS
// ═══════════════════════════════════════════════════════════════════════════

// ✅ FIX 2: enRotacion y activo son string
const filtrosInterno = ref({
  q:           '',
  carrera:     '',
  universidad: '',
  enRotacion:  '' as string,
  activo:      '' as string,
});

const carreras      = ref<SelectOption[]>([]);
const universidades = ref<SelectOption[]>([]);

async function cargarFiltrosInterno() {
  const [c, u] = await Promise.all([getInternosCarreras(), getInternosUniversidades()]);
  carreras.value      = c.map(v => ({ label: v, value: v }));
  universidades.value = u.map(v => ({ label: v, value: v }));
}

const [FormInternoModal, formInternoApi] = useVbenModal({
  connectedComponent: FormInterno,
  destroyOnClose: true,
});

function abrirNuevoInterno()              { formInternoApi.setData(null).open(); }
function abrirEditarInterno(row: InternoDto) { formInternoApi.setData(row).open(); }

function onInternoAction({ code, row }: ActionParams<InternoDto, InternoActionCode>) {
  switch (code) {
    case 'edit':   abrirEditarInterno(row); break;
    case 'toggle': confirmarToggleInterno(row); break;
    case 'qr':     confirmarRegenerarQr(row); break;
  }
}

function confirmarToggleInterno(row: InternoDto) {
  dialog.warning({
    title:        row.activo ? 'Desactivar Interno' : 'Activar Interno',
    content:      `¿${row.activo ? 'Desactivar' : 'Activar'} a ${row.nombreCompleto}?`,
    positiveText: 'Confirmar',
    negativeText: 'Cancelar',
    onPositiveClick: async () => {
      await toggleInterno(row.idInterno);
      message.success(`Interno ${row.activo ? 'desactivado' : 'activado'}`);
      gridInternoApi.query();
    },
  });
}

function confirmarRegenerarQr(row: InternoDto) {
  dialog.warning({
    title:        'Regenerar QR',
    content:      `¿Regenerar el QR de ${row.nombreCompleto}? El código anterior quedará inválido.`,
    positiveText: 'Regenerar',
    negativeText: 'Cancelar',
    onPositiveClick: async () => {
      const { codigo } = await regenerarQrInterno(row.idInterno);
      message.success(`QR regenerado. Código: ${codigo}`);
    },
  });
}

// ✅ FIX 1: tipo explícito en query
const [GridInterno, gridInternoApi] = useVbenVxeGrid<InternoDto>({
  gridOptions: {
    columns: useInternoColumns(onInternoAction),
    maxHeight: 550,
    pagerConfig: { enabled: true, pageSize: 20 },
    proxyConfig: {
      autoLoad: true,
      ajax: {
        query: async ({ page }: { page: { currentPage: number; pageSize: number } }) => {
          return getInternos({
            page:        page.currentPage - 1,
            size:        page.pageSize,
            q:           filtrosInterno.value.q           || undefined,
            carrera:     filtrosInterno.value.carrera     || undefined,
            universidad: filtrosInterno.value.universidad || undefined,
            enRotacion:  parseBool(filtrosInterno.value.enRotacion),
            activo:      parseBool(filtrosInterno.value.activo),
          });
        },
      },
    },
    toolbarConfig: { refresh: true, zoom: true, custom: true },
  },
});

// ═══════════════════════════════════════════════════════════════════════════
//  TAB 3 — RESPONSABLE
// ═══════════════════════════════════════════════════════════════════════════

const responsableActual       = ref<null | ResponsableResumenDto>(null);
const historial               = ref<any[]>([]);
const loadingResponsable      = ref(false);
const titularesParaSelect     = ref<SelectOption[]>([]);
const guardandoResponsable    = ref(false);

// ── Estado del modal de asignación ────────────────────────────────────────
const mostrarModalAsignar       = ref(false);
const idTitularSeleccionado     = ref<null | number>(null);
const modoAsignacion            = ref<'todos' | 'seleccion'>('todos');
const internosDisponibles       = ref<InternoAsignadoDto[]>([]);
const internosSeleccionados     = ref<number[]>([]);
const observacionAsignacion     = ref('');
const loadingInternos           = ref(false);

// Todos seleccionados computed
const todosSeleccionados = computed(() =>
  internosDisponibles.value.length > 0 &&
  internosSeleccionados.value.length === internosDisponibles.value.length
);

async function cargarResponsable() {
  loadingResponsable.value = true;
  try {
    const [activo, hist] = await Promise.all([
      getResponsableActivo(),
      getResponsableHistorial(),
    ]);
    responsableActual.value = activo;
    historial.value         = hist;
  } finally {
    loadingResponsable.value = false;
  }
}

async function cargarTitularesParaSelect() {
  const lista = await getTitularesActivos();
  titularesParaSelect.value = lista.map((t) => ({
    label: `${t.nombreCompleto} — ${t.cargo ?? 'Sin cargo'}`,
    value: t.idTitular,
  }));
}

async function abrirModalAsignar() {
  idTitularSeleccionado.value  = null;
  modoAsignacion.value         = 'todos';
  internosSeleccionados.value  = [];
  observacionAsignacion.value  = '';
  mostrarModalAsignar.value    = true;
  // Cargar internos disponibles para selección
  loadingInternos.value = true;
  try {
    const lista = await getInternosActivos();
    internosDisponibles.value = lista.map(i => ({
      idInterno:            i.idInterno,
      idResponsableInterno: 0,
      nombreCompleto:       i.nombreCompleto,
      carrera:              i.carrera,
      universidad:          i.universidad,
      estaEnRotacion:       i.estaEnRotacion,
      fechaAsignacion:      '',
    }));
  } finally {
    loadingInternos.value = false;
  }
}

function toggleSeleccionarTodos() {
  if (todosSeleccionados.value) {
    internosSeleccionados.value = [];
  } else {
    internosSeleccionados.value = internosDisponibles.value.map(i => i.idInterno);
  }
}

async function handleAsignarResponsable() {
  if (!idTitularSeleccionado.value) {
    message.warning('Selecciona un titular responsable'); return;
  }
  if (modoAsignacion.value === 'seleccion' && internosSeleccionados.value.length === 0) {
    message.warning('Selecciona al menos un interno'); return;
  }
  guardandoResponsable.value = true;
  try {
    await asignarResponsable({
      idTitular:    idTitularSeleccionado.value,
      asignarTodos: modoAsignacion.value === 'todos',
      idInternos:   modoAsignacion.value === 'seleccion' ? internosSeleccionados.value : [],
      observaciones: observacionAsignacion.value || undefined,
    });
    message.success(
      modoAsignacion.value === 'todos'
        ? 'Responsable asignado a todos los internos activos'
        : `Responsable asignado a ${internosSeleccionados.value.length} interno(s)`
    );
    mostrarModalAsignar.value = false;
    await cargarResponsable();
  } catch {
    message.error('Error al asignar responsable');
  } finally {
    guardandoResponsable.value = false;
  }
}

async function handleDesasignar(idResponsableInterno: number, nombreInterno: string) {
  dialog.warning({
    title:        'Quitar asignación',
    content:      `¿Quitar a ${nombreInterno} de la supervisión de la responsable actual?`,
    positiveText: 'Confirmar',
    negativeText: 'Cancelar',
    onPositiveClick: async () => {
      await desasignarInterno(idResponsableInterno);
      message.success('Asignación desactivada');
      await cargarResponsable();
    },
  });
}

// ═══════════════════════════════════════════════════════════════════════════
//  CICLO DE VIDA
// ═══════════════════════════════════════════════════════════════════════════

onMounted(() => {
  // Carga paralela sin bloquear el render
  Promise.all([
    cargarFiltrosTitular(),
    cargarFiltrosInterno(),
    cargarResponsable(),
    cargarTitularesParaSelect(),
  ]).finally(() => {
    filtersLoading.value = false;
    // Pequeño delay para que el fade-in sea suave
    setTimeout(() => { pageReady.value = true; }, 80);
  });
});

// ─── Helper fecha ────────────────────────────────────────────────────────
function formatFecha(iso: string) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('es-BO', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });
}

// ─── Limpiar filtros ────────────────────────────────────────────────────
function limpiarFiltrosTitular() {
  filtrosTitular.value = { q: '', cargo: '', especialidad: '', activo: '' };
  gridTitularApi.query();
}

function limpiarFiltrosInterno() {
  filtrosInterno.value = { q: '', carrera: '', universidad: '', enRotacion: '', activo: '' };
  gridInternoApi.query();
}
</script>

<template>
  <Page auto-content-height>

    <!-- Modals conectados -->
    <FormTitularModal @success="gridTitularApi.query()" />
    <FormInternoModal @success="gridInternoApi.query()" />

    <!-- Cabecera -->
    <div class="mb-4 flex items-center justify-between flex-wrap gap-2">
      <div>
        <h2 class="text-lg font-bold text-gray-800 mb-0">Gestión de Personal</h2>
        <p class="text-sm text-gray-500 mt-0.5">
          Administración de titulares, internos y responsable de internos del SISU
        </p>
      </div>
    </div>

    <NTabs v-model:value="tabActivo" type="bar" animated>

      <!-- ══════════════════ TAB 1 — TITULARES ══════════════════ -->
      <NTabPane name="titulares" tab="👨‍⚕️ Titulares" display-directive="show:lazy">

        <!-- Skeleton mientras cargan opciones de filtro -->
        <NCard v-if="filtersLoading" size="small" class="mb-3">
          <NSpace wrap>
            <NSkeleton height="34px" width="240px" :sharp="false" />
            <NSkeleton height="34px" width="180px" :sharp="false" />
            <NSkeleton height="34px" width="180px" :sharp="false" />
            <NSkeleton height="34px" width="120px" :sharp="false" />
            <NSkeleton height="34px" width="80px"  :sharp="false" />
          </NSpace>
        </NCard>

        <!-- Filtros reales — aparecen con transición -->
        <Transition name="sisu-fade">
          <NCard v-if="!filtersLoading" size="small" class="mb-3">
            <NSpace wrap>
              <NInput
                v-model:value="filtrosTitular.q"
                placeholder="Buscar nombre, CI, cargo…"
                clearable
                style="width:240px"
                @keyup.enter="gridTitularApi.query()"
              />
              <NSelect
                v-model:value="filtrosTitular.cargo"
                :options="cargos"
                placeholder="Cargo"
                clearable
                style="width:180px"
              />
              <NSelect
                v-model:value="filtrosTitular.especialidad"
                :options="especialidades"
                placeholder="Especialidad"
                clearable
                style="width:180px"
              />
              <NSelect
                v-model:value="filtrosTitular.activo"
                :options="opcionesEstado"
                placeholder="Estado"
                style="width:120px"
              />
              <NButton type="primary" @click="gridTitularApi.query()">Filtrar</NButton>
              <NButton @click="limpiarFiltrosTitular">Limpiar</NButton>
            </NSpace>
          </NCard>
        </Transition>

        <GridTitular table-title="Personal Titular">
          <template #toolbar-tools>
            <NButton
              type="primary"
              :style="{ background: '#e63946', borderColor: '#e63946' }"
              @click="abrirNuevoTitular"
            >
              + Nuevo Titular
            </NButton>
          </template>
        </GridTitular>
      </NTabPane>

      <!-- ══════════════════ TAB 2 — INTERNOS ══════════════════ -->
      <NTabPane name="internos" tab="🎓 Internos" display-directive="show:lazy">

        <NCard v-if="filtersLoading" size="small" class="mb-3">
          <NSpace wrap>
            <NSkeleton height="34px" width="240px" :sharp="false" />
            <NSkeleton height="34px" width="160px" :sharp="false" />
            <NSkeleton height="34px" width="160px" :sharp="false" />
            <NSkeleton height="34px" width="160px" :sharp="false" />
            <NSkeleton height="34px" width="120px" :sharp="false" />
            <NSkeleton height="34px" width="80px"  :sharp="false" />
          </NSpace>
        </NCard>

        <Transition name="sisu-fade">
          <NCard v-if="!filtersLoading" size="small" class="mb-3">
            <NSpace wrap>
              <NInput
                v-model:value="filtrosInterno.q"
                placeholder="Buscar nombre, CI, matrícula…"
                clearable
                style="width:240px"
                @keyup.enter="gridInternoApi.query()"
              />
              <NSelect
                v-model:value="filtrosInterno.carrera"
                :options="carreras"
                placeholder="Carrera"
                clearable
                style="width:160px"
              />
              <NSelect
                v-model:value="filtrosInterno.universidad"
                :options="universidades"
                placeholder="Universidad"
                clearable
                style="width:160px"
              />
              <NSelect
                v-model:value="filtrosInterno.enRotacion"
                :options="opcionesRotacion"
                placeholder="Rotación"
                style="width:160px"
              />
              <NSelect
                v-model:value="filtrosInterno.activo"
                :options="opcionesEstado"
                placeholder="Estado"
                style="width:120px"
              />
              <NButton type="primary" @click="gridInternoApi.query()">Filtrar</NButton>
              <NButton @click="limpiarFiltrosInterno">Limpiar</NButton>
            </NSpace>
          </NCard>
        </Transition>

        <GridInterno table-title="Personal Interno">
          <template #toolbar-tools>
            <NButton
              type="primary"
              :style="{ background: '#1d4ed8', borderColor: '#1d4ed8' }"
              @click="abrirNuevoInterno"
            >
              + Nuevo Interno
            </NButton>
          </template>
        </GridInterno>
      </NTabPane>

      <!-- ══════════════════ TAB 3 — RESPONSABLE ══════════════════ -->
      <NTabPane name="responsable" tab="🛡️ Responsable" display-directive="show:lazy">
        <NSpin :show="loadingResponsable">

          <!-- Header con botón asignar -->
          <div class="flex items-center justify-between mb-4">
            <div>
              <h3 class="text-base font-bold mb-0">Responsable de Internos</h3>
              <p class="text-sm text-gray-500 mt-0.5">
                Gestión de la tutora que supervisa a todos los internos del SISU
              </p>
            </div>
            <NButton
              type="primary"
              :style="{ background: '#7c3aed', borderColor: '#7c3aed' }"
              @click="abrirModalAsignar"
            >
              {{ responsableActual ? '↺ Cambiar / Reasignar' : '+ Asignar Responsable' }}
            </NButton>
          </div>

          <!-- Sin responsable asignado -->
          <NCard v-if="!responsableActual" class="mb-4">
            <NEmpty description="No hay responsable asignado actualmente">
              <template #extra>
                <NButton
                  type="primary"
                  :style="{ background: '#7c3aed', borderColor: '#7c3aed' }"
                  @click="abrirModalAsignar"
                >
                  Asignar ahora
                </NButton>
              </template>
            </NEmpty>
          </NCard>

          <!-- Card responsable actual -->
          <NCard v-else class="mb-4" :bordered="true">
            <!-- Info del titular -->
            <div class="flex items-start gap-4 flex-wrap">
              <NAvatar
                :size="56"
                round
                :style="{ background: '#e63946', fontSize: '1.4rem', fontWeight: 800 }"
              >
                {{ (responsableActual.nombreCompleto ?? 'R').charAt(0).toUpperCase() }}
              </NAvatar>
              <div class="flex-1 min-w-0">
                <div class="flex items-center gap-2 mb-1 flex-wrap">
                  <span class="font-bold text-base">{{ responsableActual.nombreCompleto }}</span>
                  <NTag type="success" size="small" round>✓ Activa</NTag>
                  <NTag type="info" size="small" round>
                    {{ responsableActual.totalInternos }} interno(s) a cargo
                  </NTag>
                </div>
                <NDescriptions :column="2" size="small" label-placement="left">
                  <NDescriptionsItem label="Cargo">
                    {{ responsableActual.cargo ?? '—' }}
                  </NDescriptionsItem>
                  <NDescriptionsItem label="Especialidad">
                    {{ responsableActual.especialidad ?? '—' }}
                  </NDescriptionsItem>
                  <NDescriptionsItem label="Email">
                    {{ responsableActual.email ?? '—' }}
                  </NDescriptionsItem>
                  <NDescriptionsItem label="Teléfono">
                    {{ responsableActual.telefono ?? '—' }}
                  </NDescriptionsItem>
                  <NDescriptionsItem label="Desde">
                    {{ responsableActual.fechaAsignacion ?? '—' }}
                  </NDescriptionsItem>
                </NDescriptions>
              </div>
            </div>

            <NDivider style="margin: 16px 0 12px" />

            <!-- Lista de internos asignados -->
            <div class="flex items-center justify-between mb-2">
              <span class="text-sm font-semibold text-gray-600">
                Internos bajo su supervisión
              </span>
              <NButton
                size="tiny"
                type="primary"
                ghost
                :style="{ color: '#7c3aed', borderColor: '#7c3aed' }"
                @click="abrirModalAsignar"
              >
                + Agregar más
              </NButton>
            </div>

            <NEmpty
              v-if="responsableActual.internos.length === 0"
              description="Sin internos asignados aún"
              size="small"
            />

            <div v-else class="grid grid-cols-1 gap-2" style="max-height:280px;overflow-y:auto">
              <div
                v-for="interno in responsableActual.internos"
                :key="interno.idInterno"
                class="flex items-center gap-3 p-2 rounded-lg border border-gray-100 hover:border-blue-200 transition-colors"
              >
                <NAvatar
                  :size="32"
                  round
                  :style="{ background: '#1d4ed8', fontSize: '0.8rem', fontWeight: 700 }"
                >
                  {{ (interno.nombreCompleto ?? 'I').charAt(0).toUpperCase() }}
                </NAvatar>
                <div class="flex-1 min-w-0">
                  <div class="font-semibold text-sm truncate">{{ interno.nombreCompleto }}</div>
                  <div class="text-xs text-gray-400">
                    {{ interno.carrera }} · {{ interno.universidad }}
                  </div>
                </div>
                <NTag
                  :type="interno.estaEnRotacion ? 'info' : 'warning'"
                  size="tiny"
                  round
                >
                  {{ interno.estaEnRotacion ? 'En rotación' : 'Fuera rot.' }}
                </NTag>
                <NButton
                  size="tiny"
                  type="error"
                  ghost
                  @click="handleDesasignar(interno.idResponsableInterno, interno.nombreCompleto)"
                >
                  Quitar
                </NButton>
              </div>
            </div>
          </NCard>

          <!-- Historial compacto -->
          <NCard title="Historial de asignaciones" size="small">
            <NEmpty v-if="historial.length === 0" size="small" description="Sin historial" />
            <div v-else style="max-height:200px;overflow-y:auto">
              <div
                v-for="h in historial"
                :key="h.idResponsableInterno"
                class="flex items-start gap-2 py-2 border-b border-gray-100 last:border-0 text-sm"
              >
                <div
                  :style="{
                    width:'8px', height:'8px', borderRadius:'50%',
                    flexShrink:0, marginTop:'5px',
                    background: h.activo ? '#22c55e' : '#94a3b8',
                  }"
                />
                <div class="flex-1 min-w-0">
                  <span class="font-medium">{{ h.nombreCompletoTitular }}</span>
                  <span class="text-gray-400 mx-1">→</span>
                  <span class="text-gray-600">{{ h.nombreCompletoInterno }}</span>
                  <div class="text-xs text-gray-400">
                    {{ h.fechaAsignacion }} · por {{ h.creadoPor ?? '—' }}
                  </div>
                </div>
                <NTag :type="h.activo ? 'success' : 'default'" size="tiny" round>
                  {{ h.activo ? 'Activo' : 'Histórico' }}
                </NTag>
              </div>
            </div>
          </NCard>

        </NSpin>

        <!-- ── Modal asignar / reasignar ─────────────────────────────── -->
        <div
          v-if="mostrarModalAsignar"
          class="fixed inset-0 z-50 flex items-center justify-center"
          style="background:rgba(0,0,0,0.45)"
          @click.self="mostrarModalAsignar = false"
        >
          <div class="bg-white rounded-xl shadow-2xl w-full max-w-lg mx-4 overflow-hidden">
            <!-- Header -->
            <div class="px-5 py-4 border-b flex items-center justify-between"
                 style="background:#7c3aed">
              <h4 class="font-bold text-white mb-0">Asignar Responsable de Internos</h4>
              <button
                class="text-white opacity-75 hover:opacity-100 text-xl leading-none"
                @click="mostrarModalAsignar = false"
              >&times;</button>
            </div>

            <!-- Body -->
            <div class="p-5 space-y-4">
              <NAlert type="info" :show-icon="true" style="font-size:.82rem">
                El titular seleccionado será la responsable de supervisar los internos asignados.
                Las asignaciones anteriores de esos internos quedarán como historial.
              </NAlert>

              <!-- Seleccionar titular -->
              <div>
                <label class="block text-sm font-semibold mb-1">
                  Titular responsable <span class="text-red-500">*</span>
                </label>
                <NSelect
                  v-model:value="idTitularSeleccionado"
                  :options="titularesParaSelect"
                  placeholder="Seleccionar titular activo…"
                  filterable
                  clearable
                  class="w-full"
                />
              </div>

              <!-- Modo de asignación -->
              <div>
                <label class="block text-sm font-semibold mb-2">Internos a asignar</label>
                <NSpace>
                  <NButton
                    :type="modoAsignacion === 'todos' ? 'primary' : 'default'"
                    size="small"
                    @click="modoAsignacion = 'todos'"
                  >
                    ✓ Todos los activos
                  </NButton>
                  <NButton
                    :type="modoAsignacion === 'seleccion' ? 'primary' : 'default'"
                    size="small"
                    @click="modoAsignacion = 'seleccion'"
                  >
                    Seleccionar manualmente
                  </NButton>
                </NSpace>
              </div>

              <!-- Lista de checkboxes (modo selección) -->
              <div v-if="modoAsignacion === 'seleccion'">
                <NSpin :show="loadingInternos">
                  <div
                    class="border border-gray-200 rounded-lg overflow-y-auto"
                    style="max-height:200px"
                  >
                    <!-- Seleccionar todos -->
                    <div class="flex items-center gap-2 px-3 py-2 border-b bg-gray-50">
                      <NCheckbox
                        :checked="todosSeleccionados"
                        :indeterminate="internosSeleccionados.length > 0 && !todosSeleccionados"
                        @update:checked="toggleSeleccionarTodos"
                      >
                        <span class="text-sm font-medium">
                          Seleccionar todos ({{ internosDisponibles.length }})
                        </span>
                      </NCheckbox>
                    </div>
                    <!-- Internos -->
                    <div
                      v-for="interno in internosDisponibles"
                      :key="interno.idInterno"
                      class="flex items-center gap-2 px-3 py-2 hover:bg-gray-50 cursor-pointer"
                      @click="() => {
                        const idx = internosSeleccionados.indexOf(interno.idInterno);
                        if (idx >= 0) internosSeleccionados.splice(idx, 1);
                        else internosSeleccionados.push(interno.idInterno);
                      }"
                    >
                      <NCheckbox
                        :checked="internosSeleccionados.includes(interno.idInterno)"
                        @update:checked="() => {
                          const idx = internosSeleccionados.indexOf(interno.idInterno);
                          if (idx >= 0) internosSeleccionados.splice(idx, 1);
                          else internosSeleccionados.push(interno.idInterno);
                        }"
                        @click.stop
                      />
                      <div class="flex-1 min-w-0">
                        <div class="text-sm font-medium truncate">{{ interno.nombreCompleto }}</div>
                        <div class="text-xs text-gray-400">{{ interno.carrera }}</div>
                      </div>
                      <NTag
                        :type="interno.estaEnRotacion ? 'info' : 'warning'"
                        size="tiny"
                        round
                      >
                        {{ interno.estaEnRotacion ? '↻ Rotación' : 'Fuera' }}
                      </NTag>
                    </div>
                    <NEmpty
                      v-if="internosDisponibles.length === 0 && !loadingInternos"
                      size="small"
                      description="Sin internos activos"
                    />
                  </div>
                  <div v-if="internosSeleccionados.length > 0" class="text-xs text-blue-600 mt-1">
                    {{ internosSeleccionados.length }} interno(s) seleccionado(s)
                  </div>
                </NSpin>
              </div>

              <!-- Observación opcional -->
              <div>
                <label class="block text-sm font-semibold mb-1">
                  Observación <span class="text-gray-400 font-normal">(opcional)</span>
                </label>
                <NInput
                  v-model:value="observacionAsignacion"
                  type="textarea"
                  :autosize="{ minRows: 2, maxRows: 3 }"
                  placeholder="Notas sobre esta asignación…"
                />
              </div>
            </div>

            <!-- Footer -->
            <div class="px-5 py-3 border-t flex justify-end gap-2 bg-gray-50">
              <NButton @click="mostrarModalAsignar = false">Cancelar</NButton>
              <NButton
                type="primary"
                :loading="guardandoResponsable"
                :disabled="!idTitularSeleccionado"
                :style="{ background: '#7c3aed', borderColor: '#7c3aed' }"
                @click="handleAsignarResponsable"
              >
                Confirmar asignación
              </NButton>
            </div>
          </div>
        </div>

      </NTabPane>

    </NTabs>
  </Page>
</template>

<style scoped>
.sisu-fade-enter-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}
.sisu-fade-enter-from {
  opacity: 0;
  transform: translateY(-4px);
}
.sisu-fade-leave-active {
  transition: opacity 0.15s ease;
}
.sisu-fade-leave-to {
  opacity: 0;
}
</style>