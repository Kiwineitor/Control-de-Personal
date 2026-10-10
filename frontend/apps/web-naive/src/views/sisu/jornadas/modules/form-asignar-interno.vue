<script lang="ts" setup>
import type { InternoSelectDto } from '#/api/sisu/jornada';
import { ref, computed, onMounted, watch } from 'vue';
import { message } from '#/adapter/naive';
import {
  NInput, NCheckbox, NButton, NSpin, NEmpty, NAvatar, NTag, NAlert,
  NTimePicker, NSwitch,
} from 'naive-ui';
import { getInternosDisponibles, asignarInternos } from '#/api/sisu/jornada';

const props = defineProps<{
  idJornada: number | null;
  horaInicioJornada: string | null;
  horaFinJornada: string | null;
}>();
const emit = defineEmits<{ success: []; cancel: [] }>();

const loading = ref(false);
const loadingLista = ref(false);
const internos = ref<InternoSelectDto[]>([]);
const seleccionados = ref<number[]>([]);
const busqueda = ref('');
const observaciones = ref('');

const horariosIndividuales = ref<Record<number, { usarGeneral: boolean; inicio: string | null; fin: string | null }>>({});

const filtrados = computed(() =>
  !busqueda.value
    ? internos.value
    : internos.value.filter(i =>
        i.nombreCompleto.toLowerCase().includes(busqueda.value.toLowerCase()) ||
        (i.carrera ?? '').toLowerCase().includes(busqueda.value.toLowerCase()),
      ),
);

const todosSeleccionados = computed(() =>
  filtrados.value.length > 0 &&
  filtrados.value.every(i => seleccionados.value.includes(i.idInterno)),
);

function toggleTodos() {
  if (todosSeleccionados.value) seleccionados.value = [];
  else seleccionados.value = filtrados.value.map(i => i.idInterno);
}

function toggleItem(id: number) {
  const idx = seleccionados.value.indexOf(id);
  if (idx >= 0) seleccionados.value.splice(idx, 1);
  else seleccionados.value.push(id);
}

watch(seleccionados, (nuevos) => {
  for (const id of nuevos) {
    if (!horariosIndividuales.value[id]) {
      horariosIndividuales.value[id] = { usarGeneral: true, inicio: null, fin: null };
    }
  }
  for (const id of Object.keys(horariosIndividuales.value).map(Number)) {
    if (!nuevos.includes(id)) delete horariosIndividuales.value[id];
  }
}, { deep: true });

const hayOverrides = computed(() =>
  seleccionados.value.some(id => {
    const h = horariosIndividuales.value[id];
    return h && !h.usarGeneral;
  }),
);

onMounted(async () => {
  if (!props.idJornada) return;
  loadingLista.value = true;
  try {
    internos.value = await getInternosDisponibles();
  } finally {
    loadingLista.value = false;
  }
});

async function handleSubmit() {
  if (!props.idJornada) return;
  if (seleccionados.value.length === 0) {
    message.warning('Selecciona al menos un interno');
    return;
  }
  loading.value = true;
  try {
      const overrides: Record<string, { horaInicio: string; horaFin: string }> = {};
    for (const id of seleccionados.value) {
      const h = horariosIndividuales.value[id];
      if (h && !h.usarGeneral && h.inicio && h.fin) {
        overrides[String(id)] = { horaInicio: h.inicio, horaFin: h.fin };
      }
    }
    await asignarInternos(props.idJornada, {
      idInternos: seleccionados.value,
      observaciones: observaciones.value || undefined,
      horariosIndividuales: Object.keys(overrides).length > 0 ? overrides : undefined,
    });
    message.success(`${seleccionados.value.length} interno(s) asignado(s)`);
    emit('success');
  } catch {
    message.error('Error al asignar internos');
  } finally {
    loading.value = false;
  }
}

function formatHoraCorta(h: string | null | undefined): string {
  return h ? h.slice(0, 5) : '--:--';
}

function nombreDe(id: number): string {
  return internos.value.find(i => i.idInterno === id)?.nombreCompleto ?? '';
}
</script>

