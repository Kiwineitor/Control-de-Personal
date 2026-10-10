<script lang="ts" setup>
import type { HorarioIndividual, TitularSelectDto } from '#/api/sisu/jornada';

import { computed, onMounted, ref, watch } from 'vue';

import {
  NAvatar, NButton, NCheckbox, NEmpty, NInput, NSpin,
  NSwitch, NTag, NTimePicker,
} from 'naive-ui';

import { message } from '#/adapter/naive';
import { asignarTitulares, getTitularesDisponibles } from '#/api/sisu/jornada';

const props = defineProps<{
  horaFinJornada: null | string;
  horaFinTardeJornada?: null | string;
  horaInicioJornada: null | string;
  horaInicioTardeJornada?: null | string;
  idJornada: null | number;
  /** PARTIDA (administrativa) → el titular tiene dos franjas configurables. */
  modalidad?: null | string;
}>();
const emit = defineEmits<{ cancel: []; success: []; }>();

/**
 * Una jornada partida abre DOS ventanas de trabajo. Sin esto el formulario solo
 * ofrecía un tramo y la tarde quedaba sin configurar: el titular constaba fuera
 * de turno toda la tarde, sin vigilancia GPS.
 */
const esPartida = computed(() =>
  props.modalidad === 'PARTIDA'
  && !!props.horaInicioTardeJornada
  && !!props.horaFinTardeJornada,
);

const loading = ref(false);
const loadingLista = ref(false);
const titulares = ref<TitularSelectDto[]>([]);
const seleccionados = ref<number[]>([]);
const busqueda = ref('');
const observaciones = ref('');

interface HorarioFila {
  usarGeneral:  boolean;
  inicio:       null | string;
  fin:          null | string;
  inicioTarde:  null | string;
  finTarde:     null | string;
  /** En jornada partida: qué franjas cubre esta persona. */
  haceManana:   boolean;
  haceTarde:    boolean;
}

const horariosIndividuales = ref<Record<number, HorarioFila>>({});

function filaNueva(): HorarioFila {
  return {
    usarGeneral: true,
    inicio: null, fin: null,
    inicioTarde: null, finTarde: null,
    haceManana: true, haceTarde: true,
  };
}

const filtrados = computed(() =>
  busqueda.value
    ? titulares.value.filter(t =>
        t.nombreCompleto.toLowerCase().includes(busqueda.value.toLowerCase()) ||
        (t.cargo ?? '').toLowerCase().includes(busqueda.value.toLowerCase()),
      )
    : titulares.value,
);

const todosSeleccionados = computed(() =>
  filtrados.value.length > 0 &&
  filtrados.value.every(t => seleccionados.value.includes(t.idTitular)),
);

function toggleTodos() {
  seleccionados.value = todosSeleccionados.value ? [] : filtrados.value.map(t => t.idTitular);
}

function toggleItem(id: number) {
  const idx = seleccionados.value.indexOf(id);
  if (idx === -1) {seleccionados.value.push(id);}
  else {seleccionados.value.splice(idx, 1);}
}

