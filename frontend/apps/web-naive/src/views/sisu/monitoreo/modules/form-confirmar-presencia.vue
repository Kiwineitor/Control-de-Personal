<script lang="ts" setup>
import type { ConfirmarPresenciaRequest, MetodoVerificacion } from '#/api/sisu/monitoreo';
import { ref } from 'vue';
import { useVbenModal } from '@vben/common-ui';
import { message } from '#/adapter/naive';
import {
  NForm, NFormItem, NSelect, NInput, NButton, NAlert, NTag, NGrid, NGridItem,
} from 'naive-ui';
import type { SelectOption } from 'naive-ui';
import { confirmarPresencia } from '#/api/sisu/monitoreo';
import { ESTADO_VERIF_META } from '../data';

const emit = defineEmits<{ success: [] }>();

const METODO_OPTIONS: SelectOption[] = [
  { label: '📍 GPS — Geolocalización',      value: 'GPS'    },
  { label: '📱 QR — Código del lugar',      value: 'QR'     },
  { label: '📷 Foto — Con marca de tiempo', value: 'FOTO'   },
  { label: '✏ Manual — Admin confirma',    value: 'MANUAL' },
];

interface Context {
  idVerificacion:  number;
  nombrePersonal:  string;
  tipoPersonal:    'INTERNO' | 'TITULAR';
  estadoActual:    string;
}

const loading = ref(false);
const ctx     = ref<Context>({ idVerificacion: 0, nombrePersonal: '', tipoPersonal: 'INTERNO', estadoActual: '' });

const form = ref({
  metodo:        'MANUAL' as MetodoVerificacion,
  latitud:       '',
  longitud:      '',
  fotoUrl:       '',
  observaciones: '',
});

const [Modal, modalApi] = useVbenModal({
  onOpenChange(isOpen: boolean) {
    if (!isOpen) return;
    ctx.value  = modalApi.getData<Context>() ?? ctx.value;
    form.value = { metodo: 'MANUAL', latitud: '', longitud: '', fotoUrl: '', observaciones: '' };
  },
});

async function handleSubmit() {
  loading.value = true;
  try {
    const req: ConfirmarPresenciaRequest = {
      metodo:        form.value.metodo,
      latitud:       form.value.latitud  ? Number(form.value.latitud)  : undefined,
      longitud:      form.value.longitud ? Number(form.value.longitud) : undefined,
      fotoUrl:       form.value.fotoUrl  || undefined,
      observaciones: form.value.observaciones || undefined,
    };
    await confirmarPresencia(ctx.value.idVerificacion, req);
    message.success(`Presencia confirmada para ${ctx.value.nombrePersonal}`);
    modalApi.close();
    emit('success');
  } catch (e: any) {
    message.error(e?.message ?? 'Error al confirmar presencia');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <Modal title="✅ Confirmar Presencia" :footer="false" style="width: 480px">
    <div class="space-y-4">

      <NAlert type="info" :show-icon="true" style="font-size:.81rem">
        Confirmando presencia de
        <strong>{{ ctx.nombrePersonal }}</strong>
        <NTag
          :type="ctx.tipoPersonal === 'INTERNO' ? 'info' : 'error'"
          size="small" round class="ml-2"
        >
          {{ ctx.tipoPersonal }}
        </NTag>
      </NAlert>

      <NForm label-placement="top" :show-require-mark="false">

        <NFormItem label="Método de verificación *">
          <NSelect
            v-model:value="form.metodo"
            :options="METODO_OPTIONS"
            style="width:100%"
          />
        </NFormItem>

        <NGrid :cols="2" :x-gap="12">
          <NGridItem>
            <NFormItem label="Latitud (opcional)">
              <NInput v-model:value="form.latitud" placeholder="-16.5000" clearable />
            </NFormItem>
          </NGridItem>
          <NGridItem>
            <NFormItem label="Longitud (opcional)">
              <NInput v-model:value="form.longitud" placeholder="-68.1500" clearable />
            </NFormItem>
          </NGridItem>
        </NGrid>

        <NFormItem v-if="form.metodo === 'FOTO'" label="URL de la foto">
          <NInput v-model:value="form.fotoUrl" placeholder="uploads/verificacion/..." clearable />
        </NFormItem>

        <NFormItem label="Observaciones (opcional)">
          <NInput
            v-model:value="form.observaciones"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 3 }"
            placeholder="Notas adicionales…"
          />
        </NFormItem>

      </NForm>

      <div class="flex justify-end gap-2">
        <NButton @click="modalApi.close()">Cancelar</NButton>
        <NButton
          type="success"
          :loading="loading"
          :style="{ background: '#22c55e', borderColor: '#22c55e' }"
          @click="handleSubmit"
        >
          Confirmar Presente
        </NButton>
      </div>
    </div>
  </Modal>
</template>