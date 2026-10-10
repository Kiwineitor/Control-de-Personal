<script setup lang="ts">
import { h, onMounted, ref } from 'vue';
import {
  NButton, NCard, NDataTable, NDrawer, NDrawerContent,
  NInput, NModal, NSelect, NSpace, NTag, NTabs, NTabPane, NSpin,
} from 'naive-ui';
import { dialog, message } from '#/adapter/naive';
import type { PersonalHospitalDto, TipoPersonalDto } from '#/api/admin/personal';
import {
  getPersonal, getTiposPersonal, togglePersonal, toggleTipoPersonal,
} from '#/api/admin/personal';
import type { InternoDto } from '#/api/sisu/personal';
import { getInternos, toggleInterno, regenerarQrInterno } from '#/api/sisu/personal';
import { useVbenModal } from '@vben/common-ui';
import FormPersonal     from './modules/form-personal.vue';
import FormTipoPersonal from './modules/form-tipo-personal.vue';
import FormInterno      from './modules/form-interno.vue';

// ── State ──────────────────────────────────────────────────────────────
const tabActivo     = ref<'personal' | 'tipos' | 'internos'>('personal');

// Personal
const personal      = ref<PersonalHospitalDto[]>([]);
const totalPersonal = ref(0);
const pagePersonal  = ref(1);
const cargando      = ref(false);
const filtroQ       = ref('');
const filtroTipo    = ref<number>(0);
const filtroActivo  = ref<string | null>(null);

// Tipos
const tipos         = ref<TipoPersonalDto[]>([]);

// Drawers / modales
const drawerPersonal     = ref(false);
const personalSeleccionado = ref<PersonalHospitalDto | null>(null);
const drawerTipo         = ref(false);
const tipoSeleccionado   = ref<TipoPersonalDto | null>(null);

// ── API ────────────────────────────────────────────────────────────────
async function cargarPersonal() {
  cargando.value = true;
  try {
    const activoBool = filtroActivo.value === 'true' ? true
                     : filtroActivo.value === 'false' ? false
                     : undefined;  // '' o null → sin filtro
    const res = await getPersonal({
      q:              filtroQ.value || undefined,
      idTipoPersonal: filtroTipo.value || undefined,
      activo:         activoBool,
      page: pagePersonal.value - 1,
      size: 20,
    });
    personal.value      = res?.content ?? [];
    totalPersonal.value = res?.totalElements ?? 0;
  } catch { /* silencioso */ }
  finally { cargando.value = false; }
}

async function cargarTipos() {
  tipos.value = await getTiposPersonal();
}

// ── Internos ────────────────────────────────────────────────────────────
const internos        = ref<InternoDto[]>([]);
const totalInternos   = ref(0);
const cargandoInt     = ref(false);
const filtroIntQ      = ref('');
const filtroIntCarrera = ref('');
const filtroIntActivo  = ref('');

const CARRERA_OPTS = [
  { label: 'Todas las carreras',  value: '' },
  { label: 'Medicina',            value: 'MEDICINA' },
  { label: 'Enfermería',          value: 'ENFERMERÍA' },
  { label: 'Odontología',         value: 'ODONTOLOGÍA' },
  { label: 'Farmacia',            value: 'FARMACIA' },
  { label: 'Laboratorio Clínico', value: 'LABORATORIO CLÍNICO' },
  { label: 'Fisioterapia',        value: 'FISIOTERAPIA' },
  { label: 'Nutrición',           value: 'NUTRICIÓN' },
];

const ACTIVO_OPTS = [
  { label: 'Todos',    value: '' },
  { label: 'Activos',  value: 'true' },
  { label: 'Inactivos',value: 'false' },
];

async function cargarInternos() {
  cargandoInt.value = true;
  try {
    const res = await getInternos({
      q:       filtroIntQ.value      || undefined,
      carrera: filtroIntCarrera.value || undefined,
      activo:  filtroIntActivo.value === 'true'  ? true
             : filtroIntActivo.value === 'false' ? false
             : undefined,
      page: 0, size: 100,
    });
    // getInternos usa toVxePage → retorna { items: T[], total: number }
    internos.value      = res.items  ?? [];
    totalInternos.value = res.total  ?? 0;
  } catch { /* silencioso */ }
  finally { cargandoInt.value = false; }
}

// Modal formulario interno
const [FormInternoModal, formInternoApi] = useVbenModal({ connectedComponent: FormInterno, destroyOnClose: true });

