<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { NButton, NEmpty, NTag, NSpin } from 'naive-ui';
import { message } from '#/adapter/naive';
import { useWebSocket } from '#/composables/useWebSocket';
import {
  getMisNotificaciones,
  marcarLeida as apiMarcarLeida,
  marcarTodasLeidas as apiMarcarTodas,
  type NotificacionHistorial,
} from '#/api/sisu/notificaciones';

const router = useRouter();
const { marcarLeidaPorId, marcarTodasLeidas: wsMarcarTodas, limpiar } = useWebSocket();

// ── Estado ───────────────────────────────────────────────────────────────────
const lista       = ref<NotificacionHistorial[]>([]);
const cargando    = ref(false);
const cargandoMas = ref(false);
const paginaActual = ref(0);
const totalPaginas = ref(1);
const hayMas = computed(() => paginaActual.value + 1 < totalPaginas.value);

// ── Carga de datos ────────────────────────────────────────────────────────────
async function cargar(reiniciar = true) {
  if (reiniciar) {
    cargando.value = true;
    paginaActual.value = 0;
  } else {
    cargandoMas.value = true;
  }
  try {
    const paged = await getMisNotificaciones(paginaActual.value, 30);
    if (paged) {
      if (reiniciar) {
        lista.value = paged.content ?? [];
      } else {
        lista.value.push(...(paged.content ?? []));
      }
      totalPaginas.value = paged.totalPages ?? 1;
    }
  } catch {
    message.error('No se pudo cargar el historial de notificaciones');
  } finally {
    cargando.value    = false;
    cargandoMas.value = false;
  }
}

async function cargarMas() {
  paginaActual.value++;
  await cargar(false);
}

onMounted(() => cargar());

// ── Acciones ─────────────────────────────────────────────────────────────────
async function onMarcarLeida(n: NotificacionHistorial) {
  if (n.leida || n.esGlobal) return;
  try {
    await apiMarcarLeida(n.idNotificacion);
    n.leida = true;
    marcarLeidaPorId(n.idNotificacion);
  } catch { /* silencioso */ }
}

async function onMarcarTodas() {
  await apiMarcarTodas();
  lista.value.forEach(n => { if (!n.esGlobal) n.leida = true; });
  wsMarcarTodas();
  message.success('Todas marcadas como leídas');
}

function onLimpiar() {
  lista.value = [];
  limpiar();
}

// ── Navegación al hacer click ─────────────────────────────────────────────────
function onClickNotif(n: NotificacionHistorial) {
  onMarcarLeida(n);
  const ruta = rutaDestino(n.tipo);
  if (ruta) router.push(ruta);
}

function rutaDestino(tipo: string): string | null {
  switch (tipo) {
    case 'ACTIVIDAD_ASIGNADA':  return '/app/actividades';
    case 'JORNADA_ASIGNADA':    return '/app/home';
    case 'PERMISO_APROBADO':
    case 'PERMISO_RECHAZADO':   return '/app/permisos';
    case 'AUSENCIA_DETECTADA':  return '/app/faltas';
    default:                    return null;
  }
}

// ── Metadatos de tipo ─────────────────────────────────────────────────────────
const TIPO_TAG: Record<string, string> = {
  JORNADA_ASIGNADA:   'info',
  ACTIVIDAD_ASIGNADA: 'success',
  PERMISO_APROBADO:   'success',
  PERMISO_RECHAZADO:  'error',
  NUEVA_SOLICITUD:    'warning',
  AUSENCIA_DETECTADA: 'error',
  CUSTOM:             'default',
  BROADCAST:          'warning',
};
const TIPO_LABEL: Record<string, string> = {
  JORNADA_ASIGNADA:   'Jornada',
  ACTIVIDAD_ASIGNADA: 'Actividad',
  PERMISO_APROBADO:   'Aprobado',
  PERMISO_RECHAZADO:  'Rechazado',
  NUEVA_SOLICITUD:    'Solicitud',
  AUSENCIA_DETECTADA: 'Ausencia',
  CUSTOM:             'Mensaje',
  BROADCAST:          'General',
};

