<script lang="ts" setup>
import { NEmpty, NText } from 'naive-ui';

defineProps<{ idJornada: number | null }>();
</script>

<template>
  <div class="py-10">
    <NEmpty description="Módulo de faltas en desarrollo. Aquí se mostrará el personal que no se presentó detectado por GPS y scheduler.">
      <template #icon><span style="font-size: 44px;">🚧</span></template>
      <template #extra>
        <NText depth="3" style="font-size: 0.85rem;">
          Este módulo integrará datos del sistema de monitoreo GPS y el scheduler de jornadas
          para identificar automáticamente faltas de asistencia.
        </NText>
      </template>
    </NEmpty>
  </div>
</template>
