<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { NButton, NCard, NEmpty, NModal, NInput, NSpin, NTag, NSpace } from 'naive-ui';
import { message } from '#/adapter/naive';
import { useWebSocket } from '#/composables/useWebSocket';
import type { EventoAbandonoDto } from '#/api/sisu/monitoreo';
import { getAlertasActivas, resolverAlerta } from '#/api/sisu/monitoreo';

// ── State ──────────────────────────────────────────────────
const alertas        = ref<EventoAbandonoDto[]>([]);
const cargando       = ref(false);
const resolviendoId  = ref<number | null>(null);
const modalResolver  = ref(false);
const alertaAResolver = ref<EventoAbandonoDto | null>(null);
const obsResolver    = ref('');
let   timerRef: ReturnType<typeof setInterval> | null = null;
const ahora          = ref(Date.now());  // para timers en vivo

// ── STOMP: actualizar al recibir alerta ─────────────────────
const { alertasMonitoreo } = useWebSocket();
watch(alertasMonitoreo, () => cargar(), { deep: true });

// ── Computed ───────────────────────────────────────────────
const alertasActivas = computed(() =>
  alertas.value.filter(a => !a.resuelto).sort((a, b) => b.tiempoFueraMinutos - a.tiempoFueraMinutos),
);
const countAbandono = computed(() =>
  alertasActivas.value.filter(a => a.estado === 'ABANDONO_CONFIRMADO').length,
);

function minutosDesde(ts: string): number {
  return Math.floor((ahora.value - new Date(ts).getTime()) / 60_000);
}

function colorEstado(estado: string) {
  if (estado === 'ABANDONO_CONFIRMADO') return 'error';
  if (estado === 'FUERA_ZONA')          return 'warning';
  if (estado === 'RETORNADO')           return 'success';
  return 'default';
}

function etiquetaEstado(estado: string) {
  const map: Record<string, string> = {
    FUERA_ZONA:          '⚠️ Fuera de zona',
    ABANDONO_CONFIRMADO: '🚨 Abandono',
    RETORNADO:           '✅ Retornado',
    RESUELTO:            '✅ Resuelto',
  };
  return map[estado] ?? estado;
}

// ── API ────────────────────────────────────────────────────
async function cargar() {
  cargando.value = true;
  try {
    alertas.value = await getAlertasActivas();
  } catch { /* silencioso */ } finally {
    cargando.value = false;
  }
}

function abrirResolver(a: EventoAbandonoDto) {
  alertaAResolver.value = a;
  obsResolver.value     = '';
  modalResolver.value   = true;
}

async function confirmarResolver() {
  if (!alertaAResolver.value) return;
  resolviendoId.value = alertaAResolver.value.idEvento;
  try {
    await resolverAlerta(alertaAResolver.value.idEvento, obsResolver.value || undefined);
    message.success('Alerta resuelta correctamente');
    modalResolver.value = false;
    await cargar();
  } catch (e: any) {
    message.error(e?.message ?? 'Error al resolver');
  } finally {
    resolviendoId.value = null;
  }
}

// ── Lifecycle ──────────────────────────────────────────────
onMounted(async () => {
  await cargar();
  // Actualizar timers cada minuto
  timerRef = setInterval(() => {
    ahora.value = Date.now();
  }, 60_000);
});

onUnmounted(() => {
  if (timerRef) clearInterval(timerRef);
});

// Exponer count para badge en tab
defineExpose({ countAbandono });
</script>

