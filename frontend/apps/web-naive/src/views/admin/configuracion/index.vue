<script lang="ts" setup>
import { onMounted, reactive, ref }         from 'vue';
import { NButton, NCard, NInput, NSelect,
         NStatistic, NTabPane,
         NTabs, NModal, NText }              from 'naive-ui';
import { Page }                              from '@vben/common-ui';
import { dialog, message }                   from '#/adapter/naive';
import { useVbenVxeGrid }                    from '#/adapter/vxe-table';
import MapPickerModal from './modules/map-picker-modal.vue';

import {
  activarLugarApi, activarRolApi, activarUsuarioApi,
  desactivarLugarApi, desactivarRolApi, desactivarUsuarioApi,
  generarTokenResetApi,
  getLugaresPaginadoApi, getGeofencingStatsApi,
  getRolesPaginadoApi, getUsuariosPaginadoApi,
  type LugarResponse, type RolResponse, type UsuarioResponse,
  type TokenResetResponse,
} from '#/api/admin/configuracion';

import {
  useLugarColumnas, useRolColumnas, useUsuarioColumnas,
  type ActionParams,
} from './data';

import FormLugar   from './modules/form-lugar.vue';
import FormRol     from './modules/form-rol.vue';
import FormUsuario from './modules/form-usuario.vue';
import FormRolVistas     from './modules/form-rol-vistas.vue';
import FormUsuarioVistas from './modules/form-usuario-vistas.vue';

// ── Stats ──────────────────────────────────────────────────────────────
const stats = reactive({ lugares: 0, geofencing: 0 });

const showVerMapa    = ref(false);
const lugarMapaVer   = ref<LugarResponse | null>(null);

async function cargarStats() {
  try {
    const geo = await getGeofencingStatsApi();
    stats.geofencing = (geo as any)?.data ?? geo ?? 0;
  } catch { /* silencioso */ }
}
onMounted(cargarStats);

// ── Filtros ────────────────────────────────────────────────────────────
const filtrosLugar   = reactive({ nombre: '', activo: '' });
const filtrosRol     = reactive({ nombre: '', estado: '' });
const filtrosUsuario = reactive({ termino: '', rolNombre: '', activo: '' });

function parseBool(val: string) {
  if (val === 'true')  return true;
  if (val === 'false') return false;
  return undefined;
}

// ── Grid Lugares ───────────────────────────────────────────────────────
const lugarRowSel  = ref<LugarResponse | null>(null);
const showFormLugar = ref(false);

const [GridLugares, gridLugaresApi] = useVbenVxeGrid<LugarResponse>({
  gridOptions: {
    columns: useLugarColumnas(onLugarAction),
    maxHeight: 460,
    pagerConfig: { enabled: true, pageSize: 15 },
    proxyConfig: {
      autoLoad: true,
      ajax: {
        query: async ({ page }: { page: { currentPage: number; pageSize: number } }) => {
          const res = await getLugaresPaginadoApi({
            page:   page.currentPage - 1,
            size:   page.pageSize,
            nombre: filtrosLugar.nombre,
            activo: parseBool(filtrosLugar.activo),
          });
          return {
            items: (res as any)?.content ?? [],
            total: (res as any)?.page?.totalElements ?? 0,
          };
        },
      },
    },
    toolbarConfig: { refresh: true, zoom: true, custom: true },
  },
});

function onLugarAction({ code, row }: ActionParams<LugarResponse>) {
  if (code === 'editar') {
    lugarRowSel.value = row;
    showFormLugar.value = true;
  } else if (code === 'ver-mapa') { 
    lugarMapaVer.value = row;
    showVerMapa.value  = true;
  } else if (code === 'activar') {
    dialog.warning({
      title: 'Activar lugar',
      content: `¿Activar "${row.nombre}"?`,
      positiveText: 'Sí', negativeText: 'Cancelar',
      onPositiveClick: async () => {
        await activarLugarApi(row.idLugar);
        message.success('Lugar activado');
        gridLugaresApi.query();
        cargarStats();
      },
    });
  } else if (code === 'desactivar') {
    dialog.warning({
      title: 'Desactivar lugar',
      content: `¿Desactivar "${row.nombre}"?`,
      positiveText: 'Sí', negativeText: 'Cancelar',
      onPositiveClick: async () => {
        await desactivarLugarApi(row.idLugar);
        message.success('Lugar desactivado');
        gridLugaresApi.query();
        cargarStats();
      },
    });
  }
}

// ── Grid Roles ─────────────────────────────────────────────────────────
const rolRowSel  = ref<RolResponse | null>(null);
const showFormRol = ref(false);
const showRolVistas = ref(false);
const rolVistasSel  = ref<RolResponse | null>(null);

