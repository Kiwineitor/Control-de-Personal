<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { NButton, NEmpty, NInput, NModal, NTag, NSpin } from 'naive-ui';
import { message } from '#/adapter/naive';
import { getMisFaltas, justificarIncidencia, type MiFaltaDto } from '#/api/sisu/app';

// ── Estado ────────────────────────────────────────────────────────────────────
const lista    = ref<MiFaltaDto[]>([]);
const cargando = ref(false);
const filtro   = ref<string>('TODOS');

const FILTROS = [
  { value: 'TODOS',               label: 'Todos' },
  { value: 'FUERA_ZONA',          label: 'Fuera de zona' },
  { value: 'ABANDONO_CONFIRMADO', label: 'Confirmado' },
  { value: 'RETORNADO',           label: 'Retornado' },
  { value: 'RESUELTO',            label: 'Resuelto' },
];

const listaFiltrada = computed(() =>
  filtro.value === 'TODOS'
    ? lista.value
    : lista.value.filter(f => f.estado === filtro.value),
);

// ── Carga ─────────────────────────────────────────────────────────────────────
async function cargar() {
  cargando.value = true;
  try {
    const data = await getMisFaltas();
    lista.value = data ?? [];
  } catch {
    lista.value = [];
  } finally {
    cargando.value = false;
  }
}

onMounted(() => cargar());

// ── Helpers visuales ──────────────────────────────────────────────────────────
const ESTADO_TAG: Record<string, string> = {
  FUERA_ZONA:          'warning',
  ABANDONO_CONFIRMADO: 'error',
  RETORNADO:           'success',
  RESUELTO:            'default',
};
const ESTADO_LABEL: Record<string, string> = {
  FUERA_ZONA:          'Fuera de zona',
  ABANDONO_CONFIRMADO: 'Abandono confirmado',
  RETORNADO:           'Retornó a zona',
  RESUELTO:            'Resuelto',
};

function iconoFalta(estado: string): { letra: string; color: string; bg: string } {
  const m: Record<string, { letra: string; color: string; bg: string }> = {
    FUERA_ZONA:          { letra: 'F', color: '#92400e', bg: '#fef3c7' },
    ABANDONO_CONFIRMADO: { letra: 'A', color: '#991b1b', bg: '#fee2e2' },
    RETORNADO:           { letra: 'R', color: '#065f46', bg: '#d1fae5' },
    RESUELTO:            { letra: 'O', color: '#1e293b', bg: '#f1f5f9' },
  };
  return m[estado] ?? { letra: '?', color: '#64748b', bg: '#f1f5f9' };
}

function fmtFecha(ts: string | null) {
  if (!ts) return '—';
  try { return new Date(ts).toLocaleString('es-PE'); } catch { return ts; }
}

function fmtMinutos(min: number) {
  if (min < 60) return `${min} min`;
  const h = Math.floor(min / 60);
  const m = min % 60;
  return m > 0 ? `${h}h ${m}min` : `${h}h`;
}

// ── Resumen ───────────────────────────────────────────────────────────────────
const totalAbandonos = computed(
  () => lista.value.filter(f => f.estado === 'ABANDONO_CONFIRMADO').length,
);
const totalRetornados = computed(
  () => lista.value.filter(f => f.estado === 'RETORNADO').length,
);
const totalResueltos = computed(
  () => lista.value.filter(f => f.estado === 'RESUELTO').length,
);

// ── Justificación ───────────────────────────────────────────────────────────
const mostrarModal = ref(false);
const eventoSel    = ref<MiFaltaDto | null>(null);
const motivoJust   = ref('');
const archivoJust  = ref<File | null>(null);
const enviando     = ref(false);

// Tag de color según el estado de justificación.
const JUST_TAG: Record<string, string> = {
  PENDIENTE:   'warning',
  EN_REVISION: 'info',
  JUSTIFICADA: 'success',
  RECHAZADA:   'error',
};

function abrirJustificar(falta: MiFaltaDto) {
  eventoSel.value    = falta;
  motivoJust.value   = '';
  archivoJust.value  = null;
  mostrarModal.value = true;
}

