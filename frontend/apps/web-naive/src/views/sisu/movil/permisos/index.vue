<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { NButton, NEmpty, NModal, NSpin, NSpace } from 'naive-ui';
import { useAccessStore } from '@vben/stores';
import { message } from '#/adapter/naive';
import {
  getMisPermisos, subirJustificanteApp,
  type SolicitudPermisoResponse,
} from '#/api/sisu/app';
import { useWebSocket } from '#/composables/useWebSocket';
import SolicitarPermisoModal from './modules/solicitar-permiso-modal.vue';

const accessStore        = useAccessStore();
const { notificaciones } = useWebSocket();

// ── State ──────────────────────────────────────────────────────
const lista    = ref<SolicitudPermisoResponse[]>([]);
const cargando = ref(true);
const filtro   = ref<'todas' | 'pendientes' | 'aprobadas' | 'rechazadas'>('todas');

const showSolicitar   = ref(false);
const solicitudEditar = ref<SolicitudPermisoResponse | null>(null);
const showSubirJust   = ref(false);
const solicitudSel    = ref<SolicitudPermisoResponse | null>(null);
const fileInputRef    = ref<HTMLInputElement | null>(null);
const archivoJust     = ref<File | null>(null);
const subiendoJust    = ref(false);

// ── Computed ───────────────────────────────────────────────────
const cntTodas      = computed(() => lista.value.length);
const cntPendientes = computed(() => lista.value.filter(s =>
  s.estadoSolicitud === 'PENDIENTE' || s.estadoSolicitud === 'EN_OBSERVACION').length);
const cntAprobadas  = computed(() => lista.value.filter(s => s.estadoSolicitud === 'APROBADA').length);
const cntRechazadas = computed(() => lista.value.filter(s => s.estadoSolicitud === 'RECHAZADA').length);

const listaFiltrada = computed(() => {
  if (filtro.value === 'pendientes')
    return lista.value.filter(s =>
      s.estadoSolicitud === 'PENDIENTE' || s.estadoSolicitud === 'EN_OBSERVACION');
  if (filtro.value === 'aprobadas')  return lista.value.filter(s => s.estadoSolicitud === 'APROBADA');
  if (filtro.value === 'rechazadas') return lista.value.filter(s => s.estadoSolicitud === 'RECHAZADA');
  return lista.value;
});



function accentClass(estado: string): string {
  const m: Record<string, string> = {
    PENDIENTE:      'perm-card--warn',
    EN_OBSERVACION: 'perm-card--info',
    APROBADA:       'perm-card--ok',
    RECHAZADA:      'perm-card--err',
    CANCELADA:      'perm-card--muted',
  };
  return m[estado] ?? 'perm-card--muted';
}

function badgeClass(estado: string): string {
  const m: Record<string, string> = {
    PENDIENTE:      'badge--warn',
    EN_OBSERVACION: 'badge--info',
    APROBADA:       'badge--ok',
    RECHAZADA:      'badge--err',
    CANCELADA:      'badge--muted',
  };
  return m[estado] ?? 'badge--muted';
}

function hasActions(sol: SolicitudPermisoResponse): boolean {
  if (sol.estadoSolicitud === 'RECHAZADA' || sol.estadoSolicitud === 'CANCELADA') return false;
  return ['PENDIENTE', 'APROBADA', 'EN_OBSERVACION'].includes(sol.estadoSolicitud)
    || !!sol.justificanteUrl;
}

// ── Carga ──────────────────────────────────────────────────────
async function cargar(silencioso = false) {
  if (!silencioso) cargando.value = true;
  try {
    lista.value = (await getMisPermisos()) ?? [];
  } catch {
    if (!silencioso) message.error('Error al cargar permisos');
  } finally {
    if (!silencioso) cargando.value = false;
  }
}