const [GridRoles, gridRolesApi] = useVbenVxeGrid<RolResponse>({
  gridOptions: {
    columns: useRolColumnas(onRolAction),
    maxHeight: 460,
    pagerConfig: { enabled: true, pageSize: 15 },
    proxyConfig: {
      autoLoad: true,
      ajax: {
        query: async ({ page }: { page: { currentPage: number; pageSize: number } }) => {
          const res = await getRolesPaginadoApi({
            page:   page.currentPage - 1,
            size:   page.pageSize,
            nombre: filtrosRol.nombre,
            estado: filtrosRol.estado,
          });
          return {
            items: (res as any)?.content ?? [],
            total: (res as any)?.page?.totalElements ?? 0,
          };
        },
      },
    },
    toolbarConfig: { refresh: true, zoom: true, custom: true },
  },
});

function onRolAction({ code, row }: ActionParams<RolResponse>) {
  if (code === 'editar') {
    rolRowSel.value = row;
    showFormRol.value = true;
  } else if (code === 'vistas') {
    rolVistasSel.value = row;
    showRolVistas.value = true;
  } else if (code === 'activar' || code === 'desactivar') {
    const activar = code === 'activar';
    dialog.warning({
      title: activar ? 'Activar rol' : 'Desactivar rol',
      content: `¿${activar ? 'Activar' : 'Desactivar'} el rol "${row.nombre}"?`,
      positiveText: 'Sí', negativeText: 'Cancelar',
      onPositiveClick: async () => {
        activar ? await activarRolApi(row.idRol) : await desactivarRolApi(row.idRol);
        message.success(`Rol ${activar ? 'activado' : 'desactivado'}`);
        gridRolesApi.query();
      },
    });
  }
}

// ── Grid Usuarios ──────────────────────────────────────────────────────
const usuarioRowSel   = ref<UsuarioResponse | null>(null);
const showFormUsuario  = ref(false);
const showUsuarioVistas = ref(false);
const usuarioVistasSel  = ref<UsuarioResponse | null>(null);
const tokenResetData   = ref<TokenResetResponse | null>(null);
const showTokenModal   = ref(false);

const [GridUsuarios, gridUsuariosApi] = useVbenVxeGrid<UsuarioResponse>({
  gridOptions: {
    columns: useUsuarioColumnas(onUsuarioAction),
    maxHeight: 460,
    pagerConfig: { enabled: true, pageSize: 15 },
    proxyConfig: {
      autoLoad: true,
      ajax: {
        query: async ({ page }: { page: { currentPage: number; pageSize: number } }) => {
          const res = await getUsuariosPaginadoApi({
            page:     page.currentPage - 1,
            size:     page.pageSize,
            termino:  filtrosUsuario.termino,
            rolNombre:filtrosUsuario.rolNombre,
            activo:   parseBool(filtrosUsuario.activo),
          });
          return {
            items: (res as any)?.content ?? [],
            total: (res as any)?.page?.totalElements ?? 0,
          };
        },
      },
    },
    toolbarConfig: { refresh: true, zoom: true, custom: true },
  },
});

function onUsuarioAction({ code, row }: ActionParams<UsuarioResponse>) {
  if (code === 'editar') {
    usuarioRowSel.value = row;
    showFormUsuario.value = true;
  } else if (code === 'permisos') {
    usuarioVistasSel.value = row;
    showUsuarioVistas.value = true;
  } else if (code === 'token-reset') {
    dialog.warning({
      title: 'Generar token de reset',
      content: `Se generará un token válido por 24 horas para que ${row.nombre} ${row.apellido} pueda restablecer su contraseña. ¿Continuar?`,
      positiveText: 'Generar', negativeText: 'Cancelar',
      onPositiveClick: async () => {
        const res = await generarTokenResetApi(row.idUsuario);
        tokenResetData.value = (res as any)?.data ?? res;
        showTokenModal.value  = true;
      },
    });
  } else if (code === 'activar' || code === 'desactivar') {
    const activar = code === 'activar';
    dialog.warning({
      title: activar ? 'Activar usuario' : 'Desactivar usuario',
      content: `¿${activar ? 'Activar' : 'Desactivar'} al usuario "${row.username}"?`,
      positiveText: 'Sí', negativeText: 'Cancelar',
      onPositiveClick: async () => {
        activar ? await activarUsuarioApi(row.idUsuario) : await desactivarUsuarioApi(row.idUsuario);
        message.success(`Usuario ${activar ? 'activado' : 'desactivado'}`);
        gridUsuariosApi.query();
      },
    });
  }
}

function copiarToken() {
  if (tokenResetData.value?.token) {
    navigator.clipboard.writeText(tokenResetData.value.token);
    message.success('Token copiado al portapapeles');
  }
}
</script>