async function enviarJustificacion() {
  if (!eventoSel.value) return;
  if (!motivoJust.value.trim()) {
    message.warning('Escribe el motivo de tu justificación.');
    return;
  }
  enviando.value = true;
  try {
    await justificarIncidencia(
      eventoSel.value.idEvento,
      motivoJust.value.trim(),
      archivoJust.value,
    );
    message.success('Justificación enviada. Queda pendiente de revisión.');
    mostrarModal.value = false;
    await cargar();
  } catch (e: any) {
    message.error(e?.message ?? 'No se pudo enviar la justificación.');
  } finally {
    enviando.value = false;
  }
}
</script>

<template>
  <div class="faltas-page">

    <!-- Cabecera -->
    <div class="faltas-header">
      <h2 class="faltas-title">Mis Faltas</h2>
      <button class="faltas-refresh" :disabled="cargando" @click="cargar()">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="2.2" width="16" height="16"
             :class="{ 'faltas-spin': cargando }">
          <path d="M23 4v6h-6"/><path d="M1 20v-6h6"/>
          <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
        </svg>
      </button>
    </div>

    <!-- Cards de resumen -->
    <div v-if="lista.length > 0" class="faltas-resumen">
      <div class="resumen-card resumen-card--error">
        <div class="resumen-card__valor">{{ totalAbandonos }}</div>
        <div class="resumen-card__label">Abandonos</div>
      </div>
      <div class="resumen-card resumen-card--success">
        <div class="resumen-card__valor">{{ totalRetornados }}</div>
        <div class="resumen-card__label">Retornados</div>
      </div>
      <div class="resumen-card resumen-card--default">
        <div class="resumen-card__valor">{{ totalResueltos }}</div>
        <div class="resumen-card__label">Resueltos</div>
      </div>
    </div>

    <!-- Filtros -->
    <div v-if="lista.length > 0" class="faltas-filtros">
      <button
        v-for="f in FILTROS"
        :key="f.value"
        class="filtro-btn"
        :class="{ 'filtro-btn--active': filtro === f.value }"
        @click="filtro = f.value"
      >
        {{ f.label }}
      </button>
    </div>

    <!-- Loading -->
    <div v-if="cargando" class="faltas-center">
      <NSpin size="large" />
    </div>

    <template v-else>
      <!-- Vacío -->
      <div v-if="listaFiltrada.length === 0" style="padding:50px 0">
        <NEmpty :description="lista.length === 0
          ? 'Sin registros de faltas'
          : 'Sin resultados para este filtro'">
          <template #icon>
            <svg v-if="lista.length === 0" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" stroke-width="1.8" style="width:48px;height:48px">
              <circle cx="12" cy="12" r="10" />
              <polyline points="12 6 12 12 16 14" />
            </svg>
            <svg v-else viewBox="0 0 24 24" fill="none" stroke="#94a3b8" stroke-width="1.8" style="width:48px;height:48px">
              <circle cx="11" cy="11" r="8" />
              <line x1="21" y1="21" x2="16.65" y2="16.65" />
            </svg>
          </template>
        </NEmpty>
        <p v-if="lista.length === 0" class="faltas-empty-sub">
          No se registraron salidas de zona durante tus turnos.
        </p>
      </div>

      <!-- Lista -->
      <div v-else class="faltas-list">
        <div
          v-for="falta in listaFiltrada"
          :key="falta.idEvento"
          class="falta-card"
          :class="`falta-card--${falta.estado.toLowerCase().replace('_', '-')}`"
        >
          <!-- Head -->
          <div class="falta-card__head">
            <div
              class="falta-badge"
              :style="{ background: iconoFalta(falta.estado).bg, color: iconoFalta(falta.estado).color }"
            >
              {{ iconoFalta(falta.estado).letra }}
            </div>
            <div class="falta-card__info">
              <div class="falta-card__lugar">
                {{ falta.nombreLugar ?? 'Lugar no especificado' }}
              </div>
              <div class="falta-card__fecha">
                {{ fmtFecha(falta.timestampSalida) }}
              </div>
            </div>
            <NTag
              :type="(ESTADO_TAG[falta.estado] ?? 'default') as any"
              size="small"
              :bordered="false"
            >
              {{ falta.tipoLabel ?? (ESTADO_LABEL[falta.estado] ?? falta.estado) }}
            </NTag>
          </div>

          <!-- Detalle -->
          <div class="falta-card__detalle">
            <div class="falta-detalle-row">
              <span class="falta-detalle-label">Tiempo fuera</span>
              <span class="falta-detalle-valor">{{ fmtMinutos(falta.tiempoFueraMinutos) }}</span>
            </div>
            <div v-if="falta.timestampRetorno" class="falta-detalle-row">
              <span class="falta-detalle-label">Retorno</span>
              <span class="falta-detalle-valor">{{ fmtFecha(falta.timestampRetorno) }}</span>
            </div>
            <div v-if="falta.observaciones" class="falta-detalle-row falta-detalle-row--obs">
              <span class="falta-detalle-label">Observaciones</span>
              <span class="falta-detalle-valor">{{ falta.observaciones }}</span>
            </div>
          </div>

          <!-- Estado de justificación -->
          <div
            v-if="falta.estadoJustificacion && falta.estadoJustificacion !== 'NO_REQUERIDA'"
            class="falta-just"
          >
            <span class="falta-just__label">Justificación</span>
            <NTag
              :type="(JUST_TAG[falta.estadoJustificacion] ?? 'default') as any"
              size="small"
              :bordered="false"
            >
              {{ falta.estadoJustificacionLabel }}
            </NTag>
          </div>
          <div v-if="falta.observacionRevision" class="falta-just__obs">
            “{{ falta.observacionRevision }}”
          </div>

          <!-- Acción justificar -->
          <div v-if="falta.puedeJustificar" class="falta-just-action">
            <NButton size="small" type="primary" secondary block @click="abrirJustificar(falta)">
              Justificar esta incidencia
            </NButton>
          </div>
        </div>
      </div>
    </template>

    <!-- Modal: justificar incidencia -->
    <NModal
      v-model:show="mostrarModal"
      preset="card"
      title="Justificar incidencia"
      :style="{ maxWidth: '92vw', width: '420px' }"
    >
      <div class="just-modal">
        <p class="just-modal__sub">
          Explica brevemente qué ocurrió. Un administrador revisará tu justificación.
        </p>
        <NInput
          v-model:value="motivoJust"
          type="textarea"
          placeholder="Motivo de la justificación…"
          :rows="4"
          :maxlength="500"
          show-count
        />
        <label class="just-modal__file">
          <span>Adjuntar comprobante (opcional)</span>
          <input
            type="file"
            accept="image/*,application/pdf"
            @change="e => archivoJust = (e.target as HTMLInputElement).files?.[0] ?? null"
          />
        </label>
        <div class="just-modal__actions">
          <NButton :disabled="enviando" @click="mostrarModal = false">Cancelar</NButton>
          <NButton type="primary" :loading="enviando" @click="enviarJustificacion()">
            Enviar
          </NButton>
        </div>
      </div>
    </NModal>

  </div>
