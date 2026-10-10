<script lang="ts" setup>
import type { RondaVerificacionRequest, TipoRonda } from '#/api/sisu/monitoreo';
import { ref } from 'vue';
import { useVbenModal } from '@vben/common-ui';
import { message } from '#/adapter/naive';
import {
  NForm, NFormItem, NInput, NSelect, NInputNumber,
  NButton, NGrid, NGridItem, NDatePicker, NAlert,
} from 'naive-ui';
import type { SelectOption } from 'naive-ui';
import { crearRonda } from '#/api/sisu/monitoreo';
import { getJornadasActivas } from '#/api/sisu/jornada';

const emit = defineEmits<{ success: [] }>();

const TIPO_OPTIONS: SelectOption[] = [
  { label: '🎲 Aleatoria',  value: 'ALEATORIA'  },
  { label: '📅 Programada', value: 'PROGRAMADA' },
  { label: '✏ Manual',      value: 'MANUAL'     },
];

const loading      = ref(false);
const jornadaOpts  = ref<SelectOption[]>([]);

const form = ref({
  nombre:         '',
  tipoRonda:      'ALEATORIA' as TipoRonda,
  fechaInicio:    '',  // yyyy-MM-dd
  horaInicio:     '09:00',
  timeoutMinutos: 10,
  idJornada:      null as number | null,
  observaciones:  '',
});

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) return;
    form.value = {
      nombre: '', tipoRonda: 'ALEATORIA',
      fechaInicio: '', horaInicio: '09:00',
      timeoutMinutos: 10, idJornada: null, observaciones: '',
    };
    try {
      const lista = await getJornadasActivas();
      jornadaOpts.value = [
        { label: '— Todas las jornadas activas —', value: null as any },
        ...lista.map(j => ({
          label: `${j.nombre ?? j.tipoJornada} · ${j.fechaInicio}`,
          value: j.idJornada,
        })),
      ];
    } catch { jornadaOpts.value = []; }
  },
});

const HH_MM = /^([01]\d|2[0-3]):[0-5]\d$/;

async function handleSubmit() {
  if (!form.value.fechaInicio || !/^\d{4}-\d{2}-\d{2}$/.test(form.value.fechaInicio))
  { message.warning('Fecha inválida (usa yyyy-MM-dd)'); return; }
  if (!HH_MM.test(form.value.horaInicio)) { message.warning('Hora inválida (HH:mm)'); return; }

  loading.value = true;
  try {
    const req: RondaVerificacionRequest = {
      nombre:          form.value.nombre        || undefined,
      tipoRonda:       form.value.tipoRonda,
      fechaHoraInicio: `${form.value.fechaInicio}T${form.value.horaInicio}:00`,
      timeoutMinutos:  form.value.timeoutMinutos,
      idJornada:       form.value.idJornada     ?? undefined,
      observaciones:   form.value.observaciones || undefined,
    };
    await crearRonda(req);
    message.success('Ronda creada y verificaciones generadas');
    modalApi.close();
    emit('success');
  } catch (e: any) {
    message.error(e?.message ?? 'Error al crear la ronda');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <Modal title="🔍 Nueva Ronda de Verificación" :footer="false" style="width: 580px">
    <NForm label-placement="top" :show-require-mark="false">

      <NAlert type="info" :show-icon="true" class="mb-4" style="font-size:.81rem">
        Al crear la ronda se generarán automáticamente verificaciones para todo el personal
        de las jornadas activas (o la jornada seleccionada).
      </NAlert>

      <NGrid :cols="2" :x-gap="16">
        <NGridItem>
          <NFormItem label="Tipo de ronda">
            <NSelect v-model:value="form.tipoRonda" :options="TIPO_OPTIONS" style="width:100%" />
          </NFormItem>
        </NGridItem>
        <NGridItem>
          <NFormItem label="Nombre (opcional)">
            <NInput v-model:value="form.nombre" placeholder="Ej: Ronda sorpresa tarde" clearable />
          </NFormItem>
        </NGridItem>
        <NGridItem>
          <NFormItem label="Fecha inicio * (yyyy-MM-dd)">
            <NInput
                v-model:value="form.fechaInicio"
                placeholder="2025-05-15"
                maxlength="10"
                clearable
            />
          </NFormItem>
        </NGridItem>
        <NGridItem>
          <NFormItem label="Hora inicio * (HH:mm)">
            <NInput v-model:value="form.horaInicio" placeholder="09:00" maxlength="5" />
          </NFormItem>
        </NGridItem>
        <NGridItem>
          <NFormItem label="Timeout (minutos)">
            <NInputNumber
              v-model:value="form.timeoutMinutos"
              :min="1" :max="60"
              style="width:100%"
            />
          </NFormItem>
        </NGridItem>
        <NGridItem>
          <NFormItem label="Jornada específica (opcional)">
            <NSelect
              v-model:value="form.idJornada"
              :options="jornadaOpts"
              placeholder="Todas las activas"
              clearable style="width:100%"
            />
          </NFormItem>
        </NGridItem>
      </NGrid>

      <NFormItem label="Observaciones">
        <NInput
          v-model:value="form.observaciones"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 3 }"
          placeholder="Motivo o notas de la ronda…"
        />
      </NFormItem>

      <div class="flex justify-end gap-2 mt-2">
        <NButton @click="modalApi.close()">Cancelar</NButton>
        <NButton type="primary" :loading="loading"
          :style="{ background: '#7c3aed', borderColor: '#7c3aed' }"
          @click="handleSubmit"
        >
          Crear Ronda
        </NButton>
      </div>
    </NForm>
  </Modal>
</template>