function nuevoInterno() { formInternoApi.setData(null).open(); }
function editarInterno(i: InternoDto) { formInternoApi.setData(i).open(); }

async function confirmarToggleInterno(i: InternoDto) {
  dialog.warning({
    title:        i.activo ? 'Desactivar interno' : 'Activar interno',
    content:      `¿${i.activo ? 'Desactivar' : 'Activar'} a ${i.nombreCompleto}?`,
    positiveText: 'Confirmar',
    onPositiveClick: async () => {
      await toggleInterno(i.idInterno);
      message.success('Estado actualizado');
      cargarInternos();
    },
  });
}

async function regenerarQr(i: InternoDto) {
  try {
    const res = await regenerarQrInterno(i.idInterno);
    message.success(`QR regenerado: ${res.codigo}`);
  } catch (e: any) {
    message.error(e?.message ?? 'Error al regenerar QR');
  }
}

const columnasInternos = [
  {
    title: 'Interno', key: 'nombreCompleto', minWidth: 180,
    render: (row: InternoDto) => h('div', [
      h('div', { style: 'font-weight:600;font-size:.86rem' }, row.nombreCompleto),
      h('div', { style: 'font-size:.74rem;color:#888' }, row.ci ?? ''),
    ]),
  },
  {
    title: 'Carrera', key: 'carrera', width: 160,
    render: (row: InternoDto) => h(NTag, { size: 'small', round: true, type: 'info' }, () => row.carrera),
  },
  { title: 'Universidad', key: 'universidad', minWidth: 130 },
  {
    title: 'Estado', key: 'activo', width: 90, align: 'center' as const,
    render: (row: InternoDto) => h(NTag, {
      type: row.activo ? 'success' : 'error', size: 'small', round: true,
    }, () => row.activo ? 'Activo' : 'Inactivo'),
  },
  {
    title: 'Acciones', key: '_acc', width: 160, align: 'center' as const,
    render: (row: InternoDto) => h(NSpace, { size: 4, justify: 'center' }, () => [
      h(NButton, { size: 'tiny', type: 'primary', ghost: true, onClick: () => editarInterno(row) }, () => '✏️'),
      h(NButton, { size: 'tiny', ghost: true, title: 'Regenerar QR', onClick: () => regenerarQr(row) }, () => '📱 QR'),
      h(NButton, {
        size: 'tiny', type: row.activo ? 'error' : 'success', ghost: true,
        onClick: () => confirmarToggleInterno(row),
      }, () => row.activo ? 'Off' : 'On'),
    ]),
  },
];

onMounted(() => { cargarPersonal(); cargarTipos(); cargarInternos(); });

const tipoOpts = () => [
  { label: 'Todos los tipos', value: 0 },
  ...tipos.value.map(t => ({ label: t.nombre, value: t.idTipoPersonal })),
];

// ── Acciones ───────────────────────────────────────────────────────────
function abrirNuevoPersonal() {
  personalSeleccionado.value = null;
  drawerPersonal.value = true;
}
function editarPersonal(p: PersonalHospitalDto) {
  personalSeleccionado.value = p;
  drawerPersonal.value = true;
}
function onPersonalSaved() {
  drawerPersonal.value = false;
  cargarPersonal();
}

function abrirNuevoTipo() {
  tipoSeleccionado.value = null;
  drawerTipo.value = true;
}
function editarTipo(t: TipoPersonalDto) {
  tipoSeleccionado.value = t;
  drawerTipo.value = true;
}
function onTipoSaved() {
  drawerTipo.value = false;
  cargarTipos();
}

async function confirmarTogglePersonal(p: PersonalHospitalDto) {
  dialog.warning({
    title: p.activo ? 'Desactivar personal' : 'Activar personal',
    content: `¿${p.activo ? 'Desactivar' : 'Activar'} a ${p.nombreCompleto}?`,
    positiveText: 'Confirmar',
    onPositiveClick: async () => {
      await togglePersonal(p.idTitular);
      message.success('Estado actualizado');
      cargarPersonal();
    },
  });
}

async function confirmarToggleTipo(t: TipoPersonalDto) {
  await toggleTipoPersonal(t.idTipoPersonal);
  message.success('Estado actualizado');
  cargarTipos();
}

