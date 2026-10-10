<script lang="ts" setup>
import { h, ref, watch, computed } from 'vue';
import { NDataTable, NSpin, NEmpty, NTag } from 'naive-ui';
import type { ActividadJornadaDto } from '#/api/sisu/actividades';
import { getActividadesJornada } from '#/api/sisu/actividades';
import { TIPO_ACTIVIDAD_META, ESTADO_ACTIVIDAD_META } from '../../actividades/data';
import { message } from '#/adapter/naive';

const props = defineProps<{ idJornada: number | null }>();

const actividades = ref<ActividadJornadaDto[]>([]);
const cargando = ref(false);

watch(() => props.idJornada, async (id) => {
  if (!id) { actividades.value = []; return; }
  cargando.value = true;
  try {
    actividades.value = (await getActividadesJornada(id)) ?? [];
  } catch {
    message.error('Error al cargar actividades de la jornada');
  } finally {
    cargando.value = false;
  }
}, { immediate: true });

const columnas = computed(() => [
  {
    title: 'Persona',
    key: 'nombrePersona',
    minWidth: 160,
    render: (row: ActividadJornadaDto) => {
      const esTitular = row.asignadoA === 'TITULAR';
      return h('div', [
        h('div', { style: { fontWeight: 600, fontSize: '0.84rem' } }, row.nombrePersona),
        h('span', {
          style: {
            display: 'inline-block',
            marginTop: '2px',
            padding: '0 7px',
            borderRadius: '8px',
            fontSize: '0.7rem',
            fontWeight: 600,
            background: esTitular ? '#fee2e2' : '#dbeafe',
            color: esTitular ? '#dc2626' : '#1d4ed8',
          },
        }, esTitular ? 'Titular' : 'Interno'),
      ]);
    },
  },
  {
    title: 'Actividad',
    key: 'nombreActividad',
    minWidth: 190,
    render: (row: ActividadJornadaDto) => {
      const meta = TIPO_ACTIVIDAD_META[row.tipoActividad];
      return h('div', [
        h('div', { style: { fontWeight: 600, fontSize: '0.84rem' } }, row.nombreActividad),
        h('span', {
          style: {
            display: 'inline-block',
            marginTop: '3px',
            padding: '1px 8px',
            borderRadius: '8px',
            fontSize: '0.7rem',
            fontWeight: 600,
            background: meta?.bg ?? '#f1f5f9',
            color: meta?.color ?? '#475569',
          },
        }, meta?.label ?? row.tipoActividad),
      ]);
    },
  },
  {
    title: 'Tipo',
    key: 'tipoActividad',
    width: 120,
    render: (row: ActividadJornadaDto) => {
      const meta = TIPO_ACTIVIDAD_META[row.tipoActividad];
      return h(NTag, {
        size: 'small',
        round: true,
        bordered: false,
        style: {
          background: meta?.bg ?? '#f1f5f9',
          color: meta?.color ?? '#475569',
        },
      }, () => meta?.label ?? row.tipoActividad);
    },
  },
  {
    title: 'Estado',
    key: 'estadoActividad',
    width: 130,
    render: (row: ActividadJornadaDto) => {
      const m = ESTADO_ACTIVIDAD_META[row.estadoActividad as keyof typeof ESTADO_ACTIVIDAD_META];
      return h(NTag, { type: m?.type ?? 'default', size: 'small', round: true }, () => m?.label ?? row.estadoActividad);
    },
  },
]);
</script>

<template>
  <NSpin :show="cargando">
    <div v-if="!cargando && actividades.length === 0" class="py-10">
      <NEmpty description="No hay actividades asignadas en esta jornada" />
    </div>

    <NDataTable
      v-else
      :columns="columnas"
      :data="actividades"
      :bordered="false"
      :striped="true"
      size="small"
      :scroll-x="750"
      :pagination="{ pageSize: 15, showSizePicker: true, pageSizes: [10, 15, 30] }"
    />
  </NSpin>
</template>
