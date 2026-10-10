<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { NButton, NCard, NForm, NFormItem, NInputNumber, NSpace, NSpin } from 'naive-ui';
import { message } from '#/adapter/naive';
import type { ConfiguracionMonitoreoDto } from '#/api/sisu/monitoreo';
import { getConfiguracion, updateConfiguracion } from '#/api/sisu/monitoreo';

const cargando  = ref(false);
const guardando = ref(false);
const form      = ref<ConfiguracionMonitoreoDto>({
  timeoutAbandonoMinutos:   20,
  timeoutSinSenalMinutos:   15,
  toleranciaAtrasoMin:      10,
  umbralFaltaMin:           20,
  intervaloGpsActivoSeg:    120,
  intervaloGpsFueraZonaSeg: 30,
  deltaMinMovimientoMetros: 30,
  radioAlertaExtraMetros:   0,
  confirmacionFueraSeg:     60,
  precisionMaxConfiableMetros: 100,
});

onMounted(async () => {
  cargando.value = true;
  try {
    const cfg = await getConfiguracion();
    form.value = { ...cfg };
  } catch { /* usar defaults */ } finally {
    cargando.value = false;
  }
});

async function guardar() {
  guardando.value = true;
  try {
    const updated = await updateConfiguracion(form.value);
    form.value = { ...updated };
    message.success('Configuración guardada correctamente');
  } catch (e: any) {
    message.error(e?.message ?? 'Error al guardar');
  } finally {
    guardando.value = false;
  }
}
</script>

<template>
  <NSpin :show="cargando">
    <NForm label-placement="left" label-width="240px" style="max-width:600px">

      <NCard title="Umbrales de incidencia" class="cfg-card">
        <NFormItem label="Tolerancia de atraso (min)">
          <NInputNumber v-model:value="form.toleranciaAtrasoMin" :min="0" :max="120" />
          <span class="cfg-hint">Minutos de gracia tras la hora de inicio antes de contar atraso. Se evalúa una sola vez al día, en la llegada</span>
        </NFormItem>
        <NFormItem label="Umbral de falta (min)">
          <NInputNumber v-model:value="form.umbralFaltaMin" :min="5" :max="180" />
          <span class="cfg-hint">Minutos desde el inicio del turno sin ningún reporte antes de registrar falta</span>
        </NFormItem>
        <NFormItem label="Timeout de abandono (min)">
          <NInputNumber v-model:value="form.timeoutAbandonoMinutos" :min="1" :max="120" />
          <span class="cfg-hint">Minutos fuera de la zona sin retornar antes de registrar abandono</span>
        </NFormItem>
        <NFormItem label="Timeout sin señal (min)">
          <NInputNumber v-model:value="form.timeoutSinSenalMinutos" :min="1" :max="120" />
          <span class="cfg-hint">Minutos sin recibir ubicación antes de registrar ausencia. Más corto que el abandono: aquí no se sabe dónde está la persona</span>
        </NFormItem>
      </NCard>

      <NCard title="Frecuencia GPS adaptativa" class="cfg-card">
        <NFormItem label="Intervalo dentro de zona (seg)">
          <NInputNumber v-model:value="form.intervaloGpsActivoSeg" :min="30" :max="600" />
          <span class="cfg-hint">Cada cuántos segundos envía GPS cuando está dentro de su área</span>
        </NFormItem>
        <NFormItem label="Intervalo fuera de zona (seg)">
          <NInputNumber v-model:value="form.intervaloGpsFueraZonaSeg" :min="10" :max="120" />
          <span class="cfg-hint">Frecuencia mayor cuando está fuera del área asignada</span>
        </NFormItem>
        <NFormItem label="Delta mínimo de movimiento (m)">
          <NInputNumber v-model:value="form.deltaMinMovimientoMetros" :min="0" :max="500" />
          <span class="cfg-hint">No enviar si el dispositivo no se movió más de X metros</span>
        </NFormItem>
      </NCard>

      <NCard title="Precisión y debounce" class="cfg-card">
        <NFormItem label="Confirmación de salida (seg)">
          <NInputNumber v-model:value="form.confirmacionFueraSeg" :min="5" :max="300" />
          <span class="cfg-hint">Segundos sostenidos fuera de zona antes de confirmar salida (debounce)</span>
        </NFormItem>
        <NFormItem label="Precisión GPS máxima confiable (m)">
          <NInputNumber v-model:value="form.precisionMaxConfiableMetros" :min="10" :max="500" />
          <span class="cfg-hint">Tope de indulgencia: precisiones peores que esto no enmascaran una salida real</span>
        </NFormItem>
      </NCard>

      <NCard title="Alertas geográficas" class="cfg-card">
        <NFormItem label="Radio de alerta previa (m)">
          <NInputNumber v-model:value="form.radioAlertaExtraMetros" :min="0" :max="200" />
          <span class="cfg-hint">Metros de margen adicional para alerta previa (0 = sin alerta previa)</span>
        </NFormItem>
      </NCard>

      <NSpace justify="end" style="margin-top:16px">
        <NButton type="primary" :loading="guardando" @click="guardar">
          💾 Guardar configuración
        </NButton>
      </NSpace>

    </NForm>
  </NSpin>
</template>

<style scoped>
.cfg-card  { border-radius: 12px; margin-bottom: 14px; }
.cfg-hint  { font-size: 11px; color: #999; margin-left: 8px; display: block; margin-top: 3px; }
</style>
