  <script lang="ts" setup>
  import { Page } from '@vben/common-ui';
  import { ref, onMounted, watch } from 'vue';
  import { useVbenVxeGrid } from '#/adapter/vxe-table';
  import { dialog, message } from '#/adapter/naive';
  import {
    NButton, NCard, NTabs, NTabPane, NSelect, NSpace,
    NModal, NInput, NGrid, NGridItem, NText, NAlert, NTag, NDivider, NDescriptions, NDescriptionsItem,
  } from 'naive-ui';
  import { useWebSocket } from '#/composables/useWebSocket';

  import { subirJustificanteApi } from '#/api/sisu/permisos';

  import type { SolicitudPermisoResponse, DelegacionResponse } from '#/api/sisu/permisos';
  import {
    getSolicitudesApi, getDelegacionesApi, getEstadisticasApi,
    cancelarSolicitudApi, rechazarSolicitudApi, observarSolicitudApi,
    aprobarDelegacionApi, rechazarDelegacionApi,
    iniciarDelegacionApi, completarDelegacionApi, cancelarDelegacionApi,
  } from '#/api/sisu/permisos';
  import {
    useSolicitudesColumns, useDelegacionesColumns,
    estadoSolicitudOptions, estadoDelegacionOptions, tipoPermisoOptions,
    type ActionParamsSol, type ActionParamsDel,
  } from './data';
  import FormSolicitud      from './modules/form-solicitud.vue';
  import FormAprobarPermiso from './modules/form-aprobar-permiso.vue';
  import FormDelegacion     from './modules/form-delegacion.vue';

  const showFormSolicitud  = ref(false);
  const showFormAprobar    = ref(false);
  const showFormDelegacion = ref(false);
  const solicitudParaAprobar = ref<SolicitudPermisoResponse | null>(null);

  // ── Modal: Ver Detalle ───────────────────────────────────────────────
  const detalleModal = ref({ visible: false, row: null as SolicitudPermisoResponse | null });
  function abrirDetalle(row: SolicitudPermisoResponse) {
    detalleModal.value = { visible: true, row };
  }

  type TagType = 'default' | 'warning' | 'success' | 'error' | 'info' | 'primary';
  const SOL_TAG: Record<string, TagType> = {
    PENDIENTE: 'warning', APROBADA: 'success', RECHAZADA: 'error',
    CANCELADA: 'default', EN_OBSERVACION: 'info',
  };
  const DEL_TAG: Record<string, TagType> = {
    PENDIENTE_APROBACION: 'warning', APROBADA: 'info', EN_CURSO: 'success',
    COMPLETADA: 'default', RECHAZADA: 'error', CANCELADA: 'default',
  };
  function solTagType(estado: string): TagType { return SOL_TAG[estado] ?? 'default'; }
  function delTagType(estado: string): TagType { return DEL_TAG[estado] ?? 'default'; }

  // ── WebSocket: auto-refresh cuando llega nueva solicitud ────────────────
  const { notificaciones, permisosRefreshSignal } = useWebSocket();

  // permisosRefreshSignal se incrementa desde la cola personal (/user/queue/notificaciones)
  // para NUEVA_SOLICITUD y SOLICITUD_MODIFICADA. Ese canal es el mismo que muestra
  // el toast — está probado que funciona, por lo que este watch es fiable.
  watch(permisosRefreshSignal, () => {
    solicitudesGridApi?.query();
    cargarStats();
  });

  // ── Stats ────────────────────────────────────────────────────────────────
  const stats = ref({ pendientes: 0, aprobadas: 0, delActivas: 0, total: 0 });

  async function cargarStats() {
    try {
      const res  = await getEstadisticasApi();
      const data = res?.data ?? res ?? {};
      stats.value = {
        pendientes: data.solicitudes?.PENDIENTE  ?? 0,
        aprobadas:  data.solicitudes?.APROBADA   ?? 0,
        delActivas: data.delegacionesActivas      ?? 0,
        total:      data.totalSolicitudes         ?? 0,
      };
    } catch { /* silencioso */ }
  }

  const justFileRef = ref<HTMLInputElement | null>(null);
  const justModal   = ref({
    visible: false, loading: false,
    file: null as File | null, row: null as SolicitudPermisoResponse | null,
  });

  function abrirJustificanteAdmin(row: SolicitudPermisoResponse) {
    justModal.value = { visible: true, loading: false, file: null, row };
  }
  async function confirmarJust() {
    if (!justModal.value.file || !justModal.value.row) return;
    justModal.value.loading = true;
    try {
      await subirJustificanteApi(justModal.value.row.idSolicitudPermiso, justModal.value.file);
      message.success('Justificante subido');
      justModal.value.visible = false;
      solicitudesGridApi.query();
    } catch { message.error('Error al subir'); }
    finally  { justModal.value.loading = false; }
  }

  // ── Tabs ─────────────────────────────────────────────────────────────────
  const activeTab        = ref('solicitudes');
  const delCargadas      = ref(false);

  function onTabChange(tab: string) {
    if (tab === 'delegaciones' && !delCargadas.value) {
      delegacionesGridApi.query();
      delCargadas.value = true;
    }
  }

  // ── Filtros ──────────────────────────────────────────────────────────────
  const filtrosSol = ref({ estado: '', tipoPermiso: '' });
  const filtrosDel = ref({ estado: '' });

  // ── Grid: Solicitudes ────────────────────────────────────────────────────
  function onSolicitudAction({ code, row }: ActionParamsSol) {
    if (code === 'detalle') { abrirDetalle(row); return; }
    if (code === 'aprobar') {
      solicitudParaAprobar.value = row;
      showFormAprobar.value = true;
      return;
    }
    if (code === 'observar')     { abrirObservarSol(row);  return; }
    if (code === 'rechazar')     { abrirRechazarSol(row);  return; }
    if (code === 'justificante') { abrirJustificanteAdmin(row); return; }
    if (code === 'cancelar') {
      dialog.warning({
        title: 'Cancelar Solicitud',
        content: `¿Cancelar la solicitud de ${row.apellidoTitular}, ${row.nombreTitular}?`,
        positiveText: 'Sí, cancelar', negativeText: 'No',
        onPositiveClick: async () => {
          await cancelarSolicitudApi(row.idSolicitudPermiso);
          message.success('Solicitud cancelada');
          solicitudesGridApi.query();
          cargarStats();
        },
      });
    }
  }

  const [SolicitudesGrid, solicitudesGridApi] = useVbenVxeGrid<SolicitudPermisoResponse>({
    gridOptions: {
      columns:     useSolicitudesColumns(onSolicitudAction),
      maxHeight:   480,
      pagerConfig: { enabled: true, pageSize: 15 },
      proxyConfig: {
        autoLoad: true,
        ajax: {
          query: async ({ page }: { page: { currentPage: number; pageSize: number } }) => {
            const res = await getSolicitudesApi({
              page: page.currentPage - 1,
              size: page.pageSize,
              ...(filtrosSol.value.estado      && { estado:      filtrosSol.value.estado      }),
              ...(filtrosSol.value.tipoPermiso && { tipoPermiso: filtrosSol.value.tipoPermiso }),
            });
            return {
              items: res?.content ?? [],
              total: res?.page?.totalElements ?? 0,
            };
          },
        },
      },
      toolbarConfig: { refresh: true, zoom: true, custom: true },
    },
  });

  // ── Grid: Delegaciones ───────────────────────────────────────────────────
  function onDelegacionAction({ code, row }: ActionParamsDel) {
    if (code === 'aprobar')   { abrirAccionDel(row, 'aprobar');   return; }
    if (code === 'rechazar')  { abrirAccionDel(row, 'rechazar');  return; }
    if (code === 'iniciar')   {
      dialog.warning({
        title: 'Iniciar Delegación',
        content: `¿Iniciar la delegación de ${row.nombreTitular}? Se registrará la hora real de inicio.`,
        positiveText: '▶ Iniciar', negativeText: 'Cancelar',
        onPositiveClick: async () => {
          await iniciarDelegacionApi(row.idDelegacion);
          message.success('Delegación iniciada 🟢');
          delegacionesGridApi.query(); cargarStats();
        },
      });
      return;
    }
    if (code === 'completar') {
      dialog.warning({
        title: 'Completar Delegación',
        content: `¿Completar la delegación? Se registrará la hora real de fin.`,
        positiveText: '✓ Completar', negativeText: 'Cancelar',
        onPositiveClick: async () => {
          await completarDelegacionApi(row.idDelegacion);
          message.success('Delegación completada ✓');
          delegacionesGridApi.query(); cargarStats();
        },
      });
      return;
    }
    if (code === 'cancelar') {
      dialog.warning({
        title: 'Cancelar Delegación',
        content: `¿Cancelar la delegación de ${row.nombreTitular}?`,
        positiveText: 'Sí, cancelar', negativeText: 'No',
        onPositiveClick: async () => {
          await cancelarDelegacionApi(row.idDelegacion);
          message.success('Delegación cancelada');
          delegacionesGridApi.query(); cargarStats();
        },
      });
    }
  }

  const [DelegacionesGrid, delegacionesGridApi] = useVbenVxeGrid<DelegacionResponse>({
    gridOptions: {
      columns:     useDelegacionesColumns(onDelegacionAction),
      maxHeight:   480,
      pagerConfig: { enabled: true, pageSize: 15 },
      proxyConfig: {
        autoLoad: false,
        ajax: {
          query: async ({ page }: { page: { currentPage: number; pageSize: number } }) => {
            const res = await getDelegacionesApi({
              page: page.currentPage - 1,
              size: page.pageSize,
              ...(filtrosDel.value.estado && { estado: filtrosDel.value.estado }),
            });
            return {
              items: res?.content ?? [],
              total: res?.page?.totalElements ?? 0,
            };
          },
        },
      },
      toolbarConfig: { refresh: true, zoom: true, custom: true },
    },
  });

  // ── Modal inline: Observar Solicitud ─────────────────────────────────────
  const observarSolModal = ref({
    visible: false, loading: false,
    observacion: '', row: null as SolicitudPermisoResponse | null,
  });
  function abrirObservarSol(row: SolicitudPermisoResponse) {
    observarSolModal.value = { visible: true, loading: false, observacion: '', row };
  }
  async function confirmarObservarSol() {
    if (!observarSolModal.value.observacion.trim()) {
      message.warning('Indica la observación para que el funcionario pueda corregir');
      return;
    }
    observarSolModal.value.loading = true;
    try {
      await observarSolicitudApi(observarSolModal.value.row!.idSolicitudPermiso,
        { observacion: observarSolModal.value.observacion });
      message.success('Solicitud devuelta con observaciones — el funcionario será notificado');
      observarSolModal.value.visible = false;
      solicitudesGridApi.query(); cargarStats();
    } catch { message.error('Error al enviar observación'); }
    finally  { observarSolModal.value.loading = false; }
  }

  // ── Modal inline: Rechazar Solicitud ─────────────────────────────────────
  const rechazarSolModal = ref({
    visible: false, loading: false,
    observacion: '', row: null as SolicitudPermisoResponse | null,
  });
  function abrirRechazarSol(row: SolicitudPermisoResponse) {
    rechazarSolModal.value = { visible: true, loading: false, observacion: '', row };
  }
  async function confirmarRechazarSol() {
    rechazarSolModal.value.loading = true;
    try {
      await rechazarSolicitudApi(rechazarSolModal.value.row!.idSolicitudPermiso,
        { observacion: rechazarSolModal.value.observacion || undefined });
      message.success('Solicitud rechazada');
      rechazarSolModal.value.visible = false;
      solicitudesGridApi.query(); cargarStats();
    } catch { message.error('Error al rechazar'); }
    finally  { rechazarSolModal.value.loading = false; }
  }

  // ── Modal inline: Aprobar / Rechazar Delegación ───────────────────────────
  const delModal = ref({
    visible: false, loading: false,
    modo: 'aprobar' as 'aprobar' | 'rechazar',
    observacion: '', row: null as DelegacionResponse | null,
  });
  function abrirAccionDel(row: DelegacionResponse, modo: 'aprobar' | 'rechazar') {
    delModal.value = { visible: true, loading: false, modo, observacion: '', row };
  }
  async function confirmarAccionDel() {
    if (delModal.value.modo === 'rechazar' && !delModal.value.observacion.trim()) {
      message.warning('La observación es obligatoria para rechazar'); return;
    }
    delModal.value.loading = true;
    try {
      if (delModal.value.modo === 'aprobar') {
        await aprobarDelegacionApi(delModal.value.row!.idDelegacion,
          { observacion: delModal.value.observacion });
        message.success('Delegación aprobada ✓');
      } else {
        await rechazarDelegacionApi(delModal.value.row!.idDelegacion,
          { observacion: delModal.value.observacion });
        message.success('Delegación rechazada');
      }
      delModal.value.visible = false;
      delegacionesGridApi.query(); cargarStats();
    } catch { message.error('Error al procesar'); }
    finally  { delModal.value.loading = false; }
  }

  onMounted(cargarStats);
  </script>

  <template>
    <Page auto-content-height>

      <!-- ── Forms con NModal (reemplaza los FormXxxModal) ──────────── -->
      <FormSolicitud
      v-model:show="showFormSolicitud"
      @saved="() => { showFormSolicitud = false; solicitudesGridApi.query(); cargarStats(); }"
      />
      <FormAprobarPermiso
      v-model:show="showFormAprobar"
      :solicitud="solicitudParaAprobar"
      @saved="() => { showFormAprobar = false; solicitudesGridApi.query(); delegacionesGridApi.query(); cargarStats(); }"
      />
      <FormDelegacion
      v-model:show="showFormDelegacion"
      @saved="() => { showFormDelegacion = false; delegacionesGridApi.query(); cargarStats(); }"
      />

      <!-- ── Cabecera + Stats ───────────────────────────────────────────── -->
      <div class="mb-4">
        <div class="mb-3">
          <h2 class="text-lg font-bold mb-0">Permisos &amp; Delegaciones</h2>
          <p class="text-sm opacity-60 mt-0.5">Gestión de solicitudes de permiso y delegaciones de turno</p>
        </div>
        <NGrid :cols="4" :x-gap="10">
          <NGridItem v-for="c in [
            { label: 'Pendientes',        value: stats.pendientes, cls: 'stat-orange', ic: '⏳' },
            { label: 'Aprobadas',         value: stats.aprobadas,  cls: 'stat-green',  ic: '✅' },
            { label: 'Del. Activas',      value: stats.delActivas, cls: 'stat-blue',   ic: '🔄' },
            { label: 'Total Solicitudes', value: stats.total,      cls: 'stat-purple', ic: '📋' },
          ]" :key="c.label">
            <div :class="['stat-card', c.cls]">
              <span class="stat-icon">{{ c.ic }}</span>
              <div>
                <div class="stat-label">{{ c.label }}</div>
                <div class="stat-value">{{ c.value }}</div>
              </div>
            </div>
          </NGridItem>
        </NGrid>
      </div>

      <!-- ── Tabs (filtros + acciones) ─────────────────────────────────── -->
      <NCard size="small" class="mb-2">
        <NTabs v-model:value="activeTab" type="line" animated @update:value="onTabChange">

          <!-- ── SOLICITUDES ── -->
          <NTabPane name="solicitudes" tab="📋 Solicitudes de Permiso">
            <div class="filtros-bar">
              <div class="filtros-grupo">
                <NSelect
                  v-model:value="filtrosSol.estado"
                  :options="estadoSolicitudOptions"
                  placeholder="Estado"
                  clearable
                  style="width: 155px"
                  @update:value="solicitudesGridApi.query()"
                />
                <NSelect
                  v-model:value="filtrosSol.tipoPermiso"
                  :options="tipoPermisoOptions"
                  placeholder="Tipo de permiso"
                  clearable
                  style="width: 210px"
                  @update:value="solicitudesGridApi.query()"
                />
                <NButton
                  secondary
                  size="small"
                  @click="() => { filtrosSol.estado = ''; filtrosSol.tipoPermiso = ''; solicitudesGridApi.query(); }"
                >
                  Limpiar filtros
                </NButton>
              </div>
              <NButton type="primary" @click="showFormSolicitud = true">
                + Nueva Solicitud
              </NButton>
            </div>
          </NTabPane>

          <!-- ── DELEGACIONES ── -->
          <NTabPane name="delegaciones" tab="🔄 Delegaciones">
            <div class="filtros-bar">
              <div class="filtros-grupo">
                <NSelect
                  v-model:value="filtrosDel.estado"
                  :options="estadoDelegacionOptions"
                  placeholder="Filtrar por estado"
                  clearable
                  style="width: 200px"
                  @update:value="delegacionesGridApi.query()"
                />
                <NButton
                  secondary
                  size="small"
                  @click="() => { filtrosDel.estado = ''; delegacionesGridApi.query(); }"
                >
                  Limpiar filtros
                </NButton>
              </div>
              <NButton type="primary" @click="showFormDelegacion = true">
                + Nueva Delegación
              </NButton>
            </div>
          </NTabPane>

        </NTabs>
      </NCard>

      <!-- ── Grids (con v-show para mantener estado) ────────────────────── -->
      <div v-show="activeTab === 'solicitudes'">
        <SolicitudesGrid table-title="Solicitudes de Permiso" />
      </div>
      <div v-show="activeTab === 'delegaciones'">
        <DelegacionesGrid table-title="Delegaciones" />
      </div>

      <!-- ═══ Modal inline: Observar Solicitud ══════════════════════════ -->
      <NModal
        v-model:show="observarSolModal.visible"
        preset="card" title="👁 Devolver con Observaciones"
        style="width:500px" :mask-closable="false"
      >
        <template v-if="observarSolModal.row">
          <div class="mb-3 p-3 rounded"
              style="background:rgba(234,179,8,.06); border:1px solid rgba(234,179,8,.3)">
            <NText><b>Titular:</b>
              {{ observarSolModal.row.apellidoTitular }},
              {{ observarSolModal.row.nombreTitular }}
            </NText><br>
            <NText><b>Tipo:</b> {{ observarSolModal.row.tipoPermisoLabel }}</NText><br>
            <NText depth="2" style="font-size:0.9em">
              <b>Motivo:</b> {{ observarSolModal.row.motivoSolicitud }}
            </NText>
          </div>
          <NAlert type="info" :bordered="false" class="mb-3" style="font-size:0.88em">
            La solicitud será devuelta al funcionario con tus observaciones.
            Podrá corregirla y volver a enviarla.
          </NAlert>
          <NInput
            v-model:value="observarSolModal.observacion"
            type="textarea" :rows="4"
            placeholder="Describe qué debe corregir o completar el funcionario (obligatorio)..."
            maxlength="500" show-count
          />
          <div class="flex justify-end gap-2 mt-4">
            <NButton @click="observarSolModal.visible = false">Cancelar</NButton>
            <NButton type="warning" :loading="observarSolModal.loading" @click="confirmarObservarSol">
              👁 Enviar con Observaciones
            </NButton>
          </div>
        </template>
      </NModal>

      <!-- ═══ Modal inline: Rechazar Solicitud ══════════════════════════ -->
      <NModal
        v-model:show="rechazarSolModal.visible"
        preset="card" title="❌ Rechazar Solicitud de Permiso"
        style="width:480px" :mask-closable="false"
      >
        <template v-if="rechazarSolModal.row">
          <div class="mb-3 p-3 rounded"
              style="background:rgba(239,68,68,.06); border:1px solid rgba(239,68,68,.2)">
            <NText><b>Titular:</b>
              {{ rechazarSolModal.row.apellidoTitular }},
              {{ rechazarSolModal.row.nombreTitular }}
            </NText><br>
            <NText><b>Tipo:</b> {{ rechazarSolModal.row.tipoPermisoLabel }}</NText><br>
            <NText depth="2" style="font-size:0.9em">
              <b>Motivo:</b> {{ rechazarSolModal.row.motivoSolicitud }}
            </NText>
          </div>
          <NInput
            v-model:value="rechazarSolModal.observacion"
            type="textarea" :rows="4"
            placeholder="Motivo del rechazo (opcional)"
            maxlength="500" show-count
          />
          <div class="flex justify-end gap-2 mt-4">
            <NButton @click="rechazarSolModal.visible = false">Cancelar</NButton>
            <NButton type="error" :loading="rechazarSolModal.loading" @click="confirmarRechazarSol">
              ❌ Confirmar Rechazo
            </NButton>
          </div>
        </template>
      </NModal>

      <!-- ═══ Modal inline: Aprobar / Rechazar Delegación ═══════════════ -->
      <NModal
        v-model:show="delModal.visible"
        preset="card"
        :title="delModal.modo === 'aprobar' ? 'Aprobar Delegación' : 'Rechazar Delegación'"
        style="width:460px" :mask-closable="false"
      >
        <template v-if="delModal.row">
          <div class="mb-3 p-3 rounded"
              :style="delModal.modo === 'aprobar'
                ? 'background:rgba(34,197,94,.06); border:1px solid rgba(34,197,94,.2)'
                : 'background:rgba(239,68,68,.06); border:1px solid rgba(239,68,68,.2)'">
            <NText><b>Titular:</b> {{ delModal.row.nombreTitular }}</NText><br>
            <NText><b>Cubre:</b>   {{ delModal.row.nombreInterno }}</NText><br>
            <NText depth="2" style="font-size:0.9em"><b>Motivo:</b> {{ delModal.row.motivo }}</NText>
          </div>
          <NInput
            v-model:value="delModal.observacion"
            type="textarea" :rows="3"
            :placeholder="delModal.modo === 'rechazar' ? 'Motivo del rechazo (obligatorio)' : 'Observación (opcional)'"
            maxlength="500"
          />
          <div class="flex justify-end gap-2 mt-4">
            <NButton @click="delModal.visible = false">Cancelar</NButton>
            <NButton
              :type="delModal.modo === 'aprobar' ? 'success' : 'error'"
              :loading="delModal.loading"
              @click="confirmarAccionDel"
            >
              {{ delModal.modo === 'aprobar' ? '✓ Aprobar' : 'Confirmar Rechazo' }}
            </NButton>
          </div>
        </template>
      </NModal>

      <!-- ═══ Modal: Ver Detalle Completo ══════════════════════════ -->
      <NModal
        v-model:show="detalleModal.visible"
        preset="card"
        style="width: 680px; max-width: 95vw"
        :mask-closable="true"
      >
        <template #header>
          <div class="flex items-center gap-3" v-if="detalleModal.row">
            <span style="font-size: 1.3rem">
              {{ { AUSENCIA_TOTAL:'🚫', PERMISO_MEDICO:'🏥', TRAMITE_ADMINISTRATIVO:'📋', OTRO:'📝' }[detalleModal.row.tipoPermiso] ?? '📝' }}
            </span>
            <div>
              <div class="font-bold text-base">Solicitud #{{ detalleModal.row.idSolicitudPermiso }}</div>
              <div class="text-xs opacity-60">{{ detalleModal.row.tipoPermisoLabel }}</div>
            </div>
            <NTag
              :type="solTagType(detalleModal.row.estadoSolicitud)"
              size="small" round
              style="margin-left: auto"
            >
              {{ detalleModal.row.estadoSolicitudLabel ?? detalleModal.row.estadoSolicitud }}
            </NTag>
          </div>
        </template>

        <template v-if="detalleModal.row">
          <!-- Titular y Jornada -->
          <section class="detalle-seccion">
            <div class="detalle-seccion-titulo">Titular</div>
            <NDescriptions :column="2" label-placement="left" size="small">
              <NDescriptionsItem label="Nombre">
                {{ detalleModal.row.apellidoTitular }}, {{ detalleModal.row.nombreTitular }}
              </NDescriptionsItem>
              <NDescriptionsItem label="Jornada">
                {{ detalleModal.row.nombreJornada ?? '—' }}
              </NDescriptionsItem>
              <NDescriptionsItem v-if="detalleModal.row.lugarJornada" label="Lugar">
                {{ detalleModal.row.lugarJornada }}
              </NDescriptionsItem>
            </NDescriptions>
          </section>

          <NDivider style="margin: 10px 0" />

          <!-- Datos del Permiso -->
          <section class="detalle-seccion">
            <div class="detalle-seccion-titulo">Permiso Solicitado</div>
            <NDescriptions :column="2" label-placement="left" size="small">
              <NDescriptionsItem label="Fecha">
                {{ detalleModal.row.fechaPermiso ?? '—' }}
              </NDescriptionsItem>
              <NDescriptionsItem label="Horario solicitado">
                <template v-if="detalleModal.row.horaInicioSolicitada">
                  {{ detalleModal.row.horaInicioSolicitada }}
                  <template v-if="detalleModal.row.horaFinSolicitada"> – {{ detalleModal.row.horaFinSolicitada }}</template>
                </template>
                <template v-else>—</template>
              </NDescriptionsItem>
              <NDescriptionsItem label="Registrado el" :span="2">
                {{ new Date(detalleModal.row.fechaHoraSolicitud).toLocaleString('es-BO') }}
              </NDescriptionsItem>
              <NDescriptionsItem label="Motivo" :span="2">
                <span style="white-space: pre-wrap; word-break: break-word">{{ detalleModal.row.motivoSolicitud }}</span>
              </NDescriptionsItem>
            </NDescriptions>
          </section>

          <!-- Revisión (si fue revisada) -->
          <template v-if="detalleModal.row.revisadoPor">
            <NDivider style="margin: 10px 0" />
            <section class="detalle-seccion">
              <div class="detalle-seccion-titulo">Revisión</div>
              <NDescriptions :column="2" label-placement="left" size="small">
                <NDescriptionsItem label="Revisado por">
                  {{ detalleModal.row.revisadoPor }}
                </NDescriptionsItem>
                <NDescriptionsItem v-if="detalleModal.row.fechaHoraRevision" label="Fecha revisión">
                  {{ new Date(detalleModal.row.fechaHoraRevision).toLocaleString('es-BO') }}
                </NDescriptionsItem>
                <NDescriptionsItem v-if="detalleModal.row.observacionRevision" label="Observación" :span="2">
                  <NAlert
                    :type="detalleModal.row.estadoSolicitud === 'RECHAZADA' ? 'error' : 'warning'"
                    :bordered="false"
                    style="font-size: 0.88em; padding: 6px 10px"
                  >
                    {{ detalleModal.row.observacionRevision }}
                  </NAlert>
                </NDescriptionsItem>
              </NDescriptions>
            </section>
          </template>

          <!-- Delegación (si existe) -->
          <template v-if="detalleModal.row.idDelegacion">
            <NDivider style="margin: 10px 0" />
            <section class="detalle-seccion">
              <div class="detalle-seccion-titulo">Delegación</div>
              <NDescriptions :column="2" label-placement="left" size="small">
                <NDescriptionsItem label="Estado">
                  <NTag
                    :type="delTagType(detalleModal.row.estadoDelegacion ?? '')"
                    size="small"
                  >
                    {{ detalleModal.row.estadoDelegacionLabel ?? detalleModal.row.estadoDelegacion }}
                  </NTag>
                </NDescriptionsItem>
                <NDescriptionsItem v-if="detalleModal.row.internoAsignado" label="Cubre">
                  {{ detalleModal.row.internoAsignado }}
                </NDescriptionsItem>
              </NDescriptions>
            </section>
          </template>

          <!-- Adjunto / Justificante -->
          <NDivider style="margin: 10px 0" />
          <section class="detalle-seccion">
            <div class="detalle-seccion-titulo">Justificante / Adjunto</div>
            <template v-if="detalleModal.row.justificanteUrl">
              <div class="flex items-center gap-3 flex-wrap mt-1">
                <NTag type="success" size="small" :bordered="false">📎 Adjunto disponible</NTag>
                <NButton
                  v-if="detalleModal.row.justificanteUrl"
                  size="small"
                  type="primary"
                  secondary
                  tag="a"
                  :href="detalleModal.row.justificanteUrl"
                  target="_blank"
                  rel="noopener"
                >
                  🔍 Ver adjunto
                </NButton>
                <NButton
                  v-if="detalleModal.row.justificanteDownloadUrl"
                  size="small"
                  secondary
                  tag="a"
                  :href="detalleModal.row.justificanteDownloadUrl"
                  download
                >
                  ⬇ Descargar
                </NButton>
              </div>
            </template>
            <template v-else>
              <NText depth="3" style="font-size: 0.88em">Sin adjunto registrado</NText>
            </template>
          </section>

          <div class="flex justify-end gap-2 mt-4">
            <NButton
              v-if="detalleModal.row.estadoSolicitud === 'PENDIENTE' || detalleModal.row.estadoSolicitud === 'EN_OBSERVACION'"
              type="default"
              @click="() => { abrirJustificanteAdmin(detalleModal.row!); detalleModal.visible = false; }"
            >
              📎 Subir justificante
            </NButton>
            <NButton @click="detalleModal.visible = false">Cerrar</NButton>
          </div>
        </template>
      </NModal>

      <!-- Modal justificante admin -->
      <NModal
        v-model:show="justModal.visible"
        preset="card" title="Subir justificante"
        style="width:460px" :mask-closable="false"
      >
        <template v-if="justModal.row">
          <NText depth="3" style="font-size:13px">
            Titular: <b>{{ justModal.row.apellidoTitular }}, {{ justModal.row.nombreTitular }}</b>
          </NText>
          <div style="margin-top:14px">
            <input ref="justFileRef" type="file" accept=".jpg,.jpeg,.png,.pdf"
                  style="display:none"
                  @change="e => justModal.file = (e.target as HTMLInputElement).files?.[0] ?? null" />
            <NButton block secondary @click="justFileRef?.click()">
              📎 Seleccionar archivo
            </NButton>
            <p style="font-size:12px;color:#888;text-align:center;margin-top:6px">
              {{ justModal.file ? justModal.file.name : 'Ningún archivo' }}
            </p>
          </div>
          <div class="flex justify-end gap-2 mt-4">
            <NButton @click="justModal.visible = false">Cancelar</NButton>
            <NButton type="primary" :loading="justModal.loading"
                    :disabled="!justModal.file" @click="confirmarJust">
              Subir justificante
            </NButton>
          </div>
        </template>
      </NModal>

    </Page>
  </template>

  <style scoped>
  /* ── Stat cards ───────────────────────────────────────────────── */
  .stat-card {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 14px;
    border-radius: 12px;
    border: 1px solid;
  }
  .stat-icon { font-size: 1.4rem; flex-shrink: 0; }
  .stat-label { font-size: 0.75rem; font-weight: 500; opacity: 0.75; }
  .stat-value { font-size: 1.5rem; font-weight: 700; line-height: 1.1; }

  /* Colores light */
  .stat-orange { background: #fff7ed; border-color: #fed7aa; color: #c2410c; }
  .stat-green  { background: #f0fdf4; border-color: #bbf7d0; color: #15803d; }
  .stat-blue   { background: #eff6ff; border-color: #bfdbfe; color: #1d4ed8; }
  .stat-purple { background: #faf5ff; border-color: #e9d5ff; color: #6d28d9; }

  /* Colores dark */
  :root.dark .stat-orange,
  html.dark .stat-orange { background: rgba(194,65,12,.15);  border-color: rgba(194,65,12,.35);  color: #fb923c; }
  :root.dark .stat-green,
  html.dark .stat-green  { background: rgba(21,128,61,.15);  border-color: rgba(21,128,61,.35);  color: #4ade80; }
  :root.dark .stat-blue,
  html.dark .stat-blue   { background: rgba(29,78,216,.15);  border-color: rgba(29,78,216,.35);  color: #60a5fa; }
  :root.dark .stat-purple,
  html.dark .stat-purple { background: rgba(109,40,217,.15); border-color: rgba(109,40,217,.35); color: #c084fc; }

  /* ── Modal detalle ───────────────────────────────────────────── */
  .detalle-seccion { margin-bottom: 2px; }
  .detalle-seccion-titulo {
    font-size: 0.72rem;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.06em;
    opacity: 0.45;
    margin-bottom: 6px;
  }

  /* ── Filtros bar ──────────────────────────────────────────────── */
  .filtros-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 8px;
    padding: 8px 0 4px;
  }
  .filtros-grupo {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
  }
  </style>