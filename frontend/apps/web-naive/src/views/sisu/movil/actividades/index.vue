<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { NButton, NEmpty, NSpin } from 'naive-ui';
import { useAccessStore } from '@vben/stores';
import { message } from '#/adapter/naive';
import {
  cancelarActividadApp,
  culminarActividadApp,
  getMisActividades,
  getMiJornada,
  type ActividadJornadaAppDto,
  type MiJornadaResponse,
} from '#/api/sisu/app';
import {
  type BitacoraEntrada,
  misEntradasBitacora,
} from '#/api/sisu/bitacora';
import { dialog } from '#/adapter/naive';
import { encolarOperacion } from '#/composables/offlineQueue';
import { useWebSocket } from '#/composables/useWebSocket';
import AutoReporteModal from './modules/auto-reporte-modal.vue';
import SubirEvidenciaAppModal from './modules/subir-evidencia-app-modal.vue';
import BitacoraModal from '#/views/sisu/movil/bitacora/modules/bitacora-modal.vue';

const accessStore = useAccessStore();
const { notificaciones } = useWebSocket();

// ── Tab principal ────────────────────────────────────────────────────────────
const activeTab = ref<'actividades' | 'bitacora'>('actividades');

// ── State ────────────────────────────────────────────────────────────────────
const jornada       = ref<MiJornadaResponse | null>(null);
const lista         = ref<ActividadJornadaAppDto[]>([]);
const cargando      = ref(true);
const filtroEstado  = ref<'todas' | 'pendientes' | 'completadas'>('todas');
const filtroJornada = ref<number | null>(null);

const showAutoReporte = ref(false);
const showSubirEvid   = ref(false);
const showBitacora    = ref(false);
const actSeleccionada = ref<ActividadJornadaAppDto | null>(null);
const culminandoId    = ref<number | null>(null);
const cancelandoId    = ref<number | null>(null);

// ── Bitácora state ──────────────────────────────────────────────────────────
const entradasBitacora = ref<BitacoraEntrada[]>([]);
const cargandoBitacora = ref(true);

// ── Jornadas derivadas de las actividades ────────────────────────────────────
const jornadasUnicas = computed(() => {
  const map = new Map<number, string>();
  for (const act of lista.value) {
    if (act.idJornada && !map.has(act.idJornada))
      map.set(act.idJornada, act.nombreJornada ?? `Jornada ${act.idJornada}`);
  }
  return Array.from(map.entries()).map(([id, nombre]) => ({ id, nombre }));
});

const multipleJornadas = computed(() => jornadasUnicas.value.length > 1);

// ── Filtrado compuesto ───────────────────────────────────────────────────────
const listaFiltrada = computed(() => {
  let items = lista.value;
  if (filtroJornada.value !== null)
    items = items.filter((a) => a.idJornada === filtroJornada.value);
  if (filtroEstado.value === 'pendientes')
    items = items.filter((a) => !a.culminada);
  if (filtroEstado.value === 'completadas')
    items = items.filter((a) => a.culminada);
  return items;
});

const cntPendientes  = computed(() => lista.value.filter((a) => !a.culminada).length);
const cntCompletadas = computed(() => lista.value.filter((a) => a.culminada).length);

// ── Helpers ──────────────────────────────────────────────────────────────────
const hFmt = (h: string | null) => h?.substring(0, 5) ?? null;

const ESTADO_LABEL: Record<string, string> = {
  PENDIENTE:   'Pendiente',
  EN_PROGRESO: 'En progreso',
  COMPLETADA:  'Completada',
  VALIDADA:    'Validada',
  CANCELADA:   'Cancelada',
};

function accentClass(e: string): string {
  const m: Record<string, string> = {
    PENDIENTE:   'act-card--warn',
    EN_PROGRESO: 'act-card--info',
    COMPLETADA:  'act-card--ok',
    VALIDADA:    'act-card--ok',
    CANCELADA:   'act-card--muted',
  };
  return m[e] ?? 'act-card--warn';
}

function estadoClass(e: string): string {
  const m: Record<string, string> = {
    PENDIENTE:   'badge--warn',
    EN_PROGRESO: 'badge--info',
    COMPLETADA:  'badge--ok',
    VALIDADA:    'badge--ok',
    CANCELADA:   'badge--muted',
  };
  return m[e] ?? 'badge--warn';
}

