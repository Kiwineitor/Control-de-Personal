<script lang="ts" setup>
import { ref, reactive, computed, watch } from 'vue';
import { NModal, NButton, NSpin, NRadioGroup, NRadioButton, NTag } from 'naive-ui';
import { message } from '#/adapter/naive';
import {
  getCatalogoVistas, getResumenUsuarioVistas, setOverridesUsuario, type VistaDto,
} from '#/api/admin/vistas';

type Estado = 'heredar' | 'grant' | 'deny';

const props = defineProps<{
  show: boolean;
  usuario: { idUsuario: number; nombre: string; apellido: string; username: string } | null;
}>();
const emit = defineEmits<{ 'update:show': [boolean]; saved: [] }>();

const catalogo  = ref<VistaDto[]>([]);
const baseRol   = ref<string[]>([]);
const estados   = reactive<Record<string, Estado>>({});
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

// Vista previa de lo que el usuario verá realmente (misma lógica que el backend).
const efectivas = computed(() => {
  const set = new Set<string>();
  for (const v of catalogo.value) {
    const st = estados[v.codigo] ?? 'heredar';
    const on = st === 'grant' ? true : st === 'deny' ? false : baseRol.value.includes(v.codigo);
    if (on) set.add(v.codigo);
  }
  set.add('INICIO');
  return catalogo.value.filter((v) => set.has(v.codigo)).map((v) => v.nombre);
});

async function cargar() {
  if (!props.usuario) return;
  cargando.value = true;
  try {
    const [cat, resumen] = await Promise.all([
      getCatalogoVistas(),
      getResumenUsuarioVistas(props.usuario.idUsuario),
    ]);
    catalogo.value = cat ?? [];
    baseRol.value  = resumen?.baseRol ?? [];
    for (const k of Object.keys(estados)) delete estados[k];
    for (const v of catalogo.value) estados[v.codigo] = 'heredar';
    for (const o of resumen?.overrides ?? []) {
      estados[o.codigo] = o.tipo === 'GRANT' ? 'grant' : 'deny';
    }
  } catch {
    message.error('No se pudieron cargar los permisos.');
  } finally {
    cargando.value = false;
  }
}

watch(() => props.show, (v) => { if (v) cargar(); });

async function guardar() {
  if (!props.usuario) return;
  guardando.value = true;
  try {
    const overrides = catalogo.value
      .filter((v) => (estados[v.codigo] ?? 'heredar') !== 'heredar')
      .map((v) => ({ codigo: v.codigo, tipo: estados[v.codigo] === 'grant' ? 'GRANT' : 'DENY' }));
    await setOverridesUsuario(props.usuario.idUsuario, overrides);
    message.success('Permisos del usuario actualizados.');
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
    :title="`🧩 Permisos: ${usuario?.apellido ?? ''}, ${usuario?.nombre ?? ''}`"
    style="width: 640px; max-width: 96vw"
    @update:show="(v) => emit('update:show', v)"
  >
    <NSpin :show="cargando">
      <p class="mb-3 text-sm text-gray-500">
        Por defecto cada vista se <b>hereda</b> del rol. Puedes <b>permitir</b> una vista extra
        o <b>bloquear</b> una que el rol sí da.
      </p>

      <div v-for="[grupo, vistas] in grupos" :key="grupo" class="mb-3">
        <div class="mb-1 text-xs font-semibold uppercase text-gray-400">{{ grupo }}</div>
        <div
          v-for="v in vistas"
          :key="v.codigo"
          class="flex items-center justify-between gap-2 py-1 pl-1"
        >
          <div class="min-w-0">
            <span class="text-sm">{{ v.nombre }}</span>
            <NTag
              v-if="baseRol.includes(v.codigo)"
              size="tiny" type="success" :bordered="false" class="ml-2"
            >rol: sí</NTag>
            <NTag v-else size="tiny" :bordered="false" class="ml-2">rol: no</NTag>
          </div>
          <NRadioGroup v-model:value="estados[v.codigo]" size="small">
            <NRadioButton value="heredar">Heredar</NRadioButton>
            <NRadioButton value="grant">Permitir</NRadioButton>
            <NRadioButton value="deny">Bloquear</NRadioButton>
          </NRadioGroup>
        </div>
      </div>

      <div class="mt-3 rounded-lg border bg-gray-50 p-3">
        <div class="mb-1 text-xs font-semibold uppercase text-gray-400">Verá en el menú</div>
        <div class="flex flex-wrap gap-1">
          <NTag v-for="n in efectivas" :key="n" size="small" type="info" :bordered="false">{{ n }}</NTag>
        </div>
      </div>
    </NSpin>

    <template #footer>
      <div class="flex justify-end gap-2">
        <NButton @click="emit('update:show', false)">Cancelar</NButton>
        <NButton type="primary" :loading="guardando" @click="guardar">Guardar</NButton>
      </div>
    </template>
  </NModal>
</template>