// ── WebSocket ─────────────────────────────────────────────────
watch(
  () => notificaciones.value.length,
  (newLen, oldLen) => {
    if (newLen <= (oldLen ?? 0)) return;
    const newest = notificaciones.value[0];
    if (!newest) return;
    const TIPOS = ['PERMISO_APROBADO', 'PERMISO_RECHAZADO', 'PERMISO_OBSERVADO', 'PERMISO_CANCELADO'];
    if (TIPOS.includes(newest.tipo)) cargar(true);
  },
);

// ── Justificante ───────────────────────────────────────────────
function abrirSubirJust(sol: SolicitudPermisoResponse) {
  solicitudSel.value  = sol;
  archivoJust.value   = null;
  showSubirJust.value = true;
}

async function subirJustificante() {
  if (!archivoJust.value || !solicitudSel.value) return;
  subiendoJust.value = true;
  try {
    const updated = await subirJustificanteApp(
      solicitudSel.value.idSolicitudPermiso, archivoJust.value);
    const idx = lista.value.findIndex(
      s => s.idSolicitudPermiso === solicitudSel.value!.idSolicitudPermiso);
    if (idx >= 0 && updated) lista.value[idx] = updated as SolicitudPermisoResponse;
    message.success('Justificante subido correctamente');
    showSubirJust.value = false;
  } catch (e: any) {
    message.error(e?.message ?? 'Error al subir justificante');
  } finally {
    subiendoJust.value = false;
  }
}

async function verJustificante(sol: SolicitudPermisoResponse) {
  if (!sol.justificanteDownloadUrl) return;
  try {
    const resp = await fetch(sol.justificanteDownloadUrl, {
      headers: { Authorization: `Bearer ${accessStore.accessToken}` },
    });
    const blob = await resp.blob();
    const url  = URL.createObjectURL(blob);
    window.open(url, '_blank');
    setTimeout(() => URL.revokeObjectURL(url), 15_000);
  } catch {
    message.error('No se pudo abrir el justificante');
  }
}

function abrirEdicion(sol: SolicitudPermisoResponse) {
  solicitudEditar.value = sol;
  showSolicitar.value   = true;
}

function onSolicitudCreada(sol: SolicitudPermisoResponse) {
  lista.value.unshift(sol);
}

function onSolicitudModificada(sol: SolicitudPermisoResponse) {
  const idx = lista.value.findIndex(s => s.idSolicitudPermiso === sol.idSolicitudPermiso);
  if (idx >= 0) lista.value[idx] = sol;
  solicitudEditar.value = null;
}

onMounted(() => cargar());
</script>

