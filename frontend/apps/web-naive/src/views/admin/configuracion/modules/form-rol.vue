<script lang="ts" setup>
import { reactive, ref, watch } from 'vue';
import { NButton, NForm, NFormItem, NInput, NModal } from 'naive-ui';
import { message }               from '#/adapter/naive';
import { crearRolApi, actualizarRolApi, type RolResponse } from '#/api/admin/configuracion';

const props = defineProps<{ show: boolean; rol: RolResponse | null }>();
const emit  = defineEmits<{
  (e: 'update:show', val: boolean): void;
  (e: 'saved'): void;
}>();

const loading   = ref(false);
const esEdicion = ref(false);
const form      = reactive({ nombre: '' });

watch(() => props.show, (val) => {
  if (!val) return;
  esEdicion.value = !!props.rol;
  form.nombre     = props.rol?.nombre ?? '';
});

async function guardar() {
  if (!form.nombre.trim()) {
    message.warning('El nombre del rol es obligatorio');
    return;
  }
  loading.value = true;
  try {
    if (esEdicion.value && props.rol) {
      await actualizarRolApi(props.rol.idRol, { nombre: form.nombre.trim() });
      message.success('Rol actualizado');
    } else {
      await crearRolApi({ nombre: form.nombre.trim() });
      message.success('Rol creado');
    }
    emit('saved');
    emit('update:show', false);
  } catch (err: any) {
    message.error(err?.response?.data?.message ?? 'Error al guardar el rol');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <NModal :show="show" preset="card"
    :title="esEdicion ? '✏️ Editar Rol' : '➕ Nuevo Rol'"
    style="width: 420px" :mask-closable="false"
    @update:show="$emit('update:show', $event)">

    <NForm label-placement="top">
      <NFormItem label="Nombre del rol *">
        <NInput v-model:value="form.nombre"
          placeholder="Ej: SUPERVISOR"
          maxlength="100"
          :input-props="{ style: 'text-transform: uppercase' }"
          @input="form.nombre = form.nombre.toUpperCase()" />
      </NFormItem>
      <p class="text-xs text-gray-400 mt-1">
        Solo letras mayúsculas y guión bajo. Ej: SUPER_ADMIN
      </p>
    </NForm>

    <div class="mt-4 flex justify-end gap-2">
      <NButton @click="$emit('update:show', false)">Cancelar</NButton>
      <NButton type="primary" :loading="loading" @click="guardar">
        {{ esEdicion ? 'Actualizar' : 'Crear rol' }}
      </NButton>
    </div>
  </NModal>
</template>