<template>
  <Page title="Configuración del Sistema" description="Gestión de lugares, roles y usuarios">

    <!-- Stats -->
    <div class="mb-4 grid grid-cols-2 gap-4 sm:grid-cols-4">
      <NCard size="small">
        <NStatistic label="Lugares con Geofencing" :value="stats.geofencing" />
      </NCard>
    </div>

    <!-- Tabs principales -->
    <NCard>
      <NTabs type="line" animated>

        <!-- ══ TAB LUGARES ══════════════════════════════════════════ -->
        <NTabPane name="lugares" tab="🏥 Lugares">
          <div class="mb-3 flex flex-wrap items-center gap-2">
            <NInput v-model:value="filtrosLugar.nombre" placeholder="Buscar por nombre..."
              clearable style="width: 220px" @keyup.enter="gridLugaresApi.query()" />
            <NSelect v-model:value="filtrosLugar.activo" placeholder="Estado"
              clearable style="width: 130px"
              :options="[{ label: 'Activos', value: 'true' }, { label: 'Inactivos', value: 'false' }]" />
            <NButton @click="gridLugaresApi.query()">Buscar</NButton>
            <NButton type="primary" @click="() => { lugarRowSel = null; showFormLugar = true; }">
              + Nuevo Lugar
            </NButton>
          </div>
          <GridLugares />
        </NTabPane>

        <!-- ══ TAB ROLES ════════════════════════════════════════════ -->
        <NTabPane name="roles" tab="🔐 Roles">
          <div class="mb-3 flex flex-wrap items-center gap-2">
            <NInput v-model:value="filtrosRol.nombre" placeholder="Buscar por nombre..."
              clearable style="width: 220px" @keyup.enter="gridRolesApi.query()" />
            <NSelect v-model:value="filtrosRol.estado" placeholder="Estado"
              clearable style="width: 130px"
              :options="[{ label: 'Activos', value: 'ACTIVO' }, { label: 'Inactivos', value: 'INACTIVO' }]" />
            <NButton @click="gridRolesApi.query()">Buscar</NButton>
            <NButton type="primary" @click="() => { rolRowSel = null; showFormRol = true; }">
              + Nuevo Rol
            </NButton>
          </div>
          <GridRoles />
        </NTabPane>

        <!-- ══ TAB USUARIOS ══════════════════════════════════════════ -->
        <NTabPane name="usuarios" tab="👤 Usuarios">
          <div class="mb-3 flex flex-wrap items-center gap-2">
            <NInput v-model:value="filtrosUsuario.termino" placeholder="Nombre, apellido o username..."
              clearable style="width: 240px" @keyup.enter="gridUsuariosApi.query()" />
            <NInput v-model:value="filtrosUsuario.rolNombre" placeholder="Filtrar por rol..."
              clearable style="width: 160px" />
            <NSelect v-model:value="filtrosUsuario.activo" placeholder="Estado"
              clearable style="width: 130px"
              :options="[{ label: 'Activos', value: 'true' }, { label: 'Inactivos', value: 'false' }]" />
            <NButton @click="gridUsuariosApi.query()">Buscar</NButton>
            <NButton type="primary" @click="() => { usuarioRowSel = null; showFormUsuario = true; }">
              + Nuevo Usuario
            </NButton>
          </div>
          <GridUsuarios />
        </NTabPane>

      </NTabs>
    </NCard>

    <!-- Modales de formulario -->
    <FormLugar
      v-model:show="showFormLugar"
      :lugar="lugarRowSel"
      @saved="() => { gridLugaresApi.query(); cargarStats(); }"
    />

    <FormRol
      v-model:show="showFormRol"
      :rol="rolRowSel"
      @saved="gridRolesApi.query()"
    />

    <FormUsuario
      v-model:show="showFormUsuario"
      :usuario="usuarioRowSel"
      @saved="gridUsuariosApi.query()"
    />

    <!-- Asignación de vistas por rol y overrides por usuario (modelo híbrido) -->
    <FormRolVistas
      v-model:show="showRolVistas"
      :rol="rolVistasSel"
      @saved="gridRolesApi.query()"
    />

    <FormUsuarioVistas
      v-model:show="showUsuarioVistas"
      :usuario="usuarioVistasSel"
      @saved="gridUsuariosApi.query()"
    />

    <!-- Añadir en template, junto a los demás modales -->
    <MapPickerModal
    v-model:show="showVerMapa"
    :titulo="lugarMapaVer ? `🗺 ${lugarMapaVer.nombre}` : '🗺 Ver ubicación'"
    :initial-lat="lugarMapaVer?.latitudCentro ?? null"
    :initial-lng="lugarMapaVer?.longitudCentro ?? null"
    :initial-radio="lugarMapaVer?.radioMetros ?? null"
    :readonly="true"
    />

    <!-- Modal Token Reset -->
    <NModal v-model:show="showTokenModal" preset="card" title="🔑 Token de restablecimiento"
      style="width: 480px" :mask-closable="false">
      <div class="space-y-3">
        <p class="text-sm text-gray-600">{{ tokenResetData?.mensaje }}</p>
        <div class="rounded-lg bg-gray-50 p-3 font-mono text-sm break-all border">
          {{ tokenResetData?.token }}
        </div>
        <p class="text-xs text-gray-400">
          Expira: {{ tokenResetData?.expiry ? new Date(tokenResetData.expiry).toLocaleString('es-BO') : '' }}
        </p>
        <NText type="warning" class="text-xs">
          ⚠ Este token solo se muestra una vez. Cópialo y entrégalo al usuario.
        </NText>
      </div>
      <div class="mt-4 flex justify-end gap-2">
        <NButton type="primary" @click="copiarToken">📋 Copiar token</NButton>
        <NButton @click="showTokenModal = false">Cerrar</NButton>
      </div>
    </NModal>

  </Page>
</template>