watch(seleccionados, (nuevos) => {
  for (const id of nuevos) {
    horariosIndividuales.value[id] ??= filaNueva();
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

/** Ninguna franja marcada = el titular nunca estaría en turno. */
function filaInvalida(h: HorarioFila | undefined): boolean {
  return !!h && !h.usarGeneral && esPartida.value && !h.haceManana && !h.haceTarde;
}

const haySinFranja = computed(() =>
  seleccionados.value.some(id => filaInvalida(horariosIndividuales.value[id])),
);

/**
 * Filas ya resueltas para el template: evita indexar el diccionario dentro del
 * v-for, que obliga a comprobar undefined en cada acceso.
 */
const filasHorario = computed(() =>
  seleccionados.value
    .map(id => ({ id, nombre: nombreDe(id), h: horariosIndividuales.value[id] }))
    .filter((f): f is { h: HorarioFila; id: number; nombre: string } => f.h !== undefined),
);

onMounted(async () => {
  if (!props.idJornada) return;
  loadingLista.value = true;
  try {
    titulares.value = await getTitularesDisponibles();
  } finally {
    loadingLista.value = false;
  }
});

async function handleSubmit() {
  if (!props.idJornada) return;
  if (seleccionados.value.length === 0) {
    message.warning('Selecciona al menos un titular');
    return;
  }
  if (haySinFranja.value) {
    message.warning('Cada titular con horario propio debe cubrir al menos una franja');
    return;
  }
  loading.value = true;
  try {
    const overrides: Record<string, HorarioIndividual> = {};
    for (const id of seleccionados.value) {
      const h = horariosIndividuales.value[id];
      if (!h || h.usarGeneral) continue;

      // Se manda solo lo que el usuario completó: las horas vacías significan
      // "hereda de la jornada", no "sin horario".
      const payload: HorarioIndividual = {
        horaInicio: h.inicio ?? props.horaInicioJornada ?? '',
        horaFin:    h.fin    ?? props.horaFinJornada    ?? '',
      };
      if (esPartida.value) {
        payload.horaInicioTarde = h.inicioTarde ?? props.horaInicioTardeJornada ?? null;
        payload.horaFinTarde    = h.finTarde    ?? props.horaFinTardeJornada    ?? null;
        payload.trabajaManana   = h.haceManana;
        payload.trabajaTarde    = h.haceTarde;
      }
      overrides[String(id)] = payload;
    }
    await asignarTitulares(props.idJornada, {
      idTitulares: seleccionados.value,
      observaciones: observaciones.value || undefined,
      horariosIndividuales: Object.keys(overrides).length > 0 ? overrides : undefined,
    });
    message.success(`${seleccionados.value.length} titular(es) asignado(s)`);
    emit('success');
  } catch {
    message.error('Error al asignar titulares');
  } finally {
    loading.value = false;
  }
}

function formatHoraCorta(h: null | string | undefined): string {
  return h ? h.slice(0, 5) : '--:--';
}

function nombreDe(id: number): string {
  return titulares.value.find(t => t.idTitular === id)?.nombreCompleto ?? '';
}
</script>

<template>
  <div class="space-y-4 px-1">
<div
      class="rounded-lg px-3 py-2 text-xs"
      style="background: hsl(var(--muted)); border: 1px solid hsl(var(--border)); color: hsl(var(--muted-foreground))"
    >
      <span class="font-semibold">
        Horario general de la jornada<template v-if="esPartida"> (partida)</template>:
      </span>
      {{ formatHoraCorta(horaInicioJornada) }} – {{ formatHoraCorta(horaFinJornada) }}
      <span class="ml-2" style="opacity:.7">· Puedes asignar un horario distinto por persona.</span>
    </div>

    <NInput
      v-model:value="busqueda"
      placeholder="Buscar por nombre o cargo…"
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
          v-for="t in filtrados"
          :key="t.idTitular"
          class="flex items-center gap-3 px-3 py-2 cursor-pointer border-b last:border-0 transition-colors"
          style="border-color: hsl(var(--border))"
          :style="seleccionados.includes(t.idTitular)
            ? { background: 'rgba(230,57,70,.08)' }
            : {}"
          @click="toggleItem(t.idTitular)"
        >
          <NCheckbox
            :checked="seleccionados.includes(t.idTitular)"
            @update:checked="() => toggleItem(t.idTitular)"
            @click.stop
          />
          <NAvatar
            :size="30"
            round
            :style="{ background: '#e63946', fontSize: '0.75rem', fontWeight: 700, flexShrink: 0 }"
          >
            {{ t.nombreCompleto.charAt(0).toUpperCase() }}
          </NAvatar>
          <div class="flex-1 min-w-0">
            <div class="text-sm font-semibold truncate">{{ t.nombreCompleto }}</div>
            <div class="text-xs" style="color: hsl(var(--muted-foreground))">
              {{ t.cargo ?? 'Sin cargo' }}
            </div>
          </div>
        </div>

        <NEmpty
          v-if="filtrados.length === 0 && !loadingLista"
          size="small"
          description="Sin titulares disponibles"
          class="py-4"
        />
      </div>
    </NSpin>

    <div v-if="seleccionados.length > 0" style="color:#3b82f6; font-size:.78rem; font-weight:600">
      ✓ {{ seleccionados.length }} titular(es) seleccionado(s)
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
          v-for="fila in filasHorario"
          :key="`hor-${ fila.id}`"
          class="rounded-lg px-3 py-2.5"
          style="background: hsl(var(--muted)); border: 1px solid hsl(var(--border))"
        >
          <div class="flex items-center justify-between mb-2">
            <span class="text-xs font-semibold" style="color: hsl(var(--foreground))">
              {{ fila.nombre }}
            </span>
            <NSwitch
              v-model:value="fila.h.usarGeneral"
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
            v-if="!fila.h?.usarGeneral"
            class="space-y-2"
          >
            <!-- ── Franja de la mañana (o única, si la jornada es continua) ── -->
            <div class="flex gap-3 items-center">
              <NCheckbox
                v-if="esPartida"
                :checked="fila.h.haceManana"
                @update:checked="(v: boolean) => (fila.h.haceManana = v)"
              >
                <span class="text-xs font-semibold">Mañana</span>
              </NCheckbox>
              <template v-if="!esPartida || fila.h.haceManana">
                <div class="flex-1">
                  <div class="text-xs mb-1" style="color: hsl(var(--muted-foreground))">Entrada</div>
                  <NTimePicker
                    v-model:formatted-value="fila.h.inicio"
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
                    v-model:formatted-value="fila.h.fin"
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
              </template>
              <span v-else class="text-xs" style="color: hsl(var(--muted-foreground))">
                No cubre la mañana
              </span>
            </div>

            <!-- ── Franja de la tarde: solo en jornada partida ─────────────── -->
            <div v-if="esPartida" class="flex gap-3 items-center">
              <NCheckbox
                :checked="fila.h.haceTarde"
                @update:checked="(v: boolean) => (fila.h.haceTarde = v)"
              >
                <span class="text-xs font-semibold">Tarde</span>
              </NCheckbox>
              <template v-if="fila.h.haceTarde">
                <div class="flex-1">
                  <div class="text-xs mb-1" style="color: hsl(var(--muted-foreground))">Entrada</div>
                  <NTimePicker
                    v-model:formatted-value="fila.h.inicioTarde"
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
                    v-model:formatted-value="fila.h.finTarde"
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
              </template>
              <span v-else class="text-xs" style="color: hsl(var(--muted-foreground))">
                No cubre la tarde
              </span>
            </div>

            <div v-if="filaInvalida(fila.h)" class="text-xs" style="color: #e03131">
              ⚠ Debe cubrir al menos una franja, si no nunca estará en turno.
            </div>
          </div>

          <div
            v-else
            class="text-xs"
            style="color: hsl(var(--muted-foreground))"
          >
            Usará {{ formatHoraCorta(horaInicioJornada) }} – {{ formatHoraCorta(horaFinJornada) }}
            <template v-if="esPartida">
              y {{ formatHoraCorta(horaInicioTardeJornada) }} – {{ formatHoraCorta(horaFinTardeJornada) }}
            </template>
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
        :style="{ background: '#e63946', borderColor: '#e63946' }"
        @click="handleSubmit"
      >
        Asignar{{ seleccionados.length > 0 ? ` (${seleccionados.length})` : '' }}
      </NButton>
    </div>
</div>
</template>