<template>
  <div class="space-y-4 px-1">

    <div
      class="rounded-lg px-3 py-2 text-xs"
      style="background: hsl(var(--muted)); border: 1px solid hsl(var(--border)); color: hsl(var(--muted-foreground))"
    >
      <span class="font-semibold">Horario general de la jornada:</span>
      {{ formatHoraCorta(horaInicioJornada) }} – {{ formatHoraCorta(horaFinJornada) }}
      <span class="ml-2" style="opacity:.7">· Puedes asignar un horario distinto por persona.</span>
    </div>

    <NAlert type="success" :show-icon="true" style="font-size:.81rem">
      El responsable activo de cada interno se registrará como snapshot automáticamente.
    </NAlert>

    <NInput
      v-model:value="busqueda"
      placeholder="Buscar por nombre o carrera…"
      clearable
    />

    <NSpin :show="loadingLista">
      <div
        class="border rounded-lg overflow-y-auto"
        style="max-height: 260px; border-color: hsl(var(--border))"
      >
        <div
          class="flex items-center gap-2 px-3 py-2 border-b sticky top-0"
          style="background: hsl(var(--card)); border-color: hsl(var(--border))"
        >
          <NCheckbox
            :checked="todosSeleccionados"
            :indeterminate="seleccionados.length > 0 && !todosSeleccionados"
            @update:checked="toggleTodos"
          >
            <span class="text-sm font-medium" style="color: hsl(var(--muted-foreground))">
              Seleccionar todos ({{ filtrados.length }})
            </span>
          </NCheckbox>
        </div>

        <div
          v-for="i in filtrados"
          :key="i.idInterno"
          class="flex items-center gap-3 px-3 py-2 cursor-pointer border-b last:border-0 transition-colors"
          style="border-color: hsl(var(--border))"
          :style="seleccionados.includes(i.idInterno)
            ? { background: 'rgba(29,78,216,.08)' }
            : {}"
          @click="toggleItem(i.idInterno)"
        >
          <NCheckbox
            :checked="seleccionados.includes(i.idInterno)"
            @update:checked="() => toggleItem(i.idInterno)"
            @click.stop
          />
          <NAvatar
            :size="30"
            round
            :style="{ background: '#1d4ed8', fontSize: '0.75rem', fontWeight: 700, flexShrink: 0 }"
          >
            {{ i.nombreCompleto.charAt(0).toUpperCase() }}
          </NAvatar>
          <div class="flex-1 min-w-0">
            <div class="text-sm font-semibold truncate">{{ i.nombreCompleto }}</div>
            <div class="text-xs" style="color: hsl(var(--muted-foreground))">
              {{ i.carrera ?? '—' }}
              <span v-if="i.universidad"> · {{ i.universidad }}</span>
            </div>
          </div>
          <NTag :type="i.estaEnRotacion ? 'info' : 'warning'" size="tiny" round>
            {{ i.estaEnRotacion ? '↻ Rotación' : 'Fuera' }}
          </NTag>
        </div>

        <NEmpty
          v-if="filtrados.length === 0 && !loadingLista"
          size="small"
          description="Sin internos disponibles"
          class="py-4"
        />
      </div>
    </NSpin>

    <div v-if="seleccionados.length > 0" style="color:#3b82f6; font-size:.78rem; font-weight:600">
      ✓ {{ seleccionados.length }} interno(s) seleccionado(s)
    </div>

    <!-- ══ HORARIOS INDIVIDUALES ════════════════════════════════════════════ -->
    <div
      v-if="seleccionados.length > 0"
      class="rounded-xl p-4 space-y-3"
      style="border: 1px solid hsl(var(--border)); background: hsl(var(--card))"
    >
      <div class="flex items-center justify-between">
        <div>
          <div class="text-sm font-bold" style="color: hsl(var(--foreground))">
            ⏰ Horarios por persona
          </div>
          <div class="text-xs mt-0.5" style="color: hsl(var(--muted-foreground))">
            Asigna horarios individuales o usa el horario general de la jornada
          </div>
        </div>
        <NTag v-if="hayOverrides" type="success" size="small" round>
          {{ seleccionados.filter(id => !horariosIndividuales[id]?.usarGeneral).length }} con override
        </NTag>
      </div>

      <div class="space-y-2">
        <div
          v-for="id in seleccionados"
          :key="'hor-' + id"
          class="rounded-lg px-3 py-2.5"
          style="background: hsl(var(--muted)); border: 1px solid hsl(var(--border))"
        >
          <div class="flex items-center justify-between mb-2">
            <span class="text-xs font-semibold" style="color: hsl(var(--foreground))">
              {{ nombreDe(id) }}
            </span>
            <NSwitch
              v-model:value="horariosIndividuales[id].usarGeneral"
              size="small"
            >
              <template #checked>
                <span style="font-size:.7rem">General</span>
              </template>
              <template #unchecked>
                <span style="font-size:.7rem">Específico</span>
              </template>
            </NSwitch>
          </div>

          <div
            v-if="!horariosIndividuales[id]?.usarGeneral"
            class="flex gap-3 items-center"
          >
            <div class="flex-1">
              <div class="text-xs mb-1" style="color: hsl(var(--muted-foreground))">Entrada</div>
              <NTimePicker
                v-model:formatted-value="horariosIndividuales[id].inicio"
                format="HH:mm:ss"
                value-format="HH:mm:ss"
                :hour-step="1"
                :minute-step="15"
                :second-step="0"
                style="width: 100%"
                size="small"
                placeholder="Hora entrada"
              />
            </div>
            <div class="flex-1">
              <div class="text-xs mb-1" style="color: hsl(var(--muted-foreground))">Salida</div>
              <NTimePicker
                v-model:formatted-value="horariosIndividuales[id].fin"
                format="HH:mm:ss"
                value-format="HH:mm:ss"
                :hour-step="1"
                :minute-step="15"
                :second-step="0"
                style="width: 100%"
                size="small"
                placeholder="Hora salida"
              />
            </div>
          </div>

          <div
            v-else
            class="text-xs"
            style="color: hsl(var(--muted-foreground))"
          >
            Usará {{ formatHoraCorta(horaInicioJornada) }} – {{ formatHoraCorta(horaFinJornada) }}
          </div>
        </div>
      </div>
    </div>

    <div>
      <label class="block text-sm font-semibold mb-1" style="color: hsl(var(--foreground))">
        Observación <span class="font-normal" style="color: hsl(var(--muted-foreground))">(opcional)</span>
      </label>
      <NInput
        v-model:value="observaciones"
        type="textarea"
        :autosize="{ minRows: 2, maxRows: 3 }"
        placeholder="Notas sobre esta asignación…"
      />
    </div>

    <div class="flex justify-end gap-2 pt-1">
      <NButton @click="emit('cancel')">Cancelar</NButton>
      <NButton
        type="primary"
        :loading="loading"
        :disabled="seleccionados.length === 0"
        :style="{ background: '#1d4ed8', borderColor: '#1d4ed8' }"
        @click="handleSubmit"
      >
        Asignar{{ seleccionados.length > 0 ? ` (${seleccionados.length})` : '' }}
      </NButton>
    </div>

  </div>
</template>
