<script lang="ts" setup>
import { ref, onMounted } from 'vue';
import { NButton, NCard, NEmpty, NInput, NModal, NSpin, NTag } from 'naive-ui';
import { message } from '#/adapter/naive';
import {
  getIncidenciasPendientesJustificacion,
  aprobarJustificacion,
  rechazarJustificacion,
  type IncidenciaRevisionDto,
} from '#/api/sisu/monitoreo';
import { descargarEvidenciaApp } from '#/api/sisu/app';

const lista    = ref<IncidenciaRevisionDto[]>([]);
const cargando = ref(false);

const mostrar     = ref(false);
const sel         = ref<IncidenciaRevisionDto | null>(null);
const observacion = ref('');
const procesando  = ref(false);

async function cargar() {
  cargando.value = true;
  try {
    lista.value = await getIncidenciasPendientesJustificacion();
  } catch {
    lista.value = [];
  } finally {
    cargando.value = false;
  }
}
onMounted(cargar);

function abrir(item: IncidenciaRevisionDto) {
  sel.value = item;
  observacion.value = '';
  mostrar.value = true;
}

async function decidir(aprobar: boolean) {
  if (!sel.value) return;
  procesando.value = true;
  try {
    const obs = observacion.value.trim() || undefined;
    if (aprobar) await aprobarJustificacion(sel.value.idEvento, obs);
    else         await rechazarJustificacion(sel.value.idEvento, obs);
    message.success(aprobar ? 'Justificación aprobada.' : 'Justificación rechazada.');
    mostrar.value = false;
    await cargar();
  } catch (e: any) {
    message.error(e?.message ?? 'No se pudo procesar la justificación.');
  } finally {
    procesando.value = false;
  }
}

async function verComprobante(u: string) {
  try {
    const blob = await descargarEvidenciaApp(u);
    window.open(URL.createObjectURL(blob), '_blank');
  } catch {
    message.error('No se pudo abrir el comprobante.');
  }
}

function fmt(ts: null | string) {
  if (!ts) return '—';
  try { return new Date(ts).toLocaleString('es-PE'); } catch { return ts; }
}
function fmtMin(min: number) {
  if (min < 60) return `${min} min`;
  const h = Math.floor(min / 60);
  const m = min % 60;
  return m > 0 ? `${h}h ${m}min` : `${h}h`;
}
</script>

<template>
  <NSpin :show="cargando">
    <div class="flex items-center justify-between mb-4">
      <p class="text-sm text-gray-500 m-0">
        Justificaciones enviadas por el personal, pendientes de tu revisión.
      </p>
      <NButton size="small" @click="cargar">↺ Actualizar</NButton>
    </div>

    <NEmpty
      v-if="!cargando && lista.length === 0"
      description="No hay justificaciones pendientes de revisión"
      class="py-12"
    />

    <div v-else class="just-grid">
      <NCard
        v-for="item in lista"
        :key="item.idEvento"
        size="small"
        :bordered="true"
        class="just-card"
      >
        <div class="flex items-start justify-between gap-2 mb-2">
          <div class="min-w-0">
            <div class="font-bold text-sm truncate">{{ item.nombrePersonal }}</div>
            <div class="text-xs text-gray-400">{{ fmt(item.timestampSalida) }}</div>
          </div>
          <NTag type="error" size="small" round :bordered="false">{{ item.tipoLabel }}</NTag>
        </div>

        <div class="just-row"><span>Lugar</span><b>{{ item.nombreLugar ?? '—' }}</b></div>
        <div class="just-row"><span>Tiempo fuera</span><b>{{ fmtMin(item.tiempoFueraMinutos) }}</b></div>

        <div class="just-motivo">
          <span class="just-motivo__label">Motivo del funcionario</span>
          <p class="just-motivo__text">{{ item.motivoJustificacion ?? '—' }}</p>
        </div>

        <div class="flex items-center gap-2 mt-2">
          <NButton
            v-if="item.justificanteUrl"
            size="tiny" tertiary
            @click="verComprobante(item.justificanteUrl)"
          >
            📎 Ver comprobante
          </NButton>
          <NButton size="small" type="primary" @click="abrir(item)">Revisar</NButton>
        </div>
      </NCard>
    </div>

    <!-- Modal de revisión -->
    <NModal
      v-model:show="mostrar"
      preset="card"
      title="Revisar justificación"
      :style="{ maxWidth: '92vw', width: '460px' }"
    >
      <div v-if="sel" class="rev-modal">
        <div class="just-row"><span>Personal</span><b>{{ sel.nombrePersonal }}</b></div>
        <div class="just-row"><span>Tipo</span><b>{{ sel.tipoLabel }}</b></div>
        <div class="just-row"><span>Lugar</span><b>{{ sel.nombreLugar ?? '—' }}</b></div>
        <div class="just-motivo">
          <span class="just-motivo__label">Motivo del funcionario</span>
          <p class="just-motivo__text">{{ sel.motivoJustificacion ?? '—' }}</p>
        </div>

        <NInput
          v-model:value="observacion"
          type="textarea"
          placeholder="Observación (opcional)…"
          :rows="3"
          :maxlength="500"
          show-count
        />

        <div class="rev-modal__actions">
          <NButton :disabled="procesando" @click="mostrar = false">Cancelar</NButton>
          <NButton type="error" :loading="procesando" @click="decidir(false)">Rechazar</NButton>
          <NButton type="primary" :loading="procesando" @click="decidir(true)">Aprobar</NButton>
        </div>
      </div>
    </NModal>
  </NSpin>
</template>

<style scoped>
.just-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 14px;
}
.just-card { border-top: 3px solid #f59e0b; }
.just-row {
  display: flex; justify-content: space-between; align-items: baseline;
  font-size: 12.5px; padding: 2px 0;
}
.just-row span { color: #64748b; }
.just-motivo { margin-top: 8px; }
.just-motivo__label { font-size: 11px; color: #64748b; }
.just-motivo__text {
  margin: 2px 0 0; font-size: 13px; color: #1e293b;
  background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 8px;
  white-space: pre-wrap; word-break: break-word;
}
.rev-modal { display: flex; flex-direction: column; gap: 10px; }
.rev-modal__actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 4px; }
</style>