function iconoNotif(tipo: string): { letra: string; color: string; bg: string } {
  const m: Record<string, { letra: string; color: string; bg: string }> = {
    JORNADA_ASIGNADA:   { letra: 'J', color: '#1e40af', bg: '#dbeafe' },
    ACTIVIDAD_ASIGNADA: { letra: 'A', color: '#065f46', bg: '#d1fae5' },
    PERMISO_APROBADO:   { letra: 'P', color: '#065f46', bg: '#d1fae5' },
    PERMISO_RECHAZADO:  { letra: 'P', color: '#991b1b', bg: '#fee2e2' },
    NUEVA_SOLICITUD:    { letra: 'S', color: '#92400e', bg: '#fef3c7' },
    AUSENCIA_DETECTADA: { letra: 'A', color: '#991b1b', bg: '#fee2e2' },
    CUSTOM:             { letra: 'M', color: '#1e293b', bg: '#f1f5f9' },
    BROADCAST:          { letra: 'G', color: '#92400e', bg: '#fef3c7' },
  };
  return m[tipo] ?? { letra: 'N', color: '#64748b', bg: '#f1f5f9' };
}

function fmtTimestamp(ts: string) {
  try { return new Date(ts).toLocaleString('es-PE'); } catch { return ts; }
}

function esNavegable(tipo: string) {
  return ['ACTIVIDAD_ASIGNADA', 'JORNADA_ASIGNADA',
          'PERMISO_APROBADO', 'PERMISO_RECHAZADO',
          'AUSENCIA_DETECTADA'].includes(tipo);
}
</script>

<template>
  <div class="notif-page">

    <!-- Cabecera -->
    <div class="notif-header">
      <h2 class="notif-title">Notificaciones</h2>
      <div class="notif-actions">
        <button class="notif-btn" @click="onMarcarTodas">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" width="14" height="14">
            <polyline points="20 6 9 17 4 12" />
          </svg>
          Marcar leídas
        </button>
        <button class="notif-btn notif-btn--sec" @click="onLimpiar">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" width="14" height="14">
            <polyline points="3 6 5 6 21 6" />
            <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
          </svg>
          Limpiar
        </button>
        <button class="notif-btn notif-btn--icon" @click="cargar()" :disabled="cargando">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"
               stroke-width="2.2" width="16" height="16"
               :class="{ 'notif-spin': cargando }">
            <path d="M23 4v6h-6"/><path d="M1 20v-6h6"/>
            <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
          </svg>
        </button>
      </div>
    </div>

    <!-- Loading inicial -->
    <div v-if="cargando" class="notif-center">
      <NSpin size="large" />
    </div>

    <template v-else>
      <!-- Sin notificaciones -->
      <div v-if="lista.length === 0" style="padding:50px 0">
        <NEmpty description="Sin notificaciones">
          <template #icon>
            <svg viewBox="0 0 24 24" fill="none" stroke="#94a3b8" stroke-width="1.8" style="width:48px;height:48px">
              <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
              <path d="M13.73 21a2 2 0 0 1-3.46 0" />
            </svg>
          </template>
        </NEmpty>
      </div>

      <!-- Lista -->
      <div v-else class="notif-list">
        <div
          v-for="n in lista"
          :key="n.idNotificacion"
          class="notif-card"
          :class="{
            'notif-card--leida':    n.leida,
            'notif-card--nav':      esNavegable(n.tipo),
            'notif-card--global':   n.esGlobal,
          }"
          @click="onClickNotif(n)"
        >
          <!-- Punto de no leída -->
          <div v-if="!n.leida && !n.esGlobal" class="notif-punto" />

          <div class="notif-card__head">
            <div
              class="notif-badge"
              :style="{ background: iconoNotif(n.tipo).bg, color: iconoNotif(n.tipo).color }"
            >
              {{ iconoNotif(n.tipo).letra }}
            </div>
            <div class="notif-card__info">
              <div class="notif-card__titulo">{{ n.titulo }}</div>
              <div class="notif-card__meta">
                <span class="notif-card__ts">{{ fmtTimestamp(n.timestamp) }}</span>
                <span v-if="n.remitenteUsername" class="notif-card__remitente">· {{ n.remitenteUsername }}</span>
              </div>
            </div>
            <div class="notif-card__tags">
              <NTag :type="(TIPO_TAG[n.tipo] ?? 'default') as any"
                    size="small" :bordered="false">
                {{ TIPO_LABEL[n.tipo] ?? n.tipo }}
              </NTag>
              <NTag v-if="n.esGlobal" type="warning" size="small" :bordered="false">
                General
              </NTag>
            </div>
          </div>

          <p class="notif-card__mensaje">{{ n.mensaje }}</p>

          <!-- Hint de navegación -->
          <div v-if="esNavegable(n.tipo)" class="notif-card__nav-hint">
            Ver detalle →
          </div>
        </div>
      </div>

      <!-- Cargar más -->
      <div v-if="hayMas" class="notif-cargar-mas">
        <NButton secondary :loading="cargandoMas" @click="cargarMas">
          Cargar más notificaciones
        </NButton>
      </div>
    </template>

  </div>
