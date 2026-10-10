<script lang="ts" setup>
import { ref, computed, watch } from 'vue';
import { NModal, NButton, NCheckbox, NCheckboxGroup, NSpin } from 'naive-ui';
import { message } from '#/adapter/naive';
import {
  getCatalogoVistas, getVistasDeRol, setVistasDeRol, type VistaDto,
} from '#/api/admin/vistas';

const props = defineProps<{
  show: boolean;
  rol:  { idRol: number; nombre: string } | null;
}>();
const emit = defineEmits<{ 'update:show': [boolean]; saved: [] }>();

const catalogo  = ref<VistaDto[]>([]);
const seleccion = ref<string[]>([]);
const cargando  = ref(false);
const guardando = ref(false);

const grupos = computed(() => {
  const m = new Map<string, VistaDto[]>();
  for (const v of catalogo.value) {
    const g = v.grupo ?? 'Otros';
    if (!m.has(g)) m.set(g, []);
    m.get(g)!.push(v);
  }
  return [...m.entries()];
});

async function cargar() {
  if (!props.rol) return;
  cargando.value = true;
  try {
    const [cat, actuales] = await Promise.all([
      getCatalogoVistas(),
      getVistasDeRol(props.rol.idRol),
    ]);
    catalogo.value  = cat ?? [];
    seleccion.value = actuales ?? [];
  } catch {
    message.error('No se pudieron cargar las vistas.');
  } finally {
    cargando.value = false;
  }
}

watch(() => props.show, (v) => { if (v) cargar(); });

async function guardar() {
  if (!props.rol) return;
  guardando.value = true;
  try {
    await setVistasDeRol(props.rol.idRol, seleccion.value);
    message.success('Vistas del rol actualizadas.');
    emit('saved');
    emit('update:show', false);
  } catch (e: any) {
    message.error(e?.message ?? 'No se pudo guardar.');
  } finally {
    guardando.value = false;
  }
}
</script>

<template>
  <NModal
    :show="show"
    preset="card"
    :title="`🧩 Vistas del rol: ${rol?.nombre ?? ''}`"
    style="width: 520px; max-width: 94vw"
    @update:show="(v) => emit('update:show', v)"
  >
    <NSpin :show="cargando">
      <p class="mb-3 text-sm text-gray-500">
        Marca las opciones de menú que este rol tendrá por defecto. Cada usuario podrá tener
        ajustes puntuales (permitir/bloquear) sobre esta base.
      </p>
      <NCheckboxGroup v-model:value="seleccion">
        <div v-for="[grupo, vistas] in grupos" :key="grupo" class="mb-3">
          <div class="mb-1 text-xs font-semibold uppercase text-gray-400">{{ grupo }}</div>
          <div class="flex flex-col gap-1 pl-1">
            <NCheckbox v-for="v in vistas" :key="v.codigo" :value="v.codigo">
              {{ v.nombre }}
              <span class="ml-1 text-xs text-gray-400">({{ v.codigo }})</span>
            </NCheckbox>
          </div>
        </div>
      </NCheckboxGroup>
    </NSpin>

    <template #footer>
      <div class="flex justify-end gap-2">
        <NButton @click="emit('update:show', false)">Cancelar</NButton>
        <NButton type="primary" :loading="guardando" @click="guardar">Guardar</NButton>
      </div>
    </template>
  </NModal>
</template>