<template>
  <div class="permisos-page">

    <!-- ── Spinner de carga inicial ─────────────────────────── -->
    <div v-if="cargando" class="permisos-center">
      <NSpin size="large" />
    </div>

    <template v-else>

      <!-- ── Context header ────────────────────────────────────── -->
      <div class="ctx-header">
        <div class="ctx-header__info">
          <span class="ctx-header__nombre">Solicitudes de Permiso</span>
          <span class="ctx-header__sub">{{ cntTodas }} solicitudes registradas</span>
        </div>
        <div class="ctx-header__actions">
          <button class="ctx-reload" title="Actualizar" @click="cargar(true)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M23 4v6h-6" /><path d="M1 20v-6h6" />
              <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15" />
            </svg>
          </button>
          <NButton
            type="primary"
            size="small"
            @click="showSolicitar = true"
          >
            <template #icon>
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"
                   style="width:14px;height:14px">
                <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
              </svg>
            </template>
            <span class="btn-nueva-text">Nueva solicitud</span>
          </NButton>
        </div>
      </div>

      <!-- ── Filtros con contadores ─────────────────────────── -->
      <div class="filter-tabs">
        <button
          v-for="f in [
            { key: 'todas',      label: 'Todas',      cnt: cntTodas      },
            { key: 'pendientes', label: 'Pendientes',  cnt: cntPendientes },
            { key: 'aprobadas',  label: 'Aprobadas',   cnt: cntAprobadas  },
            { key: 'rechazadas', label: 'Rechazadas',  cnt: cntRechazadas },
          ]"
          :key="f.key"
          class="filter-tab"
          :class="{ 'filter-tab--active': filtro === f.key }"
          @click="filtro = f.key as any"
        >
          {{ f.label }}
          <span class="tab-count">{{ f.cnt }}</span>
        </button>
      </div>

      <!-- ── Empty ─────────────────────────────────────────────── -->
      <div v-if="listaFiltrada.length === 0" class="permisos-empty">
        <NEmpty
          :description="filtro === 'todas' ? 'Sin solicitudes registradas' : 'Ninguna en este filtro'"
        >
          <template #icon>
            <svg viewBox="0 0 24 24" fill="none" stroke="#94a3b8" stroke-width="1.8" style="width:40px;height:40px">
              <path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z" />
              <polyline points="14 2 14 8 20 8" />
              <line x1="12" y1="12" x2="12" y2="18" /><line x1="9" y1="15" x2="15" y2="15" />
            </svg>
          </template>
        </NEmpty>
      </div>

      <!-- ── Lista de solicitudes ───────────────────────────────── -->
      <div v-else class="permisos-list">
        <div
          v-for="sol in listaFiltrada"
          :key="sol.idSolicitudPermiso"
          class="perm-card"
          :class="accentClass(sol.estadoSolicitud)"
        >
          <!-- Fila superior: badge de estado -->
          <div class="perm-card__top">
            <span class="estado-badge" :class="badgeClass(sol.estadoSolicitud)">
              {{ sol.estadoSolicitudLabel }}
            </span>
          </div>

          <!-- Tipo de permiso -->
          <p class="perm-card__tipo">{{ sol.tipoPermisoLabel }}</p>

          <!-- Meta: jornada, fecha, horas -->
          <div class="perm-card__meta">
            <span v-if="sol.nombreJornada" class="meta-tag meta-tag--jornada">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12">
                <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
              </svg>
              {{ sol.nombreJornada }}
            </span>
            <span v-if="sol.fechaPermiso" class="meta-tag">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12">
                <rect x="3" y="4" width="18" height="18" rx="2" ry="2" />
                <line x1="16" y1="2" x2="16" y2="6" /><line x1="8" y1="2" x2="8" y2="6" />
                <line x1="3" y1="10" x2="21" y2="10" />
              </svg>
              {{ sol.fechaPermiso }}
            </span>
            <span v-if="sol.horaInicioSolicitada" class="meta-tag">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12">
                <circle cx="12" cy="12" r="10" /><polyline points="12 6 12 12 16 14" />
              </svg>
              {{ sol.horaInicioSolicitada?.substring(0, 5) }} — {{ sol.horaFinSolicitada?.substring(0, 5) }}
            </span>
          </div>

          <!-- Motivo -->
          <p class="perm-card__motivo">{{ sol.motivoSolicitud }}</p>

          <!-- ── Banner de observación (EN_OBSERVACION) ──────────── -->
          <div v-if="sol.estadoSolicitud === 'EN_OBSERVACION'" class="obs-banner">
            <div class="obs-banner__header">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16" style="flex-shrink:0">
                <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
                <line x1="12" y1="9" x2="12" y2="13" /><line x1="12" y1="17" x2="12.01" y2="17" />
              </svg>
              <span class="obs-banner__title">Requiere corrección</span>
            </div>
            <p class="obs-banner__msg">{{ sol.observacionRevision }}</p>
          </div>

          <!-- Observación de rechazo (RECHAZADA, solo informativa) -->
          <p v-else-if="sol.estadoSolicitud === 'RECHAZADA' && sol.observacionRevision"
             class="perm-card__obs perm-card__obs--rechazo">
            {{ sol.observacionRevision }}
          </p>

          <!-- Indicador de justificante -->
          <div class="perm-card__just">
            <span v-if="!sol.justificanteUrl" class="just-none">Sin justificante adjunto</span>
            <span v-else class="just-ok">Justificante adjunto</span>
          </div>

          <!-- Acciones: bloqueadas en RECHAZADA -->
          <div v-if="hasActions(sol)" class="perm-card__actions">

            <!-- Corregir y reenviar (solo EN_OBSERVACION) — acción principal -->
            <NButton
              v-if="sol.estadoSolicitud === 'EN_OBSERVACION'"
              type="warning"
              block
              @click="abrirEdicion(sol)"
            >
              Corregir y reenviar
            </NButton>

            <!-- Adjuntar justificante (PENDIENTE o APROBADA) -->
            <NButton
              v-if="['PENDIENTE','APROBADA'].includes(sol.estadoSolicitud)"
              block
              class="perm-btn-sec"
              @click="abrirSubirJust(sol)"
            >
              {{ sol.justificanteUrl ? 'Cambiar justificante' : 'Adjuntar justificante' }}
            </NButton>

            <!-- Ver justificante -->
            <div v-if="sol.justificanteUrl" class="perm-actions-secondary">
              <NButton size="small" class="perm-btn-sec" @click="verJustificante(sol)">
                Ver justificante
              </NButton>
            </div>
          </div>
        </div>
      </div>

    </template>

    <!-- ── Modales ───────────────────────────────────────────── -->
    <SolicitarPermisoModal
      v-model:show="showSolicitar"
      :solicitud-editar="solicitudEditar"
      @creada="onSolicitudCreada"
      @modificada="onSolicitudModificada"
      @update:show="v => { if (!v) solicitudEditar = null; showSolicitar = v; }"
    />

    <NModal
      v-model:show="showSubirJust"
      preset="card"
      style="width:96%;max-width:420px;border-radius:18px"
      title="Adjuntar justificante"
      :mask-closable="false"
      class="perm-just-modal"
    >
      <NSpace vertical size="medium">
        <div style="font-size:13px;color:#475569">
          Solicitud: <strong style="color:#1e293b">{{ solicitudSel?.tipoPermisoLabel }}</strong>
        </div>
        <input
          ref="fileInputRef"
          type="file"
          accept=".jpg,.jpeg,.png,.pdf"
          style="display:none"
          @change="e => archivoJust = (e.target as HTMLInputElement).files?.[0] ?? null"
        />
        <div class="just-file-picker">
          <button class="just-file-picker__btn" @click="fileInputRef?.click()">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
              <path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z" />
              <polyline points="14 2 14 8 20 8" />
              <line x1="12" y1="18" x2="12" y2="12" /><line x1="9" y1="15" x2="15" y2="15" />
            </svg>
            Seleccionar archivo
          </button>
          <span class="just-file-picker__name">
            {{ archivoJust ? archivoJust.name : 'Ningún archivo seleccionado' }}
          </span>
        </div>
        <div style="font-size:12px;color:#94a3b8">Formatos: JPG, PNG, PDF — Máx. 150 MB</div>
      </NSpace>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showSubirJust = false">Cancelar</NButton>
          <NButton
            type="primary"
            :loading="subiendoJust"
            :disabled="!archivoJust"
            @click="subirJustificante"
          >Adjuntar</NButton>
        </NSpace>
      </template>
    </NModal>

  </div>