</template>

<style scoped>
.notif-page    { padding-bottom: 20px; }
.notif-center  { display: flex; justify-content: center; padding: 60px 0; }

/* ── Header ───────────────────────────────────────────────────────────────── */
.notif-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 16px; padding: 14px 16px;
  background: #ffffff; border: 1px solid #e2e8f0; border-radius: 14px;
  position: relative;
}
.notif-header::before {
  content: ''; position: absolute; top: 0; left: 0; right: 0; height: 3px;
  background: linear-gradient(90deg, #2563eb, #1d4ed8);
  border-radius: 14px 14px 0 0;
}
.notif-title { font-size: 17px; font-weight: 700; color: #1e293b; margin: 0; }

.notif-actions { display: flex; align-items: center; gap: 6px; }

.notif-btn {
  display: inline-flex; align-items: center; gap: 5px;
  padding: 6px 12px; min-height: 34px;
  border: 1.5px solid #e2e8f0; border-radius: 8px;
  background: #f8fafc; color: #1e40af;
  font-size: 12.5px; font-weight: 600; cursor: pointer;
  white-space: nowrap; transition: all 0.15s;
  -webkit-tap-highlight-color: transparent;
}
.notif-btn:hover { border-color: #2563eb; background: #eff6ff; }
.notif-btn--sec { color: #64748b; }
.notif-btn--sec:hover { color: #1e40af; border-color: #2563eb; background: #eff6ff; }
.notif-btn--icon {
  padding: 6px 8px; color: #64748b;
}
.notif-btn--icon:hover { color: #1e40af; border-color: #2563eb; background: #eff6ff; }
.notif-spin { animation: notif-spin 1s linear infinite; }
@keyframes notif-spin { to { transform: rotate(360deg); } }

/* ── List ─────────────────────────────────────────────────────────────────── */
.notif-list { display: flex; flex-direction: column; gap: 8px; }

/* ── Card ─────────────────────────────────────────────────────────────────── */
.notif-card {
  background: #ffffff; border: 1px solid #e2e8f0; border-radius: 14px;
  padding: 14px 16px; position: relative;
  transition: opacity .2s, transform .1s, box-shadow .15s;
  -webkit-tap-highlight-color: transparent;
}
.notif-card--nav  { cursor: pointer; }
.notif-card--nav:hover { box-shadow: 0 2px 8px rgba(30, 64, 175, 0.06); }
.notif-card--nav:active { transform: scale(0.985); }
.notif-card--leida { opacity: .55; }
.notif-card--global {
  border-left: 4px solid #d97706;
}

/* ── Badge de tipo (círculo con letra) ─────────────────────────────────────── */
.notif-badge {
  width: 36px; height: 36px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 14px; font-weight: 700;
  flex-shrink: 0;
}

/* ── Head ─────────────────────────────────────────────────────────────────── */
.notif-card__head {
  display: flex; align-items: flex-start; gap: 10px; margin-bottom: 6px;
}
.notif-card__info  { flex: 1; min-width: 0; }
.notif-card__titulo {
  font-size: 14px; font-weight: 600;
  color: #1e293b; line-height: 1.25;
}
.notif-card__meta  { display: flex; align-items: center; gap: 4px; margin-top: 2px; }
.notif-card__ts        { font-size: 11px; color: #94a3b8; }
.notif-card__remitente { font-size: 11px; color: #94a3b8; }
.notif-card__tags {
  display: flex; flex-direction: column; gap: 3px; align-items: flex-end;
  flex-shrink: 0;
}
.notif-card__mensaje {
  font-size: 13px; color: #64748b; margin: 0; line-height: 1.45;
}

.notif-card__nav-hint {
  font-size: 11.5px; font-weight: 700;
  color: #2563eb; margin-top: 6px; text-align: right;
}

/* ── Punto de no leída ─────────────────────────────────────────────────────── */
.notif-punto {
  position: absolute; top: 14px; right: 14px;
  width: 8px; height: 8px; border-radius: 50%;
  background: #2563eb; box-shadow: 0 0 0 2px #ffffff;
}

/* ── Cargar más ────────────────────────────────────────────────────────────── */
.notif-cargar-mas {
  display: flex; justify-content: center; margin-top: 16px;
}
.notif-cargar-mas :deep(.n-button) {
  border: 1.5px solid #e2e8f0 !important;
  background: #f8fafc !important;
  color: #1e40af !important;
  font-weight: 600 !important;
}
.notif-cargar-mas :deep(.n-button:hover) {
  border-color: #2563eb !important;
  background: #eff6ff !important;
}
</style>