// ── Columnas ───────────────────────────────────────────────────────────
const columnasPersonal = [
  {
    title: 'Personal',
    key: 'nombreCompleto',
    minWidth: 180,
    render: (row: PersonalHospitalDto) => h('div', [
      h('div', { style: 'font-weight:600;font-size:14px' }, row.nombreCompleto),
      h('div', { style: 'font-size:12px;color:#888' }, row.ci ?? ''),
    ]),
  },
  {
    title: 'Tipo',
    key: 'tipoPersonalNombre',
    width: 140,
    render: (row: PersonalHospitalDto) => row.tipoPersonalNombre
      ? h(NTag, {
          style: `background:${row.tipoPersonalColor}22;color:${row.tipoPersonalColor};`,
          size: 'small', round: true,
        }, () => row.tipoPersonalNombre)
      : h('span', { style: 'color:#ccc' }, '—'),
  },
  { title: 'Cargo', key: 'cargo', width: 160 },
  {
    title: 'Usuario',
    key: 'username',
    width: 120,
    render: (row: PersonalHospitalDto) => row.tieneUsuario
      ? h('span', { style: 'font-size:12px;color:#18a058' }, `✓ ${row.username}`)
      : h('span', { style: 'font-size:12px;color:#ccc' }, 'Sin cuenta'),
  },
  {
    title: 'Estado',
    key: 'activo',
    width: 90,
    align: 'center' as const,
    render: (row: PersonalHospitalDto) =>
      h(NTag, { type: row.activo ? 'success' : 'error', size: 'small', round: true },
        () => row.activo ? 'Activo' : 'Inactivo'),
  },
  {
    title: 'Acciones',
    key: 'acciones',
    width: 120,
    align: 'center' as const,
    render: (row: PersonalHospitalDto) => h(NSpace, { size: 4, justify: 'center' }, () => [
      h(NButton, { size: 'tiny', type: 'primary', ghost: true, onClick: () => editarPersonal(row) }, () => '✏️'),
      h(NButton, {
        size: 'tiny', type: row.activo ? 'error' : 'success', ghost: true,
        onClick: () => confirmarTogglePersonal(row),
      }, () => row.activo ? 'Desactivar' : 'Activar'),
    ]),
  },
];

const columnasTipos = [
  {
    title: 'Nombre',
    key: 'nombre',
    minWidth: 150,
    render: (row: TipoPersonalDto) => h('span', {
      style: `font-weight:600;color:${row.color}`,
    }, row.nombre),
  },
  { title: 'Descripción', key: 'descripcion', minWidth: 200 },
  {
    title: 'Acceso',
    key: 'esPersonalMedico',
    width: 140,
    render: (row: TipoPersonalDto) => h(NTag, {
      type: row.esPersonalMedico ? 'info' : 'default', size: 'small',
    }, () => row.esPersonalMedico ? '🏥 Médico/Asistencial' : '🏢 Apoyo/Admin'),
  },
  {
    title: 'Personal',
    key: 'totalPersonal',
    width: 90,
    align: 'center' as const,
  },
  {
    title: 'Estado',
    key: 'activo',
    width: 90,
    align: 'center' as const,
    render: (row: TipoPersonalDto) =>
      h(NTag, { type: row.activo ? 'success' : 'error', size: 'small', round: true },
        () => row.activo ? 'Activo' : 'Inactivo'),
  },
  {
    title: '',
    key: 'acciones',
    width: 110,
    align: 'center' as const,
    render: (row: TipoPersonalDto) => h(NSpace, { size: 4, justify: 'center' }, () => [
      h(NButton, { size: 'tiny', type: 'primary', ghost: true, onClick: () => editarTipo(row) }, () => '✏️ Editar'),
      h(NButton, {
        size: 'tiny', type: row.activo ? 'error' : 'success', ghost: true,
        onClick: () => confirmarToggleTipo(row),
      }, () => row.activo ? 'Off' : 'On'),
    ]),
  },
];
</script>