</template>

<style scoped>
.faltas-page   { padding-bottom: 20px; }
.faltas-center { display: flex; justify-content: center; padding: 60px 0; }

/* ── Header ────────────────────────────────────────────────────────────────── */
.faltas-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 16px; padding: 14px 16px;
  background: #ffffff; border: 1px solid #e2e8f0; border-radius: 14px;
  position: relative;
}
.faltas-header::before {
  content: ''; position: absolute; top: 0; left: 0; right: 0; height: 3px;
  background: linear-gradient(90deg, #2563eb, #1d4ed8);
  border-radius: 14px 14px 0 0;
}
.faltas-title { font-size: 17px; font-weight: 700; color: #1e293b; margin: 0; }

.faltas-refresh {
  width: 34px; height: 34px; display: flex; align-items: center; justify-content: center;
  border: 1px solid #e2e8f0; background: #f8fafc; color: #64748b;
  border-radius: 9px; cursor: pointer; transition: all 0.18s;
  -webkit-tap-highlight-color: transparent;
}
.faltas-refresh:active { background: #e2e8f0; color: #1e293b; }
.faltas-refresh:disabled { opacity: 0.5; }
.faltas-spin { animation: faltas-spin 1s linear infinite; }
@keyframes faltas-spin { to { transform: rotate(360deg); } }

/* ── Resumen ────────────────────────────────────────────────────────────────── */
.faltas-resumen {
  display: flex; gap: 10px; margin-bottom: 14px;
}
.resumen-card {
  flex: 1; border-radius: 14px; padding: 14px 8px;
  text-align: center; border: 1px solid transparent;
}
.resumen-card--error   { background: #fee2e2; border-color: #fca5a5; }
.resumen-card--success { background: #d1fae5; border-color: #86efac; }
.resumen-card--default { background: #f8fafc; border-color: #e2e8f0; }
.resumen-card__valor   { font-size: 24px; font-weight: 800; }
.resumen-card--error   .resumen-card__valor { color: #dc2626; }
.resumen-card--success .resumen-card__valor { color: #059669; }
.resumen-card--default .resumen-card__valor { color: #64748b; }
.resumen-card__label   { font-size: 11px; color: #64748b; font-weight: 500; margin-top: 2px; }

/* ── Filtros ────────────────────────────────────────────────────────────────── */
.faltas-filtros {
  display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 14px;
}
.filtro-btn {
  padding: 5px 14px; border-radius: 20px; font-size: 12px; font-weight: 500;
  border: 1.5px solid #e2e8f0; background: #ffffff; cursor: pointer;
  color: #64748b; transition: all 0.18s;
  -webkit-tap-highlight-color: transparent;
}
.filtro-btn--active {
  background: #2563eb; color: #ffffff; border-color: #2563eb;
}

/* ── Empty ──────────────────────────────────────────────────────────────────── */
.faltas-empty-sub {
  text-align: center; color: #64748b; font-size: 13px;
  margin-top: 8px; padding: 0 20px;
}

/* ── Lista ──────────────────────────────────────────────────────────────────── */
.faltas-list { display: flex; flex-direction: column; gap: 10px; }

/* ── Card ───────────────────────────────────────────────────────────────────── */
.falta-card {
  background: #ffffff; border: 1px solid #e2e8f0;
  border-radius: 14px; padding: 14px 16px;
  border-left: 4px solid transparent;
  box-shadow: 0 2px 6px rgba(30, 64, 175, 0.04);
}
.falta-card--fuera-zona          { border-left-color: #d97706; }
.falta-card--abandono-confirmado { border-left-color: #dc2626; }
.falta-card--retornado           { border-left-color: #059669; }
.falta-card--resuelto            { border-left-color: #94a3b8; }

/* ── Badge de estado (círculo con letra) ─────────────────────────────────────── */
.falta-badge {
  width: 36px; height: 36px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 14px; font-weight: 700;
  flex-shrink: 0;
}

/* ── Head ───────────────────────────────────────────────────────────────────── */
.falta-card__head {
  display: flex; align-items: flex-start; gap: 10px; margin-bottom: 10px;
}
.falta-card__info  { flex: 1; min-width: 0; }
.falta-card__lugar { font-size: 14px; font-weight: 700; color: #1e293b; line-height: 1.2; }
.falta-card__fecha { font-size: 11px; color: #94a3b8; margin-top: 2px; }

/* ── Detalle ────────────────────────────────────────────────────────────────── */
.falta-card__detalle { display: flex; flex-direction: column; gap: 5px; }
.falta-detalle-row {
  display: flex; justify-content: space-between; align-items: baseline; gap: 8px;
}
.falta-detalle-row--obs { flex-direction: column; gap: 2px; }
.falta-detalle-label {
  font-size: 11px; color: #64748b; flex-shrink: 0;
}
.falta-detalle-valor {
  font-size: 12.5px; font-weight: 600; color: #1e293b;
  text-align: right;
}
.falta-detalle-row--obs .falta-detalle-valor {
  text-align: left; font-style: italic; color: #64748b; font-weight: 400;
}

/* ── Justificación ────────────────────────────────────────────────────────── */
.falta-just {
  display: flex; align-items: center; gap: 8px;
  margin-top: 10px; padding-top: 10px; border-top: 1px dashed #e2e8f0;
}
.falta-just__label { font-size: 11px; color: #64748b; }
.falta-just__obs {
  font-size: 11.5px; color: #64748b; font-style: italic; margin-top: 6px;
}
.falta-just-action { margin-top: 10px; }

.just-modal { display: flex; flex-direction: column; gap: 12px; }
.just-modal__sub { font-size: 13px; color: #64748b; margin: 0; }
.just-modal__file {
  display: flex; flex-direction: column; gap: 6px;
  font-size: 12px; color: #475569;
}
.just-modal__actions {
  display: flex; justify-content: flex-end; gap: 8px; margin-top: 4px;
}
</style>