// ── Bitácora helpers ────────────────────────────────────────────────────────
function fmtFecha(iso: string) {
  const d = new Date(iso);
  return d.toLocaleDateString('es-PE', { day: 'numeric', month: 'short' });
}

function fmtHora(iso: string) {
  const d = new Date(iso);
  return d.toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' });
}

// ── Carga de datos ───────────────────────────────────────────────────────────
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

async function cargarBitacora(silencioso = false) {
  if (!silencioso) cargandoBitacora.value = true;
  try {
    entradasBitacora.value = await misEntradasBitacora(50);
  } catch {
    if (!silencioso) message.error('Error al cargar bitácora');
  } finally {
    if (!silencioso) cargandoBitacora.value = false;
  }
}

function cambiarTab(tab: 'actividades' | 'bitacora') {
  activeTab.value = tab;
  if (tab === 'bitacora' && entradasBitacora.value.length === 0) {
    cargarBitacora();
  }
}

// ── WebSocket: recarga silenciosa al recibir actividad/jornada nueva ─────────
watch(
  () => notificaciones.value[0],
  (newest) => {
    if (!newest) return;
    if (newest.tipo === 'ACTIVIDAD_ASIGNADA' || newest.tipo === 'JORNADA_ASIGNADA')
      cargar(true);
  },
);

// ── Acciones ─────────────────────────────────────────────────────────────────
async function culminar(act: ActividadJornadaAppDto) {
  culminandoId.value = act.idActividadJornada;
  try {
    const updated = await culminarActividadApp(act.idActividadJornada);
    const idx = lista.value.findIndex(
      (a) => a.idActividadJornada === act.idActividadJornada,
    );
    if (idx >= 0 && updated) lista.value[idx] = updated as ActividadJornadaAppDto;
    message.success('Actividad culminada ✓');
  } catch (e: any) {
    if (!navigator.onLine) {
      await encolarOperacion({
        tipo: 'culminar',
        endpoint: `/api/v1/actividades/asignacion/${act.idActividadJornada}/culminar`,
        method: 'POST',
        body: {},
        idActividadJornada: act.idActividadJornada,
      });
      const idx = lista.value.findIndex((a) => a.idActividadJornada === act.idActividadJornada);
      if (idx >= 0) {
        lista.value[idx] = {
          ...lista.value[idx]!,
          culminada: true,
          estadoActividad: 'COMPLETADA' as any,
        };
      }
      message.info('Guardado localmente — se sincronizará al conectar');
    } else {
      message.error(e?.message ?? 'Error al culminar');
    }
  } finally {
    culminandoId.value = null;
  }
}

function confirmarCancelar(act: ActividadJornadaAppDto) {
  dialog.warning({
    title:        'Suspender actividad',
    content:      `¿Deseas suspender "${act.nombreActividad}"? Quedará marcada como cancelada.`,
    positiveText: 'Suspender',
    negativeText: 'No, volver',
    onPositiveClick: async () => {
      cancelandoId.value = act.idActividadJornada;
      try {
        await cancelarActividadApp(act.idActividadJornada, 'Suspendida por el usuario');
        const idx = lista.value.findIndex((a) => a.idActividadJornada === act.idActividadJornada);
        if (idx >= 0) lista.value[idx] = { ...lista.value[idx]!, estadoActividad: 'CANCELADA', culminada: false };
        message.success('Actividad suspendida');
      } catch (e: any) {
        if (!navigator.onLine) {
          await encolarOperacion({
            tipo: 'cancelar',
            endpoint: `/api/v1/actividades/${act.idActividadJornada}/estado`,
            method: 'POST',
            body: { estado: 'CANCELADA', observaciones: 'Suspendida por el usuario' },
            idActividadJornada: act.idActividadJornada,
          });
          const idx = lista.value.findIndex((a) => a.idActividadJornada === act.idActividadJornada);
          if (idx >= 0) lista.value[idx] = { ...lista.value[idx]!, estadoActividad: 'CANCELADA', culminada: false };
          message.info('Guardado localmente — se sincronizará al conectar');
        } else {
          message.error(e?.message ?? 'Error al suspender');
        }
      } finally {
        cancelandoId.value = null;
      }
    },
  });
}

