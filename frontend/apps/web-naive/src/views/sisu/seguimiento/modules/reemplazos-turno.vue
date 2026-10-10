<script lang="ts" setup>
import { h, ref, watch, reactive } from 'vue';
import {
  NDataTable, NSpin, NEmpty, NButton, NSpace, NTag, NModal, NInput,
  NSelect, NText, NDatePicker,
} from 'naive-ui';
import type { SelectOption } from 'naive-ui';
import type { ReemplazoTurno } from '#/api/sisu/jornada';
import {
  getReemplazosPorJornada, crearReemplazo, aprobarReemplazo, rechazarReemplazo,
  getTitularesDisponibles, getInternosDisponibles,
} from '#/api/sisu/jornada';
import type { TitularSelectDto, InternoSelectDto } from '#/api/sisu/jornada';
import { message } from '#/adapter/naive';

const props = defineProps<{ idJornada: number | null }>();

const reemplazos = ref<ReemplazoTurno[]>([]);
const cargando = ref(false);
const showModal = ref(false);
const creando = ref(false);

const form = reactive({
  fechaReemplazo: null as number | null,
  idOriginal: null as number | null,
  idReemplazo: null as number | null,
  motivo: '',
});

const titulares = ref<TitularSelectDto[]>([]);
const internos = ref<InternoSelectDto[]>([]);

function parseId(val: string | null): { tipo: string; id: number } | null {
  if (!val) return null;
  const [tipo, idStr] = val.split(':');
  return { tipo: tipo!, id: Number(idStr) };
}

async function cargarReemplazos() {
  if (!props.idJornada) { reemplazos.value = []; return; }
  cargando.value = true;
  try {
    reemplazos.value = (await getReemplazosPorJornada(props.idJornada)) ?? [];
  } catch {
    message.error('Error al cargar reemplazos');
  } finally {
    cargando.value = false;
  }
}

async function cargarPersonas() {
  try {
    const [tits, ints] = await Promise.all([
      getTitularesDisponibles(),
      getInternosDisponibles(),
    ]);
    titulares.value = tits ?? [];
    internos.value = ints ?? [];
  } catch {
    // silencioso
  }
}

watch(() => props.idJornada, async (id) => {
  if (!id) { reemplazos.value = []; return; }
  await cargarReemplazos();
  await cargarPersonas();
}, { immediate: true });

