<script lang="ts" setup>
import type { ActividadDto, ActividadRequest, TipoActividad } from '#/api/sisu/actividades';
import { ref, computed } from 'vue';
import { useVbenModal } from '@vben/common-ui';
import { message } from '#/adapter/naive';
import { NForm, NFormItem, NInput, NSelect, NSwitch, NButton, NDivider } from 'naive-ui';
import type { SelectOption } from 'naive-ui';
import { createActividad, updateActividad } from '#/api/sisu/actividades';

const emit = defineEmits<{ success: [] }>();

const TIPO_OPTIONS: SelectOption[] = [
  { label: '🏥 Clínica',        value: 'CLINICA'        },
  { label: '📋 Administrativa', value: 'ADMINISTRATIVA' },
  { label: '🎓 Académica',      value: 'ACADEMICA'      },
  { label: '👁 Supervisión',    value: 'SUPERVISION'    },
  { label: '🚨 Emergencia',     value: 'EMERGENCIA'     },
  { label: '📌 Otra',           value: 'OTRA'           },
];

const loading = ref(false);
const isEdit  = computed(() => !!form.value.idActividad);

interface FormData {
  idActividad?:  number;
  nombre:        string;
  descripcion:   string;
  tipoActividad: TipoActividad;
  activo:        boolean;
}

const EMPTY = (): FormData => ({
  idActividad:   undefined,
  nombre:        '',
  descripcion:   '',
  tipoActividad: 'CLINICA',
  activo:        true,
});

const form = ref<FormData>(EMPTY());

const [Modal, modalApi] = useVbenModal({
  onOpenChange(isOpen: boolean) {
    if (!isOpen) return;
    const data = modalApi.getData<ActividadDto>();
    if (data?.idActividad) {
      form.value = {
        idActividad:   data.idActividad,
        nombre:        data.nombre        ?? '',
        descripcion:   data.descripcion   ?? '',
        tipoActividad: data.tipoActividad ?? 'CLINICA',
        activo:        data.activo,
      };
    } else {
      form.value = EMPTY();
    }
  },
});

async function handleSubmit() {
  if (!form.value.nombre.trim()) { message.warning('El nombre es obligatorio'); return; }
  loading.value = true;
  try {
    const payload: ActividadRequest = {
      nombre:        form.value.nombre.trim(),
      descripcion:   form.value.descripcion || undefined,
      tipoActividad: form.value.tipoActividad,
      activo:        form.value.activo,
    };
    if (isEdit.value) {
      await updateActividad(form.value.idActividad!, payload);
      message.success('Actividad actualizada');
    } else {
      await createActividad(payload);
      message.success('Actividad creada en el catálogo');
    }
    modalApi.close();
    emit('success');
  } catch (e: any) {
    message.error(e?.message ?? 'Error al guardar');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <Modal
    :title="isEdit ? '✏️ Editar Actividad' : '➕ Nueva Actividad'"
    :footer="false"
    style="width: 500px"
  >
    <NForm label-placement="top" :show-require-mark="false">

      <NFormItem label="Nombre *">
        <NInput
          v-model:value="form.nombre"
          placeholder="Ej: Revisión de pacientes, Toma de signos vitales…"
          maxlength="150"
          clearable
        />
      </NFormItem>

      <NFormItem label="Tipo de actividad">
        <NSelect v-model:value="form.tipoActividad" :options="TIPO_OPTIONS" style="width:100%" />
      </NFormItem>

      <NFormItem label="Descripción (opcional)">
        <NInput
          v-model:value="form.descripcion"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 4 }"
          placeholder="Detalle de la actividad, criterios de cumplimiento…"
          maxlength="500"
        />
      </NFormItem>

      <NFormItem label="Estado">
        <NSwitch v-model:value="form.activo">
          <template #checked>Activa en catálogo</template>
          <template #unchecked>Inactiva</template>
        </NSwitch>
      </NFormItem>

      <div class="flex justify-end gap-2 mt-2">
        <NButton @click="modalApi.close()">Cancelar</NButton>
        <NButton type="primary" :loading="loading"
          :style="{ background: '#0f766e', borderColor: '#0f766e' }"
          @click="handleSubmit"
        >
          {{ isEdit ? 'Actualizar' : 'Crear Actividad' }}
        </NButton>
      </div>

    </NForm>
  </Modal>
</template>