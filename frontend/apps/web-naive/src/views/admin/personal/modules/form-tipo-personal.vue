<script setup lang="ts">
import { ref, watch } from 'vue';
import {
  NButton, NForm, NFormItem, NInput, NSwitch, NSpace, NColorPicker,
} from 'naive-ui';
import { message } from '#/adapter/naive';
import type { TipoPersonalDto, TipoPersonalRequest } from '#/api/admin/personal';
import { crearTipoPersonal, actualizarTipoPersonal } from '#/api/admin/personal';

const props  = defineProps<{ tipo: TipoPersonalDto | null }>();
const emit   = defineEmits<{ (e: 'saved'): void; (e: 'cancel'): void }>();
const saving = ref(false);

const form = ref<TipoPersonalRequest>({
  nombre: '', descripcion: '', color: '#64748b',
  esPersonalMedico: false, puedeGestionarInternos: false, activo: true,
});

watch(() => props.tipo, (t) => {
  if (t) {
    form.value = {
      nombre: t.nombre, descripcion: t.descripcion ?? '',
      color: t.color ?? '#64748b',
      esPersonalMedico: t.esPersonalMedico,
      puedeGestionarInternos: t.puedeGestionarInternos,
      activo: t.activo,
    };
  } else {
    form.value = {
      nombre: '', descripcion: '', color: '#64748b',
      esPersonalMedico: false, puedeGestionarInternos: false, activo: true,
    };
  }
}, { immediate: true });

async function guardar() {
  if (!form.value.nombre.trim()) { message.warning('El nombre es obligatorio'); return; }
  saving.value = true;
  try {
    if (props.tipo) {
      await actualizarTipoPersonal(props.tipo.idTipoPersonal, form.value);
      message.success('Tipo actualizado');
    } else {
      await crearTipoPersonal(form.value);
      message.success('Tipo creado');
    }
    emit('saved');
  } catch (e: any) {
    message.error(e?.message ?? 'Error al guardar');
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <NForm label-placement="left" label-width="180px">
    <NFormItem label="Nombre del tipo" required>
      <NInput v-model:value="form.nombre" placeholder="MEDICO, ENFERMERO, CAJERO..."
              style="text-transform:uppercase" />
    </NFormItem>
    <NFormItem label="Descripción">
      <NInput v-model:value="(form as any).descripcion" type="textarea" :rows="2" />
    </NFormItem>
    <NFormItem label="Color en UI">
      <NColorPicker v-model:value="form.color" :modes="['hex']" style="width:200px" />
    </NFormItem>
    <NFormItem label="Es personal médico/asistencial">
      <NSwitch v-model:value="form.esPersonalMedico" />
      <span style="font-size:12px;color:#888;margin-left:10px">
        Accede a: Turnos, Actividades, Permisos, Alertas
      </span>
    </NFormItem>
    <NFormItem label="Puede gestionar internos">
      <NSwitch v-model:value="form.puedeGestionarInternos" />
      <span style="font-size:12px;color:#888;margin-left:10px">Solo médicos responsables</span>
    </NFormItem>
    <NFormItem label="Activo">
      <NSwitch v-model:value="form.activo" />
    </NFormItem>
    <NSpace justify="end" style="margin-top:8px">
      <NButton @click="emit('cancel')">Cancelar</NButton>
      <NButton type="primary" :loading="saving" @click="guardar">
        {{ tipo ? 'Actualizar' : 'Crear' }}
      </NButton>
    </NSpace>
  </NForm>
</template>