<template>
  <div class="personal-page">
    <!-- Modal internos -->
    <FormInternoModal @success="cargarInternos" />

    <NTabs v-model:value="tabActivo" type="bar" animated>

      <!-- ══ TAB: PERSONAL ═══════════════════════════════════════════ -->
      <NTabPane name="personal" tab="👥 Personal del Hospital">
        <!-- Filtros -->
        <NCard size="small" class="mb-3">
          <NSpace wrap>
            <NInput v-model:value="filtroQ" placeholder="Buscar por nombre, CI, cargo..."
                    clearable style="width:260px" @keyup.enter="cargarPersonal" />
            <NSelect
              v-model:value="filtroTipo" :options="tipoOpts()"
              placeholder="Tipo de personal"
              style="width:200px" clearable
              @update:value="cargarPersonal"
            />
            <NSelect
              v-model:value="filtroActivo"
              :options="ACTIVO_OPTS"
              style="width:130px"
              @update:value="cargarPersonal"
            />
            <NButton type="primary" @click="cargarPersonal">Buscar</NButton>
            <div class="ml-auto">
              <NButton
                type="primary"
                style="background:#18a058;border-color:#18a058"
                @click="abrirNuevoPersonal"
              >
                + Registrar personal
              </NButton>
            </div>
          </NSpace>
        </NCard>

        <!-- Tabla -->
        <NSpin :show="cargando">
          <NDataTable
            :data="personal"
            :columns="columnasPersonal"
            size="small"
            :bordered="false"
            :striped="true"
          />
          <div style="display:flex;justify-content:flex-end;margin-top:8px;font-size:13px;color:#888">
            Total: {{ totalPersonal }} registros
          </div>
        </NSpin>
      </NTabPane>

      <!-- ══ TAB: INTERNOS ══════════════════════════════════════════════ -->
      <NTabPane name="internos" tab="🎓 Internos">
        <NCard size="small" class="mb-3">
          <NSpace wrap>
            <NInput
              v-model:value="filtroIntQ"
              placeholder="Buscar por nombre, CI…"
              clearable style="width:240px"
              @keyup.enter="cargarInternos"
            />
            <NSelect
              v-model:value="filtroIntCarrera"
              :options="CARRERA_OPTS"
              style="width:200px"
              @update:value="cargarInternos"
            />
            <NSelect
              v-model:value="filtroIntActivo"
              :options="ACTIVO_OPTS"
              style="width:130px"
              @update:value="cargarInternos"
            />
            <NButton type="primary" @click="cargarInternos">Buscar</NButton>
            <div class="ml-auto">
              <NButton
                type="primary"
                style="background:#1d4ed8;border-color:#1d4ed8"
                @click="nuevoInterno"
              >
                + Registrar interno
              </NButton>
            </div>
          </NSpace>
        </NCard>
        <NSpin :show="cargandoInt">
          <NDataTable
            :data="internos"
            :columns="columnasInternos"
            size="small"
            :bordered="false"
            :striped="true"
          />
          <div style="display:flex;justify-content:flex-end;margin-top:8px;font-size:13px;color:#888">
            Total: {{ totalInternos }} internos
          </div>
        </NSpin>
      </NTabPane>

      <!-- ══ TAB: TIPOS DE PERSONAL ═══════════════════════════════════ -->
      <NTabPane name="tipos" tab="🏷️ Tipos de Personal">
        <NCard size="small" class="mb-3">
          <NSpace justify="space-between">
            <span style="font-size:13px;color:#666">
              Configura los tipos de cargo del hospital y sus permisos de acceso al sistema.
            </span>
            <NButton type="primary" @click="abrirNuevoTipo">+ Nuevo tipo</NButton>
          </NSpace>
        </NCard>
        <NDataTable
          :data="tipos"
          :columns="columnasTipos"
          size="small"
          :bordered="false"
          :striped="true"
        />
      </NTabPane>

    </NTabs>

    <!-- Drawer: registrar/editar personal -->
    <NDrawer v-model:show="drawerPersonal" :width="600" placement="right">
      <NDrawerContent
        :title="personalSeleccionado ? `✏️ Editar — ${personalSeleccionado.nombreCompleto}` : '👤 Registrar personal'"
        closable
      >
        <FormPersonal
          :personal="personalSeleccionado"
          @saved="onPersonalSaved"
          @cancel="drawerPersonal = false"
        />
      </NDrawerContent>
    </NDrawer>

    <!-- Drawer: tipo de personal -->
    <NDrawer v-model:show="drawerTipo" :width="480" placement="right">
      <NDrawerContent
        :title="tipoSeleccionado ? `✏️ Editar tipo — ${tipoSeleccionado.nombre}` : '🏷️ Nuevo tipo de personal'"
        closable
      >
        <FormTipoPersonal
          :tipo="tipoSeleccionado"
          @saved="onTipoSaved"
          @cancel="drawerTipo = false"
        />
      </NDrawerContent>
    </NDrawer>
  </div>
</template>

<style scoped>
.personal-page { padding-bottom: 24px; }
.mb-3 { margin-bottom: 12px; }
</style>
