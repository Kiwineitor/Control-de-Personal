<script lang="ts" setup>
import type { RegistrarMovimientoRequest, MetodoRegistro } from '#/api/sisu/monitoreo';
import { ref } from 'vue';
import { useVbenModal } from '@vben/common-ui';
import { message } from '#/adapter/naive';
import {
  NForm, NFormItem, NSelect, NInput, NButton, NAlert, NTag, NGrid, NGridItem,
} from 'naive-ui';
import type { SelectOption } from 'naive-ui';
import { registrarEntrada, registrarSalida } from '#/api/sisu/monitoreo';

const emit = defineEmits<{ success: [] }>();

const METODO_OPTIONS: SelectOption[] = [
  { label: '✏ Manual',    value: 'MANUAL' },
  { label: '📱 QR',       value: 'QR'     },
  { label: '📍 GPS',      value: 'GPS'    },
  { label: '📷 Foto',     value: 'FOTO'   },
];

interface Context {
  idInternoJornada?:  number;
  idTitularJornada?:  number;
  nombrePersonal:     string;
  tipoPersonal:       'INTERNO' | 'TITULAR';
  tipoMovimiento:     'entrada' | 'salida';
}

const loading = ref(false);
const ctx     = ref<Context>({
  nombrePersonal: '', tipoPersonal: 'INTERNO', tipoMovimiento: 'entrada',
});

const form = ref({
  metodo:        'MANUAL' as MetodoRegistro,
  latitud:       '',
  longitud:      '',
  observaciones: '',
});

const [Modal, modalApi] = useVbenModal({
  onOpenChange(isOpen: boolean) {
    if (!isOpen) return;
    ctx.value  = modalApi.getData<Context>() ?? ctx.value;
    form.value = { metodo: 'MANUAL', latitud: '', longitud: '', observaciones: '' };
  },
});

async function handleSubmit() {
  loading.value = true;
  try {
    const req: RegistrarMovimientoRequest = {
      idInternoJornada: ctx.value.idInternoJornada,
      idTitularJornada: ctx.value.idTitularJornada,
      metodo:           form.value.metodo,
      latitud:          form.value.latitud  ? Number(form.value.latitud)  : undefined,
      longitud:         form.value.longitud ? Number(form.value.longitud) : undefined,
      observaciones:    form.value.observaciones || undefined,
    };

    if (ctx.value.tipoMovimiento === 'entrada') {
      await registrarEntrada(req);
      message.success(`Entrada registrada para ${ctx.value.nombrePersonal}`);
    } else {
      await registrarSalida(req);
      message.success(`Salida registrada para ${ctx.value.nombrePersonal}`);
    }
    modalApi.close();
    emit('success');
  } catch (e: any) {
    message.error(e?.message ?? 'Error al registrar');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <Modal
    :title="ctx.tipoMovimiento === 'entrada' ? '🟢 Registrar Entrada' : '🔴 Registrar Salida'"
    :footer="false"
    style="width: 440px"
  >
    <div class="space-y-4">

      <NAlert
        :type="ctx.tipoMovimiento === 'entrada' ? 'success' : 'warning'"
        :show-icon="true"
        style="font-size:.81rem"
      >
        {{ ctx.tipoMovimiento === 'entrada' ? 'Registrando entrada de' : 'Registrando salida de' }}
        <strong> {{ ctx.nombrePersonal }}</strong>
        <NTag
          :type="ctx.tipoPersonal === 'INTERNO' ? 'info' : 'error'"
          size="small" round class="ml-2"
        >
          {{ ctx.tipoPersonal }}
        </NTag>
      </NAlert>

      <NForm label-placement="top" :show-require-mark="false">
        <NFormItem label="Método de registro">
          <NSelect v-model:value="form.metodo" :options="METODO_OPTIONS" style="width:100%" />
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

        <NFormItem label="Observaciones (opcional)">
          <NInput
            v-model:value="form.observaciones"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 3 }"
            placeholder="Notas del registro…"
          />
        </NFormItem>
      </NForm>

      <div class="flex justify-end gap-2">
        <NButton @click="modalApi.close()">Cancelar</NButton>
        <NButton
          :type="ctx.tipoMovimiento === 'entrada' ? 'success' : 'warning'"
          :loading="loading"
          @click="handleSubmit"
        >
          {{ ctx.tipoMovimiento === 'entrada' ? 'Registrar Entrada' : 'Registrar Salida' }}
        </NButton>
      </div>
    </div>
  </Modal>
</template>