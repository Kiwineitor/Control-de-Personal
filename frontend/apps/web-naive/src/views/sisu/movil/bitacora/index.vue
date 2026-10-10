<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { NButton, NSpin, NEmpty } from 'naive-ui';

import { message } from '#/adapter/naive';
import {
  type BitacoraEntrada,
  misEntradasBitacora,
} from '#/api/sisu/bitacora';
import BitacoraModal from './modules/bitacora-modal.vue';

const router = useRouter();

const entradas    = ref<BitacoraEntrada[]>([]);
const cargando    = ref(true);
const modalShow   = ref(false);

onMounted(() => cargar());

async function cargar() {
  cargando.value = true;
  try {
    entradas.value = await misEntradasBitacora(50);
  } catch {
    message.error('Error al cargar bitácora');
  } finally {
    cargando.value = false;
  }
}

function onCreada(_entrada: BitacoraEntrada) {
  cargar();
}

function fmtFecha(iso: string) {
  const d = new Date(iso);
  return d.toLocaleDateString('es-PE', {
    weekday: 'short', day: 'numeric', month: 'short',
  });
}

function fmtHora(iso: string) {
  const d = new Date(iso);
  return d.toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}
</script>

<template>
  <div class="bit-page">
    <!-- Header -->
    <div class="bit-page__header">
      <NButton
        quaternary
        size="small"
        @click="router.back()"
      >
        ← Volver
      </NButton>
      <h2 class="bit-page__title">Bitácora del Turno</h2>
      <NButton
        type="primary"
        size="small"
        @click="modalShow = true"
      >
        + Nueva
      </NButton>
    </div>

    <!-- Loading -->
    <div v-if="cargando" class="bit-page__loading">
      <NSpin size="large" />
    </div>

    <!-- Empty -->
    <NEmpty
      v-else-if="entradas.length === 0"
      description="Sin entradas registradas"
      class="bit-page__empty"
    />

    <!-- Lista -->
    <div v-else class="bit-list">
      <div
        v-for="e in entradas"
        :key="e.idBitacora"
        class="bit-card"
      >
        <div class="bit-card__top">
          <span class="bit-card__fecha">{{ fmtFecha(e.fechaHoraEvento) }}</span>
          <span class="bit-card__hora">{{ fmtHora(e.fechaHoraEvento) }}</span>
        </div>
        <p class="bit-card__desc">{{ e.descripcion }}</p>
        <div class="bit-card__meta">
          <span v-if="e.nombreJornada" class="bit-card__chip">{{ e.nombreJornada }}</span>
          <span v-if="e.nombreLugar" class="bit-card__chip">📍 {{ e.nombreLugar }}</span>
          <span v-if="e.evidenciaDownloadUrl" class="bit-card__chip bit-card__chip--evidence">
            📎 Evidencia
          </span>
        </div>
      </div>
    </div>

    <!-- FAB -->
    <button class="bit-fab" @click="modalShow = true">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" width="24" height="24">
        <line x1="12" y1="5" x2="12" y2="19" />
        <line x1="5" y1="12" x2="19" y2="12" />
      </svg>
    </button>

    <!-- Modal -->
    <BitacoraModal
      :show="modalShow"
      @update:show="modalShow = $event"
      @creada="onCreada"
    />
  </div>
</template>

<style scoped>
.bit-page {
  padding: 16px;
  padding-bottom: 100px;
  min-height: 100dvh;
}

.bit-page__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.bit-page__title {
  font-size: 18px;
  font-weight: 700;
  color: #1e293b;
  margin: 0;
}

.bit-page__loading {
  display: flex;
  justify-content: center;
  padding: 60px 0;
}

.bit-page__empty {
  padding: 60px 0;
}

.bit-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.bit-card {
  background: #fff;
  border-radius: 12px;
  padding: 14px 16px;
  box-shadow: 0 1px 4px rgba(0,0,0,.06);
  border: 1px solid #f1f5f9;
}

.bit-card__top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.bit-card__fecha {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  text-transform: capitalize;
}

.bit-card__hora {
  font-size: 12px;
  font-weight: 700;
  color: #2563eb;
  font-variant-numeric: tabular-nums;
}

.bit-card__desc {
  font-size: 14px;
  color: #475569;
  line-height: 1.45;
  margin: 0 0 8px;
}

.bit-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.bit-card__chip {
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 6px;
  background: #f1f5f9;
  color: #64748b;
  font-weight: 500;
}

.bit-card__chip--evidence {
  background: #eff6ff;
  color: #2563eb;
}

.bit-fab {
  position: fixed;
  bottom: 90px;
  right: 20px;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: #2563eb;
  color: #fff;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 16px rgba(37,99,235,.35);
  cursor: pointer;
  z-index: 10;
  transition: transform .15s;
}

.bit-fab:active {
  transform: scale(.92);
}
</style>
