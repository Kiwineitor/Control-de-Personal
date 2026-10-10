<script lang="ts" setup>
import { ref, watch, computed } from 'vue';
import { useAccessStore } from '@vben/stores';
import { NCard, NSpin, NEmpty, NButton, NText, NDivider } from 'naive-ui';
import type { BitacoraEntrada } from '#/api/sisu/bitacora';
import { bitacoraJornadaAdmin } from '#/api/sisu/bitacora';
import { message } from '#/adapter/naive';

const props = defineProps<{ idJornada: number | null }>();

const accessStore = useAccessStore();
const entradas = ref<BitacoraEntrada[]>([]);
const cargando = ref(false);

interface GrupoBitacora {
  nombre: string;
  entradas: BitacoraEntrada[];
}

const grupos = computed<GrupoBitacora[]>(() => {
  const map = new Map<string, BitacoraEntrada[]>();
  for (const e of entradas.value) {
    const key = e.nombrePersonal ?? 'Sin asignar';
    if (!map.has(key)) map.set(key, []);
    map.get(key)!.push(e);
  }
  return Array.from(map.entries()).map(([nombre, items]) => ({
    nombre,
    entradas: items.sort(
      (a, b) => new Date(b.fechaHoraEvento).getTime() - new Date(a.fechaHoraEvento).getTime(),
    ),
  }));
});

function formatearFecha(fecha: string): string {
  return new Date(fecha).toLocaleString('es-PE', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

async function verEvidencia(e: BitacoraEntrada) {
  const url = e.evidenciaDownloadUrl ?? e.evidenciaUrl;
  if (!url) return;
  try {
    const resp = await fetch(url, {
      headers: { Authorization: `Bearer ${accessStore.accessToken}` },
    });
    if (!resp.ok) throw new Error();
    const blob = await resp.blob();
    const objectUrl = URL.createObjectURL(blob);
    window.open(objectUrl, '_blank');
    setTimeout(() => URL.revokeObjectURL(objectUrl), 15_000);
  } catch {
    message.error('No se pudo abrir la evidencia');
  }
}

watch(() => props.idJornada, async (id) => {
  if (!id) { entradas.value = []; return; }
  cargando.value = true;
  try {
    entradas.value = (await bitacoraJornadaAdmin(id)) ?? [];
  } catch {
    message.error('Error al cargar bitácora de la jornada');
  } finally {
    cargando.value = false;
  }
}, { immediate: true });
</script>

<template>
  <NSpin :show="cargando">
    <div v-if="!cargando && entradas.length === 0" class="py-10">
      <NEmpty description="No hay entradas de bitácora para esta jornada" />
    </div>

    <div v-else class="bitacora-container">
      <div
        v-for="grupo in grupos"
        :key="grupo.nombre"
        class="mb-4"
      >
        <NDivider title-placement="left">
          <NText strong style="font-size: 0.9rem;">
            {{ grupo.nombre }} ({{ grupo.entradas.length }})
          </NText>
        </NDivider>

        <div class="bitacora-entries">
          <NCard
            v-for="e in grupo.entradas"
            :key="e.idBitacora"
            size="small"
            class="bitacora-entry"
          >
            <div class="flex items-center justify-between mb-1">
              <NText strong style="font-size: 0.85rem;">
                {{ formatearFecha(e.fechaHoraEvento) }}
              </NText>
              <NButton
                v-if="e.evidenciaUrl || e.evidenciaDownloadUrl"
                size="tiny"
                type="primary"
                ghost
                @click="verEvidencia(e)"
              >
                📎 Evidencia
              </NButton>
            </div>
            <div class="text-sm" style="color: hsl(var(--muted-foreground));">
              {{ e.descripcion }}
            </div>
            <div
              v-if="e.nombreLugar"
              class="text-xs mt-1"
              style="color: hsl(var(--muted-foreground));"
            >
              📍 {{ e.nombreLugar }}
            </div>
          </NCard>
        </div>
      </div>
    </div>
  </NSpin>
</template>

<style scoped>
.bitacora-container {
  display: flex;
  flex-direction: column;
}
.bitacora-entries {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.bitacora-entry {
  border-left: 3px solid #3b82f6;
  transition: box-shadow 0.15s;
}
.bitacora-entry:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}
</style>