</template>

<style scoped>
/* ── Layout ─────────────────────────────────────────────────── */
.permisos-page   { display: flex; flex-direction: column; gap: 12px; padding-bottom: 20px; }
.permisos-center { display: flex; justify-content: center; padding: 60px 0; }
.permisos-list   { display: flex; flex-direction: column; gap: 10px; }
.permisos-empty  { padding: 36px 0; }

/* ── Context header ─────────────────────────────────────────── */
.ctx-header {
  display: flex; align-items: center; justify-content: space-between;
  gap: 10px; padding: 12px 16px;
  background: #ffffff; border: 1px solid #e2e8f0; border-radius: 14px;
  position: relative;
}
.ctx-header::before {
  content: ''; position: absolute; top: 0; left: 0; right: 0; height: 3px;
  background: linear-gradient(90deg, #2563eb, #1d4ed8);
  border-radius: 14px 14px 0 0;
}
.ctx-header__info   { display: flex; flex-direction: column; gap: 3px; min-width: 0; flex: 1; }
.ctx-header__nombre { font-size: 14px; font-weight: 700; color: #1e293b; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.ctx-header__sub    { font-size: 12.5px; color: #64748b; }

.ctx-header__actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }

.ctx-reload {
  flex-shrink: 0; width: 34px; height: 34px;
  display: flex; align-items: center; justify-content: center;
  border: 1px solid #e2e8f0; background: #f8fafc; color: #64748b;
  border-radius: 9px; cursor: pointer; transition: all 0.18s;
  -webkit-tap-highlight-color: transparent;
}
.ctx-reload:active { background: #e2e8f0; color: #1e293b; }
.ctx-reload svg    { width: 15px; height: 15px; display: block; }

@media (max-width: 360px) {
  .btn-nueva-text { display: none; }
}

/* ── Filter tabs ────────────────────────────────────────────── */
.filter-tabs { display: flex; gap: 7px; }

.filter-tab {
  flex: 1; display: flex; align-items: center; justify-content: center; gap: 6px;
  padding: 10px 6px; min-height: 44px;
  border: 1px solid #e2e8f0; border-radius: 12px;
  background: #ffffff; color: #64748b;
  font-size: 12px; font-weight: 500; cursor: pointer;
  transition: all 0.2s; -webkit-tap-highlight-color: transparent;
  white-space: nowrap;
}
.filter-tab--active {
  background: #2563eb; border-color: #2563eb; color: #ffffff;
}
.tab-count {
  font-size: 11.5px; font-weight: 700; padding: 1px 7px;
  border-radius: 10px; background: #f1f5f9; color: #64748b;
}
.filter-tab--active .tab-count {
  background: rgba(255, 255, 255, 0.22); color: inherit;
}

/* ── Permiso card ───────────────────────────────────────────── */
.perm-card {
  display: flex; flex-direction: column; gap: 9px;
  padding: 15px 16px; border-radius: 14px;
  background: #ffffff; border: 1px solid #e2e8f0;
  border-left: 4px solid transparent;
  box-shadow: 0 2px 6px rgba(30, 64, 175, 0.04);
}
.perm-card--warn  { border-left-color: #d97706; }
.perm-card--info  { border-left-color: #2563eb; }
.perm-card--ok    { border-left-color: #059669; }
.perm-card--err   { border-left-color: #dc2626; }
.perm-card--muted { border-left-color: #94a3b8; }

/* ── Estado badge ───────────────────────────────────────────── */
.perm-card__top { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }

.estado-badge {
  font-size: 11px; font-weight: 600;
  padding: 2px 10px; border-radius: 20px;
}
.badge--warn  { background: #fef3c7; color: #92400e; }
.badge--info  { background: #dbeafe; color: #1e40af; }
.badge--ok    { background: #d1fae5; color: #065f46; }
.badge--err   { background: #fee2e2; color: #991b1b; }
.badge--muted { background: #f1f5f9; color: #64748b; }

/* ── Card content ───────────────────────────────────────────── */
.perm-card__tipo {
  font-size: 14.5px; font-weight: 700;
  color: #1e293b; margin: 0; line-height: 1.35;
}

.perm-card__meta { display: flex; flex-wrap: wrap; gap: 5px; }
.meta-tag {
  display: inline-flex; align-items: center; gap: 4px;
  font-size: 11px; padding: 2px 8px;
  background: #f1f5f9; color: #64748b;
  border-radius: 6px;
}
.meta-tag svg { width: 12px; height: 12px; flex-shrink: 0; }
.meta-tag--jornada {
  color: #1e40af;
  background: #eff6ff;
}

.perm-card__motivo {
  font-size: 12.5px; color: #64748b; margin: 0; line-height: 1.5;
  display: -webkit-box; -webkit-line-clamp: 2; line-clamp: 2;
  -webkit-box-orient: vertical; overflow: hidden;
}

.perm-card__obs {
  font-size: 12px; color: #64748b; margin: 0; line-height: 1.5;
  background: #f1f5f9; border-radius: 8px; padding: 6px 10px;
}
.perm-card__obs--rechazo {
  background: #fee2e2; color: #991b1b;
}

.perm-card__just { font-size: 12px; }
.just-none       { color: #94a3b8; }
.just-ok         { color: #059669; font-weight: 500; }

/* ── Card actions ───────────────────────────────────────────── */
.perm-card__actions {
  display: flex; flex-direction: column; gap: 8px;
  padding-top: 8px; border-top: 1px solid #e2e8f0;
  margin-top: 2px;
}

.perm-card__actions :deep(.n-button--block) {
  min-height: 44px; font-size: 14px;
}

.perm-actions-secondary { display: flex; gap: 8px; flex-wrap: wrap; }
.perm-actions-secondary :deep(.n-button) {
  flex: 1; min-width: 0; min-height: 38px;
}

.perm-btn-sec {
  --n-border: 1.5px solid #e2e8f0 !important;
  --n-color: #f8fafc !important;
  --n-text-color: #1e40af !important;
  --n-font-weight: 600;
}
.perm-card__actions :deep(.perm-btn-sec) {
  border: 1.5px solid #e2e8f0 !important;
  background: #f8fafc !important;
  color: #1e40af !important;
  font-weight: 600 !important;
  transition: all 0.15s;
}
.perm-card__actions :deep(.perm-btn-sec:hover) {
  border-color: #2563eb !important;
  background: #eff6ff !important;
}

/* ── Banner de observación ──────────────────────────────────── */
.obs-banner {
  display: flex; flex-direction: column; gap: 6px;
  padding: 10px 12px;
  background: #fef3c7; border: 1px solid #fcd34d;
  border-radius: 10px;
}
.obs-banner__header {
  display: flex; align-items: center; gap: 6px;
  color: #92400e;
}
.obs-banner__title {
  font-size: 12.5px; font-weight: 700;
  color: #92400e;
}
.obs-banner__msg {
  font-size: 12.5px; color: #92400e; margin: 0;
  line-height: 1.5;
}

/* ── Justificante modal file picker ─────────────────────────── */
.just-file-picker {
  display: flex; flex-direction: column; gap: 8px;
}
.just-file-picker__btn {
  width: 100%; display: flex; align-items: center; justify-content: center; gap: 8px;
  padding: 16px; border: 2px dashed #e2e8f0; border-radius: 12px;
  background: #f8fafc; font-size: 15px; font-weight: 600;
  color: #475569; cursor: pointer; transition: all 0.2s;
  -webkit-tap-highlight-color: transparent;
}
.just-file-picker__btn:hover,
.just-file-picker__btn:active {
  border-color: #2563eb; background: #eff6ff; color: #1e40af;
}
.just-file-picker__name {
  font-size: 12px; color: #64748b; text-align: center;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}

/* ── Justificante modal header gradient ─────────────────────── */
:deep(.perm-just-modal .n-modal-card) {
  border-radius: 18px !important;
}
:deep(.perm-just-modal .n-modal-card-header) {
  background: linear-gradient(90deg, #2563eb, #1d4ed8);
  margin: -1px -1px 0 -1px;
  border-radius: 18px 18px 0 0 !important;
  padding: 14px 20px !important;
}
:deep(.perm-just-modal .n-modal-card-header .n-card-header__main) {
  color: #ffffff !important;
  font-size: 16px !important;
  font-weight: 700 !important;
}
:deep(.perm-just-modal .n-modal-card-header .n-card-header__close) {
  color: rgba(255,255,255,0.8) !important;
}
:deep(.perm-just-modal .n-modal-card-body) {
  padding: 20px !important;
}
</style>