async function verEvidencia(act: ActividadJornadaAppDto) {
  if (!act.evidenciaDownloadUrl) return;
  try {
    const apiBase = (import.meta.env.VITE_GLOB_API_URL as string) ?? '';
    const downloadUrl = act.evidenciaDownloadUrl.startsWith('http')
      ? act.evidenciaDownloadUrl
      : `${apiBase}${act.evidenciaDownloadUrl}`;

    const resp = await fetch(downloadUrl, {
      headers: { Authorization: `Bearer ${accessStore.accessToken}` },
    });
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    const blob = await resp.blob();
    const url  = URL.createObjectURL(blob);
    window.open(url, '_blank');
    setTimeout(() => URL.revokeObjectURL(url), 15_000);
  } catch {
    message.error('No se pudo abrir la evidencia');
  }
}

async function verEvidenciaBitacora(entrada: BitacoraEntrada) {
  if (!entrada.evidenciaDownloadUrl) return;
  try {
    const apiBase = (import.meta.env.VITE_GLOB_API_URL as string) ?? '';
    const downloadUrl = entrada.evidenciaDownloadUrl.startsWith('http')
      ? entrada.evidenciaDownloadUrl
      : `${apiBase}${entrada.evidenciaDownloadUrl}`;

    const resp = await fetch(downloadUrl, {
      headers: { Authorization: `Bearer ${accessStore.accessToken}` },
    });
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
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

function onActividadCreada(act: ActividadJornadaAppDto) {
  lista.value.unshift(act);
}

function onEvidenciaSubida(act: ActividadJornadaAppDto) {
  const idx = lista.value.findIndex(
    (a) => a.idActividadJornada === act.idActividadJornada,
  );
  if (idx >= 0) lista.value[idx] = act;
  actSeleccionada.value = null;
}

function onBitacoraCreada(entrada: BitacoraEntrada) {
  if (entrada.idBitacora < 0) {
    entradasBitacora.value.unshift(entrada);
  } else {
    cargarBitacora(true);
  }
}

onMounted(cargar);
</script>

<template>
  <div class="act-page">

    <!-- ── Loading ──────────────────────────────────────────────── -->
    <div v-if="cargando" class="act-center">
      <NSpin size="large" />
    </div>

    <template v-else>

      <!-- ── Sin jornada ──────────────────────────────────────────── -->
      <div v-if="!jornada && activeTab === 'actividades'" class="empty-card">
        <svg class="empty-card__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <rect x="3" y="4" width="18" height="18" rx="2" ry="2" />
          <line x1="16" y1="2" x2="16" y2="6" /><line x1="8" y1="2" x2="8" y2="6" />
          <line x1="3" y1="10" x2="21" y2="10" />
          <line x1="8" y1="14" x2="8" y2="18" /><line x1="12" y1="14" x2="12" y2="18" /><line x1="16" y1="14" x2="16" y2="18" />
        </svg>
        <p class="empty-card__title">Sin jornada activa</p>
        <p class="empty-card__body">No tienes jornada asignada para hoy.</p>
        <NButton size="small" secondary @click="cargar()">Actualizar</NButton>
      </div>

      <template v-else>

        <!-- ── Cabecera: jornada + acciones ──────────────────────── -->
        <div class="ctx-header">
          <div class="ctx-header__info">
            <span class="ctx-header__nombre">{{ jornada?.nombre ?? 'Bitácora' }}</span>
            <span v-if="jornada" class="ctx-header__horario">
              {{ jornada.horaInicio?.substring(0, 5) }} — {{ jornada.horaFin?.substring(0, 5) }}
              <span v-if="jornada.nombreLugar" class="ctx-header__lugar">
                · {{ jornada.nombreLugar }}
              </span>
            </span>
          </div>
          <div class="ctx-header__actions">
            <button class="ctx-reload" title="Actualizar" @click="activeTab === 'actividades' ? cargar(true) : cargarBitacora(true)">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M23 4v6h-6" /><path d="M1 20v-6h6" />
                <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15" />
              </svg>
            </button>
          </div>
        </div>

        <!-- ── Tab Switcher ─────────────────────────────────────── -->
        <div class="tab-switcher">
          <button
            class="tab-btn"
            :class="{ 'tab-btn--active': activeTab === 'actividades' }"
            @click="cambiarTab('actividades')"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
              <line x1="8" y1="6" x2="21" y2="6" /><line x1="8" y1="12" x2="21" y2="12" /><line x1="8" y1="18" x2="21" y2="18" />
              <line x1="3" y1="6" x2="3.01" y2="6" /><line x1="3" y1="12" x2="3.01" y2="12" /><line x1="3" y1="18" x2="3.01" y2="18" />
            </svg>
            Actividades
            <span v-if="activeTab === 'actividades'" class="tab-count-badge">{{ lista.length }}</span>
          </button>
          <button
            class="tab-btn"
            :class="{ 'tab-btn--active': activeTab === 'bitacora' }"
            @click="cambiarTab('bitacora')"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
              <path d="M12 20h9" />
              <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z" />
            </svg>
            Bitácora
            <span v-if="activeTab === 'bitacora' && entradasBitacora.length" class="tab-count-badge">{{ entradasBitacora.length }}</span>
          </button>
        </div>

        <!-- ═══════════════ TAB: ACTIVIDADES ═══════════════ -->
        <template v-if="activeTab === 'actividades'">

          <!-- ── Botones de acción ──────────────────────────── -->
          <div class="action-buttons">
            <button class="action-btn action-btn--actividad" @click="showAutoReporte = true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
                <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
              </svg>
              <span class="action-btn__text">
                <strong>Registrar</strong>
                <small>Actividad</small>
              </span>
            </button>
            <button class="action-btn action-btn--bitacora" @click="showBitacora = true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
                <path d="M12 20h9" />
                <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z" />
              </svg>
              <span class="action-btn__text">
                <strong>Registrar</strong>
                <small>Bitácora</small>
              </span>
            </button>
          </div>

          <!-- ── Selector de jornada (solo si hay más de 1) ───────── -->
          <div v-if="multipleJornadas" class="jornada-pills">
            <button
              class="pill"
              :class="{ 'pill--active': filtroJornada === null }"
              @click="filtroJornada = null"
            >
              Todas
            </button>
            <button
              v-for="j in jornadasUnicas"
              :key="j.id"
              class="pill"
              :class="{ 'pill--active': filtroJornada === j.id }"
              @click="filtroJornada = j.id"
            >
              {{ j.nombre }}
            </button>
          </div>

          <!-- ── Filtros de estado ─────────────────────────────────── -->
          <div class="filter-tabs">
            <button
              class="filter-tab"
              :class="{ 'filter-tab--active': filtroEstado === 'todas' }"
              @click="filtroEstado = 'todas'"
            >
              Todas
              <span class="tab-count">{{ lista.length }}</span>
            </button>
            <button
              class="filter-tab"
              :class="{ 'filter-tab--active': filtroEstado === 'pendientes' }"
              @click="filtroEstado = 'pendientes'"
            >
              Pendientes
              <span class="tab-count">{{ cntPendientes }}</span>
            </button>
            <button
              class="filter-tab"
              :class="{ 'filter-tab--active': filtroEstado === 'completadas' }"
              @click="filtroEstado = 'completadas'"
            >
              Completadas
              <span class="tab-count">{{ cntCompletadas }}</span>
            </button>
          </div>

          <!-- ── Empty ─────────────────────────────────────────────── -->
          <div v-if="listaFiltrada.length === 0" class="act-empty">
            <NEmpty
              :description="
                filtroEstado === 'todas' ? 'Sin actividades asignadas' : 'Ninguna en este filtro'
              "
            >
              <template #icon>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" style="width:40px;height:40px;color:#94a3b8">
                  <rect x="3" y="4" width="18" height="18" rx="2" ry="2" />
                  <line x1="16" y1="2" x2="16" y2="6" /><line x1="8" y1="2" x2="8" y2="6" />
                  <line x1="8" y1="14" x2="8" y2="18" /><line x1="12" y1="14" x2="12" y2="18" /><line x1="16" y1="14" x2="16" y2="18" />
                </svg>
              </template>
            </NEmpty>
          </div>

          <!-- ── Lista de actividades ──────────────────────────────── -->
          <div v-else class="act-list">
            <div
              v-for="act in listaFiltrada"
              :key="act.idActividadJornada"
              class="act-card"
              :class="accentClass(act.estadoActividad)"
            >
              <div class="act-card__top">
                <span class="estado-badge" :class="estadoClass(act.estadoActividad)">
                  {{ ESTADO_LABEL[act.estadoActividad] ?? act.estadoActividad }}
                </span>
                <span v-if="act.esCarryOver" class="carryover-chip">↩ Traspasado</span>
                <span v-if="act.estadoActividad === 'VALIDADA'" class="validada-chip">✅ Validada</span>
              </div>

              <p class="act-card__nombre">{{ act.nombreActividad }}</p>

              <div class="act-card__meta">
                <span class="meta-tag">{{ act.tipoActividad }}</span>
                <span
                  v-if="multipleJornadas && act.nombreJornada"
                  class="meta-tag meta-tag--jornada"
                >
                  {{ act.nombreJornada }}
                </span>
              </div>

              <div v-if="act.horaInicio" class="act-card__horas">
                <svg class="icon-xs" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10" /><polyline points="12 6 12 12 16 14" />
                </svg>
                {{ hFmt(act.horaInicio) }} — {{ hFmt(act.horaFin) ?? '?' }}
              </div>

              <p v-if="act.observaciones" class="act-card__obs">{{ act.observaciones }}</p>

              <div class="act-card__evid">
                <span v-if="!act.evidenciaUrl" class="evid-none">Sin evidencia</span>
                <span v-else class="evid-ok">Evidencia adjunta</span>
              </div>

              <div class="act-card__actions">
                <NButton
                  v-if="!act.culminada && act.estadoActividad !== 'VALIDADA' && act.estadoActividad !== 'CANCELADA'"
                  type="primary"
                  block
                  :loading="culminandoId === act.idActividadJornada"
                  @click="culminar(act)"
                >
                  Culminar actividad
                </NButton>

                <NButton
                  v-if="act.estadoActividad !== 'VALIDADA' && act.estadoActividad !== 'CANCELADA'"
                  secondary
                  block
                  @click="abrirSubirEvidencia(act)"
                >
                  {{ act.evidenciaUrl ? 'Cambiar evidencia' : 'Subir evidencia' }}
                </NButton>

                <div class="act-actions-secondary">
                  <NButton
                    v-if="act.evidenciaUrl"
                    size="small"
                    secondary
                    @click="verEvidencia(act)"
                  >
                    Ver evidencia
                  </NButton>
                  <NButton
                    v-if="!act.culminada && act.estadoActividad !== 'VALIDADA' && act.estadoActividad !== 'CANCELADA'"
                    size="small"
                    type="error"
                    secondary
                    :loading="cancelandoId === act.idActividadJornada"
                    @click="confirmarCancelar(act)"
                  >
                    Suspender
                  </NButton>
                </div>
              </div>
            </div>
          </div>
        </template>

        <!-- ═══════════════ TAB: BITÁCORA ═══════════════ -->
        <template v-else>

          <!-- Botón registrar bitácora -->
          <button class="bitacora-register-btn" @click="showBitacora = true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
              <path d="M12 20h9" />
              <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z" />
            </svg>
            <span>Nueva entrada de bitácora</span>
          </button>

          <!-- Loading -->
          <div v-if="cargandoBitacora" class="act-center">
            <NSpin size="large" />
          </div>

          <!-- Empty -->
          <div v-else-if="entradasBitacora.length === 0" class="act-empty">
            <NEmpty description="Sin entradas de bitácora registradas">
              <template #icon>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" style="width:40px;height:40px;color:#94a3b8">
                  <path d="M12 20h9" />
                  <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z" />
                </svg>
              </template>
            </NEmpty>
          </div>

          <!-- Lista de entradas -->
          <div v-else class="bit-list">
            <div
              v-for="entrada in entradasBitacora"
              :key="entrada.idBitacora"
              class="bit-card"
            >
              <div class="bit-card__top">
                <div class="bit-card__fecha-col">
                  <span class="bit-card__dia">{{ new Date(entrada.fechaHoraEvento).getDate() }}</span>
                  <span class="bit-card__mes">{{ fmtFecha(entrada.fechaHoraEvento) }}</span>
                </div>
                <div class="bit-card__info">
                  <span class="bit-card__hora">{{ fmtHora(entrada.fechaHoraEvento) }}</span>
                  <span v-if="entrada.nombreJornada" class="bit-card__chip">{{ entrada.nombreJornada }}</span>
                </div>
              </div>
              <p class="bit-card__desc">{{ entrada.descripcion }}</p>
              <div v-if="entrada.evidenciaDownloadUrl" class="bit-card__footer">
                <button class="bit-card__evidence-btn" @click="verEvidenciaBitacora(entrada)">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
                    <path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48" />
                  </svg>
                  Ver evidencia adjunta
                </button>
              </div>
              <div v-if="entrada.nombreLugar" class="bit-card__lugar">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12">
                  <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z" />
                  <circle cx="12" cy="10" r="3" />
                </svg>
                {{ entrada.nombreLugar }}
              </div>
            </div>
          </div>
        </template>

      </template>
    </template>

    <!-- ── Modales ───────────────────────────────────────────────── -->
    <AutoReporteModal
      v-model:show="showAutoReporte"
      @creada="onActividadCreada"
    />
    <SubirEvidenciaAppModal
      v-if="actSeleccionada"
      v-model:show="showSubirEvid"
      :actividad="actSeleccionada"
      @subida="onEvidenciaSubida"
    />
    <BitacoraModal
      :show="showBitacora"
      @update:show="showBitacora = $event"
      @creada="onBitacoraCreada"
    />

  </div>
</template>

<style scoped>
/* ── Layout ─────────────────────────────────────────────────────────────── */
.act-page   { display: flex; flex-direction: column; gap: 12px; padding-bottom: 20px; }
.act-center { display: flex; justify-content: center; padding: 60px 0; }

/* ── Empty card ─────────────────────────────────────────────────────────── */
.empty-card {
  display: flex; flex-direction: column; align-items: center; text-align: center;
  gap: 12px; padding: 44px 24px;
  background: #ffffff; border: 1px solid #e2e8f0;
  border-radius: 16px;
}
.empty-card__icon  { width: 44px; height: 44px; color: #94a3b8; }
.empty-card__title { font-size: 15px; font-weight: 700; color: #1e293b; margin: 0; }
.empty-card__body  { font-size: 13px; color: #64748b; margin: 0; line-height: 1.55; }

/* ── Context header ─────────────────────────────────────────────────────── */
.ctx-header {
  display: flex; align-items: center; justify-content: space-between;
  gap: 10px; padding: 12px 16px;
  background: #ffffff; border: 1px solid #e2e8f0;
  border-radius: 14px;
  position: relative;
}
.ctx-header::before {
  content: ''; position: absolute; top: 0; left: 0; right: 0; height: 3px;
  background: linear-gradient(90deg, #2563eb, #1d4ed8);
  border-radius: 14px 14px 0 0;
}
.ctx-header__info    { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.ctx-header__nombre  { font-size: 14px; font-weight: 700; color: #1e293b;
                        white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.ctx-header__horario { font-size: 12.5px; color: #64748b; }
.ctx-header__lugar   { color: #64748b; }

.ctx-header__actions {
  display: flex; align-items: center; gap: 8px; flex-shrink: 0;
}

.ctx-reload {
  flex-shrink: 0; width: 34px; height: 34px;
  display: flex; align-items: center; justify-content: center;
  border: 1px solid #e2e8f0;
  background: #f8fafc; color: #64748b;
  border-radius: 9px; cursor: pointer; transition: all 0.18s;
  -webkit-tap-highlight-color: transparent;
}
.ctx-reload:active { background: #e2e8f0; color: #1e293b; }
.ctx-reload svg    { width: 15px; height: 15px; display: block; }

/* ── Tab Switcher ─────────────────────────────────────────────────────── */
.tab-switcher {
  display: flex; gap: 8px;
}
.tab-btn {
  flex: 1; display: flex; align-items: center; justify-content: center; gap: 7px;
  padding: 12px 8px; min-height: 48px;
  border: 1.5px solid #e2e8f0; border-radius: 12px;
  background: #ffffff; color: #64748b;
  font-size: 13.5px; font-weight: 600; cursor: pointer;
  transition: all 0.2s; -webkit-tap-highlight-color: transparent;
}
.tab-btn--active {
  background: #1e3a8a; border-color: #1e3a8a;
  color: #ffffff;
  box-shadow: 0 2px 8px rgba(30, 58, 138, 0.3);
}
.tab-btn svg {
  flex-shrink: 0;
}
.tab-count-badge {
  font-size: 11px; font-weight: 700; padding: 1px 7px;
  border-radius: 10px; background: #f1f5f9; color: #64748b;
}
.tab-btn--active .tab-count-badge {
  background: rgba(255, 255, 255, 0.22); color: inherit;
}

/* ── Action Buttons ────────────────────────────────────────────────────── */
.action-buttons {
  display: flex; gap: 10px;
}
.action-btn {
  flex: 1; display: flex; align-items: center; gap: 10px;
  padding: 14px 16px; border-radius: 14px;
  border: 1.5px solid; cursor: pointer;
  transition: all 0.2s; -webkit-tap-highlight-color: transparent;
}
.action-btn__text {
  display: flex; flex-direction: column; gap: 2px; text-align: left;
}
.action-btn__text strong {
  font-size: 13.5px; font-weight: 700; display: block;
}
.action-btn__text small {
  font-size: 11px; font-weight: 500; opacity: 0.7; display: block;
}

.action-btn--actividad {
  background: #eff6ff; border-color: #bfdbfe; color: #1e40af;
}
.action-btn--actividad:active { background: #dbeafe; }

.action-btn--bitacora {
  background: #fff7ed; border-color: #fed7aa; color: #9a3412;
}
.action-btn--bitacora:active { background: #ffedd5; }

/* ── Jornada pills ──────────────────────────────────────────────────────── */
.jornada-pills {
  display: flex; gap: 7px; overflow-x: auto; padding-bottom: 2px;
  scrollbar-width: none;
}
.jornada-pills::-webkit-scrollbar { display: none; }

.pill {
  flex-shrink: 0; padding: 7px 16px;
  border: 1.5px solid #e2e8f0; border-radius: 22px;
  background: #ffffff; color: #64748b;
  font-size: 13px; font-weight: 500; cursor: pointer;
  white-space: nowrap; transition: all 0.2s;
  -webkit-tap-highlight-color: transparent;
}
.pill--active {
  background: #2563eb; border-color: #2563eb;
  color: #ffffff;
}

/* ── Filter tabs ────────────────────────────────────────────────────────── */
.filter-tabs { display: flex; gap: 7px; }
.filter-tab {
  flex: 1; display: flex; align-items: center; justify-content: center; gap: 6px;
  padding: 10px 6px; min-height: 44px;
  border: 1px solid #e2e8f0; border-radius: 12px;
  background: #ffffff; color: #64748b;
  font-size: 13px; font-weight: 500; cursor: pointer;
  transition: all 0.2s; -webkit-tap-highlight-color: transparent;
}
.filter-tab--active {
  background: #2563eb; border-color: #2563eb;
  color: #ffffff;
}
.tab-count {
  font-size: 11.5px; font-weight: 700; padding: 1px 7px;
  border-radius: 10px; background: #f1f5f9; color: #64748b;
}
.filter-tab--active .tab-count {
  background: rgba(255, 255, 255, 0.22); color: inherit;
}

/* ── Empty ──────────────────────────────────────────────────────────────── */
.act-empty { padding: 36px 0; }

/* ── List ───────────────────────────────────────────────────────────────── */
.act-list { display: flex; flex-direction: column; gap: 10px; }

/* ── Activity card ──────────────────────────────────────────────────────── */
.act-card {
  display: flex; flex-direction: column; gap: 9px;
  padding: 15px 16px; border-radius: 14px;
  background: #ffffff; border: 1px solid #e2e8f0;
  border-left: 4px solid transparent;
  box-shadow: 0 2px 6px rgba(30, 64, 175, 0.04);
}
.act-card--warn  { border-left-color: #d97706; }
.act-card--info  { border-left-color: #2563eb; }
.act-card--ok    { border-left-color: #059669; }
.act-card--muted { border-left-color: #94a3b8; }

/* ── Estado badge ───────────────────────────────────────────────────────── */
.act-card__top { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }

.estado-badge {
  font-size: 11px; font-weight: 600;
  padding: 2px 10px; border-radius: 20px;
}
.badge--warn  { background: #fef3c7; color: #92400e; }
.badge--info  { background: #dbeafe; color: #1e40af; }
.badge--ok    { background: #d1fae5; color: #065f46; }
.badge--muted { background: #f1f5f9; color: #64748b; }

.carryover-chip {
  font-size: 11px; padding: 2px 8px;
  background: #f1f5f9; color: #64748b;
  border-radius: 20px;
}

.validada-chip {
  font-size: 11.5px; font-weight: 600;
  color: #065f46;
}

/* ── Content ────────────────────────────────────────────────────────────── */
.act-card__nombre {
  font-size: 14.5px; font-weight: 700;
  color: #1e293b; margin: 0; line-height: 1.35;
}

.act-card__meta  { display: flex; flex-wrap: wrap; gap: 5px; }
.meta-tag {
  font-size: 11px; padding: 2px 8px;
  background: #f1f5f9; color: #64748b;
  border-radius: 6px;
}
.meta-tag--jornada {
  color: #1e40af;
  background: #eff6ff;
}

.act-card__horas {
  display: flex; align-items: center; gap: 5px;
  font-size: 12.5px; color: #64748b;
}
.icon-xs { width: 13px; height: 13px; flex-shrink: 0; }

.act-card__obs {
  font-size: 12.5px; color: #64748b; margin: 0;
  line-height: 1.5;
  display: -webkit-box; -webkit-line-clamp: 2; line-clamp: 2;
  -webkit-box-orient: vertical; overflow: hidden;
}

.act-card__evid  { font-size: 12px; }
.evid-none       { color: #94a3b8; }
.evid-ok         { color: #059669; font-weight: 500; }

/* ── Card actions ───────────────────────────────────────────────────────── */
.act-card__actions {
  display: flex; flex-direction: column; gap: 8px;
  padding-top: 8px; border-top: 1px solid #e2e8f0;
  margin-top: 2px;
}

.act-actions-secondary {
  display: flex; gap: 8px; flex-wrap: wrap;
}
.act-actions-secondary :deep(.n-button) {
  flex: 1; min-width: 0;
  min-height: 38px;
}

.act-card__actions :deep(.n-button--block) {
  min-height: 44px;
  font-size: 14px;
}

.act-card__actions :deep(.n-button--secondary) {
  border: 1.5px solid #e2e8f0 !important;
  background: #f8fafc !important;
  color: #1e40af !important;
  font-weight: 600;
  transition: all 0.15s;
}
.act-card__actions :deep(.n-button--secondary:hover),
.act-card__actions :deep(.n-button--secondary:active) {
  border-color: #2563eb !important;
  background: #eff6ff !important;
}

.act-actions-secondary :deep(.n-button--secondary) {
  font-size: 12.5px;
  min-height: 38px;
}

/* ── Bitácora Register Button ──────────────────────────────────────────── */
.bitacora-register-btn {
  display: flex; align-items: center; justify-content: center; gap: 10px;
  width: 100%; padding: 14px;
  border-radius: 14px; border: 1.5px dashed #fed7aa;
  background: #fff7ed; color: #9a3412;
  font-size: 14px; font-weight: 600; cursor: pointer;
  transition: all 0.2s; -webkit-tap-highlight-color: transparent;
}
.bitacora-register-btn:active {
  background: #ffedd5; border-color: #fb923c;
}

/* ── Bitácora List ─────────────────────────────────────────────────────── */
.bit-list { display: flex; flex-direction: column; gap: 10px; }

.bit-card {
  background: #ffffff; border: 1px solid #e2e8f0;
  border-radius: 14px; padding: 14px 16px;
  box-shadow: 0 1px 4px rgba(0,0,0,.04);
}

.bit-card__top {
  display: flex; align-items: flex-start; gap: 12px; margin-bottom: 8px;
}

.bit-card__fecha-col {
  display: flex; flex-direction: column; align-items: center;
  min-width: 44px; padding: 6px 8px;
  background: #fff7ed; border-radius: 10px;
  border: 1px solid #fed7aa;
}
.bit-card__dia {
  font-size: 20px; font-weight: 800; color: #9a3412; line-height: 1;
}
.bit-card__mes {
  font-size: 10px; font-weight: 600; color: #c2410c;
  text-transform: capitalize; white-space: nowrap;
}

.bit-card__info {
  display: flex; flex-direction: column; gap: 4px;
  flex: 1; min-width: 0;
}
.bit-card__hora {
  font-size: 13px; font-weight: 700; color: #2563eb;
  font-variant-numeric: tabular-nums;
}
.bit-card__chip {
  font-size: 11px; padding: 2px 8px;
  background: #f1f5f9; color: #64748b;
  border-radius: 6px; font-weight: 500;
  display: inline-block; width: fit-content;
}

.bit-card__desc {
  font-size: 14px; color: #475569; line-height: 1.45;
  margin: 0 0 8px;
}

.bit-card__footer {
  padding-top: 8px; border-top: 1px solid #f1f5f9;
}
.bit-card__evidence-btn {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 6px 12px; border-radius: 8px;
  border: 1px solid #bfdbfe; background: #eff6ff;
  color: #1e40af; font-size: 12px; font-weight: 600;
  cursor: pointer; transition: all 0.15s;
  -webkit-tap-highlight-color: transparent;
}
.bit-card__evidence-btn:active {
  background: #dbeafe;
}

.bit-card__lugar {
  display: flex; align-items: center; gap: 4px;
  margin-top: 8px; font-size: 11.5px; color: #64748b;
}
</style>