function formatearFecha(f: string): string {
  return new Date(f).toLocaleDateString('es-PE', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

async function handleCrear() {
  if (!props.idJornada || !form.fechaReemplazo || !form.idOriginal || !form.idReemplazo || !form.motivo.trim()) {
    message.warning('Completa todos los campos');
    return;
  }
  const originalParsed = parseId(String(form.idOriginal));
  const reemplazoParsed = parseId(String(form.idReemplazo));
  if (!originalParsed || !reemplazoParsed) {
    message.warning('Selecciona personas válidas');
    return;
  }

  const fecha = new Date(form.fechaReemplazo);
  const yyyy = fecha.getFullYear();
  const mm = String(fecha.getMonth() + 1).padStart(2, '0');
  const dd = String(fecha.getDate()).padStart(2, '0');
  const fechaStr = `${yyyy}-${mm}-${dd}`;

  creando.value = true;
  try {
    await crearReemplazo({
      idJornada: props.idJornada,
      fechaReemplazo: fechaStr,
      idOriginal: originalParsed.id,
      idReemplazo: reemplazoParsed.id,
      motivo: form.motivo.trim(),
    });
    message.success('Reemplazo creado');
    showModal.value = false;
    form.fechaReemplazo = null;
    form.idOriginal = null;
    form.idReemplazo = null;
    form.motivo = '';
    await cargarReemplazos();
  } catch (e: any) {
    message.error(e?.message ?? 'Error al crear reemplazo');
  } finally {
    creando.value = false;
  }
}

async function handleAprobar(r: ReemplazoTurno) {
  try {
    await aprobarReemplazo(r.idReemplazo);
    message.success('Reemplazo aprobado');
    await cargarReemplazos();
  } catch (e: any) {
    message.error(e?.message ?? 'Error al aprobar');
  }
}

async function handleRechazar(r: ReemplazoTurno) {
  try {
    await rechazarReemplazo(r.idReemplazo);
    message.success('Reemplazo rechazado');
    await cargarReemplazos();
  } catch (e: any) {
    message.error(e?.message ?? 'Error al rechazar');
  }
}

function buildPersonOpts(): SelectOption[] {
  const opts: SelectOption[] = [];
  for (const t of titulares.value) {
    opts.push({ label: `${t.nombreCompleto} · Titular`, value: `T:${t.idTitular}` });
  }
  for (const i of internos.value) {
    opts.push({ label: `${i.nombreCompleto} · Interno`, value: `I:${i.idInterno}` });
  }
  return opts;
}

const columnas = [
  {
    title: 'Fecha',
    key: 'fechaReemplazo',
    width: 120,
    render: (row: ReemplazoTurno) =>
      h('span', { style: { fontSize: '0.82rem' } }, formatearFecha(row.fechaReemplazo)),
  },
  {
    title: 'Original',
    key: 'nombreOriginal',
    minWidth: 150,
    render: (row: ReemplazoTurno) =>
      h('span', { style: { fontWeight: 600, fontSize: '0.84rem' } }, row.nombreOriginal),
  },
  {
    title: 'Reemplazante',
    key: 'nombreReemplazo',
    minWidth: 150,
    render: (row: ReemplazoTurno) =>
      h('span', { style: { fontWeight: 600, fontSize: '0.84rem' } }, row.nombreReemplazo),
  },
  {
    title: 'Motivo',
    key: 'motivo',
    minWidth: 180,
    render: (row: ReemplazoTurno) =>
      h('span', { style: { fontSize: '0.82rem', color: 'hsl(var(--muted-foreground))' } }, row.motivo),
  },
  {
    title: 'Estado',
    key: 'estado',
    width: 120,
    align: 'center' as const,
    render: (row: ReemplazoTurno) => {
      const type = row.estado === 'APROBADO'
        ? 'success'
        : row.estado === 'RECHAZADO'
          ? 'error'
          : 'warning';
      const label = row.estado === 'APROBADO' ? 'Aprobado'
        : row.estado === 'RECHAZADO' ? 'Rechazado'
        : 'Pendiente';
      return h(NTag, { type, size: 'small', round: true }, () => label);
    },
  },
  {
    title: 'Acciones',
    key: 'acciones',
    width: 160,
    align: 'center' as const,
    render: (row: ReemplazoTurno) => {
      if (row.estado !== 'PENDIENTE') {
        return h('span', { style: { color: 'hsl(var(--muted-foreground))', fontSize: '0.78rem' } },
          row.autorizadoPorNombre ?? '—');
      }
      return h(NSpace, { size: 4, justify: 'center' }, () => [
        h(NButton, {
          size: 'tiny',
          type: 'success',
          ghost: true,
          onClick: () => handleAprobar(row),
        }, () => '✓ Aprobar'),
        h(NButton, {
          size: 'tiny',
          type: 'error',
          ghost: true,
          onClick: () => handleRechazar(row),
        }, () => '✕ Rechazar'),
      ]);
    },
  },
];
</script>

<template>
  <NSpin :show="cargando">
    <div class="mb-3 flex justify-end">
      <NButton
        type="primary"
        :style="{ background: '#0f766e', borderColor: '#0f766e' }"
        @click="showModal = true"
      >
        + Nuevo Reemplazo
      </NButton>
    </div>

    <div v-if="!cargando && reemplazos.length === 0" class="py-10">
      <NEmpty description="No hay reemplazos registrados para esta jornada" />
    </div>

    <NDataTable
      v-else
      :columns="columnas"
      :data="reemplazos"
      :bordered="false"
      :striped="true"
      size="small"
      :scroll-x="850"
      :pagination="{ pageSize: 15, showSizePicker: true, pageSizes: [10, 15, 30] }"
    />

    <NModal
      v-model:show="showModal"
      preset="card"
      title="Nuevo Reemplazo de Turno"
      style="width: 520px;"
      :mask-closable="!creando"
    >
      <div class="space-y-4">
        <div class="flex flex-col gap-1">
          <NText strong class="text-xs">Fecha del reemplazo</NText>
          <NDatePicker
            v-model:value="form.fechaReemplazo"
            type="date"
            value-format="yyyy-MM-dd"
            style="width: 100%;"
          />
        </div>
        <div class="flex flex-col gap-1">
          <NText strong class="text-xs">Persona original</NText>
          <NSelect
            v-model:value="form.idOriginal"
            :options="buildPersonOpts()"
            placeholder="Persona que será reemplazada"
            filterable
          />
        </div>
        <div class="flex flex-col gap-1">
          <NText strong class="text-xs">Reemplazante</NText>
          <NSelect
            v-model:value="form.idReemplazo"
            :options="buildPersonOpts()"
            placeholder="Persona que reemplazará"
            filterable
          />
        </div>
        <div class="flex flex-col gap-1">
          <NText strong class="text-xs">Motivo</NText>
          <NInput
            v-model:value="form.motivo"
            type="textarea"
            placeholder="Describe el motivo del reemplazo"
            :rows="3"
          />
        </div>
      </div>
      <template #footer>
        <NSpace justify="end">
          <NButton :disabled="creando" @click="showModal = false">Cancelar</NButton>
          <NButton
            type="primary"
            :loading="creando"
            :disabled="creando"
            @click="handleCrear"
          >
            Crear Reemplazo
          </NButton>
        </NSpace>
      </template>
    </NModal>
  </NSpin>
</template>