<template>
  <div class="alertas-panel">

    <!-- Resumen -->
    <div class="alertas-resumen">
      <NCard size="small" class="resumen-card resumen-card--abandono" v-if="countAbandono > 0">
        <span class="resumen-num">{{ countAbandono }}</span>
        <span class="resumen-lbl">abandono(s) confirmado(s)</span>
      </NCard>
      <NCard size="small" class="resumen-card resumen-card--fuera">
        <span class="resumen-num">{{ alertasActivas.length }}</span>
        <span class="resumen-lbl">alerta(s) activa(s)</span>
      </NCard>
    </div>

    <NSpin :show="cargando">
      <NEmpty
        v-if="!cargando && alertasActivas.length === 0"
        description="Sin alertas activas en este momento"
        class="py-8"
      />

      <div v-else class="alertas-lista">
        <NCard
          v-for="a in alertasActivas"
          :key="a.idEvento"
          size="small"
          class="alerta-card"
          :class="`alerta-card--${a.estado === 'ABANDONO_CONFIRMADO' ? 'abandono' : 'fuera'}`"
        >
          <div class="alerta-row">
            <div class="alerta-info">
              <div class="alerta-nombre">{{ a.nombrePersonal }}</div>
              <div class="alerta-sub">
                {{ a.cargo ?? a.tipoPersonal }} · {{ a.nombreLugar ?? 'Sin lugar' }}
              </div>
              <NSpace size="small" class="mt-1">
                <NTag :type="colorEstado(a.estado)" size="small" round>
                  {{ etiquetaEstado(a.estado) }}
                </NTag>
                <NTag type="default" size="small">
                  Salida: {{ minutosDesde(a.timestampSalida) }} min atrás
                </NTag>
                <NTag v-if="a.distanciaAlSalirMetros" type="default" size="small">
                  {{ Math.round(Number(a.distanciaAlSalirMetros)) }}m del límite
                </NTag>
              </NSpace>
            </div>
            <div class="alerta-timer" :class="a.estado === 'ABANDONO_CONFIRMADO' ? 'timer--red' : 'timer--orange'">
              <span class="timer-num">{{ a.tiempoFueraMinutos }}</span>
              <span class="timer-lbl">min fuera</span>
            </div>
          </div>

          <div class="alerta-footer">
            <NButton
              size="small"
              type="primary"
              :loading="resolviendoId === a.idEvento"
              @click="abrirResolver(a)"
            >
              ✓ Resolver
            </NButton>
          </div>
        </NCard>
      </div>
    </NSpin>

    <!-- Modal resolver -->
    <NModal
      v-model:show="modalResolver"
      preset="card"
      title="Resolver alerta"
      style="width:420px"
    >
      <p style="font-size:14px;margin-bottom:12px">
        ¿Resolver alerta de <strong>{{ alertaAResolver?.nombrePersonal }}</strong>?
      </p>
      <NInput
        v-model:value="obsResolver"
        type="textarea"
        :rows="3"
        placeholder="Observaciones (opcional)..."
      />
      <template #footer>
        <NSpace justify="end">
          <NButton @click="modalResolver = false">Cancelar</NButton>
          <NButton type="primary" :loading="!!resolviendoId" @click="confirmarResolver">
            Confirmar
          </NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.alertas-panel { display: flex; flex-direction: column; gap: 12px; }
.alertas-resumen { display: flex; gap: 10px; }
.resumen-card { border-radius: 10px; flex: 1; }
.resumen-card--abandono { background: #fff0f0; border-color: #e03131; }
.resumen-card--fuera    { background: #fff9e6; border-color: #f08c00; }
.resumen-num  { font-size: 28px; font-weight: 800; display: block; line-height: 1; }
.resumen-lbl  { font-size: 12px; color: #888; }

.alertas-lista { display: flex; flex-direction: column; gap: 10px; }
.alerta-card   { border-radius: 12px; }
.alerta-card--abandono { background: #fff5f5; border-color: #e03131; }
.alerta-card--fuera    { background: #fffbf0; border-color: #f08c00; }

.alerta-row    { display: flex; gap: 12px; align-items: flex-start; }
.alerta-info   { flex: 1; }
.alerta-nombre { font-size: 15px; font-weight: 700; color: #1a1a2e; }
.alerta-sub    { font-size: 12px; color: #888; margin-top: 2px; }

.alerta-timer  { display: flex; flex-direction: column; align-items: center;
                 min-width: 56px; padding: 8px; border-radius: 10px; }
.timer--orange { background: #fff3e0; }
.timer--red    { background: #ffebee; }
.timer-num     { font-size: 22px; font-weight: 800; line-height: 1; }
.timer--orange .timer-num { color: #f08c00; }
.timer--red    .timer-num { color: #e03131; }
.timer-lbl     { font-size: 10px; color: #888; text-align: center; }

.alerta-footer { margin-top: 8px; display: flex; justify-content: flex-end; }
</style>
