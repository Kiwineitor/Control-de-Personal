<script lang="ts" setup>
import type { ActividadDto, ActividadRequest, ActividadJornadaRequest, TipoActividad } from '#/api/sisu/actividades';
import { ref } from 'vue';
import { useVbenModal } from '@vben/common-ui';
import { message } from '#/adapter/naive';
import {
  NForm, NFormItem, NSelect, NInput, NButton, NAlert, NTag, NGrid, NGridItem, NDivider,
} from 'naive-ui';
import type { SelectOption } from 'naive-ui';
import { getCatalogoActivo, asignarActividad, createActividad } from '#/api/sisu/actividades';
import { TIPO_ACTIVIDAD_META } from '../data';

const emit = defineEmits<{ success: [] }>();

interface Context {
  idInternoJornada?:  number;
  idTitularJornada?:  number;
  nombrePersona:      string;
  tipoPersona:        'INTERNO' | 'TITULAR';
}

const TIPO_OPTIONS: SelectOption[] = [
  { label: 'Clínica',        value: 'CLINICA'        },
  { label: 'Administrativa', value: 'ADMINISTRATIVA' },
  { label: 'Académica',      value: 'ACADEMICA'      },
  { label: 'Supervisión',    value: 'SUPERVISION'    },
  { label: 'Emergencia',     value: 'EMERGENCIA'     },
  { label: 'Otra',           value: 'OTRA'           },
];

const ctx          = ref<Context>({ nombrePersona: '', tipoPersona: 'INTERNO' });
const loading      = ref(false);
const loadingCat   = ref(false);
const loadingNueva = ref(false);
const catalogoOpts = ref<SelectOption[]>([]);
const mostrarNueva = ref(false);

const form = ref({
  idActividad:   null as number | null,
  horaInicio:    '',
  horaFin:       '',
  observaciones: '',
});

const formNueva = ref({
  nombre:        '',
  tipoActividad: 'CLINICA' as TipoActividad,
  descripcion:   '',
});

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) return;
    ctx.value       = modalApi.getData<Context>() ?? ctx.value;
    form.value      = { idActividad: null, horaInicio: '', horaFin: '', observaciones: '' };
    mostrarNueva.value = false;
    formNueva.value = { nombre: '', tipoActividad: 'CLINICA', descripcion: '' };
    loadingCat.value = true;
    try {
      const lista = await getCatalogoActivo();
      catalogoOpts.value = lista.map(a => ({
        label: `${a.nombre}  ·  ${TIPO_ACTIVIDAD_META[a.tipoActividad]?.label ?? a.tipoActividad}`,
        value: a.idActividad,
      }));
    } finally {
      loadingCat.value = false;
    }
  },
});

function toggleNueva() {
  mostrarNueva.value = !mostrarNueva.value;
  if (mostrarNueva.value) {
    formNueva.value = { nombre: '', tipoActividad: 'CLINICA', descripcion: '' };
  }
}

async function handleCrearNueva() {
  if (!formNueva.value.nombre.trim()) { message.warning('El nombre es obligatorio'); return; }
  loadingNueva.value = true;
  try {
    const payload: ActividadRequest = {
      nombre:        formNueva.value.nombre.trim(),
      tipoActividad: formNueva.value.tipoActividad,
      descripcion:   formNueva.value.descripcion || undefined,
      activo:        true,
    };
    const nueva = await createActividad(payload);
    const opt: SelectOption = {
      label: `${nueva.nombre}  ·  ${TIPO_ACTIVIDAD_META[nueva.tipoActividad]?.label ?? nueva.tipoActividad}`,
      value: nueva.idActividad,
    };
    catalogoOpts.value = [opt, ...catalogoOpts.value];
    form.value.idActividad = nueva.idActividad;
    mostrarNueva.value = false;
    message.success(`Actividad "${nueva.nombre}" creada y seleccionada`);
  } catch (e: any) {
    message.error(e?.message ?? 'Error al crear la actividad');
  } finally {
    loadingNueva.value = false;
  }
}

const HH_MM = /^([01]\d|2[0-3]):[0-5]\d$/;

async function handleSubmit() {
  if (!form.value.idActividad) { message.warning('Selecciona una actividad'); return; }
  if (form.value.horaInicio && !HH_MM.test(form.value.horaInicio))
    { message.warning('Hora inicio inválida (usa HH:mm)'); return; }
  if (form.value.horaFin && !HH_MM.test(form.value.horaFin))
    { message.warning('Hora fin inválida (usa HH:mm)'); return; }

  loading.value = true;
  try {
    const req: ActividadJornadaRequest = {
      idActividad:      form.value.idActividad,
      idInternoJornada: ctx.value.idInternoJornada,
      idTitularJornada: ctx.value.idTitularJornada,
      horaInicio:       form.value.horaInicio ? `${form.value.horaInicio}:00` : undefined,
      horaFin:          form.value.horaFin    ? `${form.value.horaFin}:00`    : undefined,
      observaciones:    form.value.observaciones || undefined,
    };
    await asignarActividad(req);
    message.success('Actividad asignada correctamente');
    modalApi.close();
    emit('success');
  } catch (e: any) {
    message.error(e?.message ?? 'Error al asignar la actividad');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <Modal title="📋 Asignar Actividad a Jornada" :footer="false" style="width: 520px">
    <div class="space-y-4">

      <NAlert type="info" :show-icon="true" style="font-size:.81rem">
        Asignando actividad a
        <strong>{{ ctx.nombrePersona }}</strong>
        <NTag
          :type="ctx.tipoPersona === 'INTERNO' ? 'info' : 'error'"
          size="small" round class="ml-2"
        >
          {{ ctx.tipoPersona }}
        </NTag>
      </NAlert>

      <NForm label-placement="top" :show-require-mark="false">

        <!-- Selector de actividad existente -->
        <NFormItem label="Actividad del catálogo *">
          <NSelect
            v-model:value="form.idActividad"
            :options="catalogoOpts"
            :loading="loadingCat"
            placeholder="Seleccionar actividad…"
            filterable clearable
            style="width:100%"
          />
        </NFormItem>

        <!-- Toggle para crear nueva actividad -->
        <div class="flex items-center gap-2 mb-3" style="margin-top: -4px">
          <button
            type="button"
            class="text-xs font-medium transition-colors border-none bg-transparent p-0 cursor-pointer"
            :class="mostrarNueva ? 'text-slate-400 dark:text-slate-500' : 'text-emerald-700 dark:text-emerald-400'"
            @click="toggleNueva"
          >
            {{ mostrarNueva ? '✕ Cancelar nueva actividad' : '+ Crear nueva actividad' }}
          </button>
          <span v-if="!mostrarNueva" class="text-xs text-muted-foreground">
            (si no existe en el catálogo)
          </span>
        </div>

        <!-- Mini-formulario de nueva actividad -->
        <template v-if="mostrarNueva">
          <div class="rounded-lg border p-3 mb-3 space-y-3 bg-emerald-50 border-emerald-200 dark:bg-emerald-950/30 dark:border-emerald-800/50">
            <div class="text-xs font-semibold mb-1 text-emerald-700 dark:text-emerald-400">
              Nueva actividad — se guardará en el catálogo y quedará seleccionada
            </div>

            <NFormItem label="Nombre *" style="margin-bottom:8px">
              <NInput
                v-model:value="formNueva.nombre"
                placeholder="Ej: Revisión de pacientes, Toma de signos vitales…"
                maxlength="150"
                clearable
              />
            </NFormItem>

            <NFormItem label="Tipo" style="margin-bottom:8px">
              <NSelect
                v-model:value="formNueva.tipoActividad"
                :options="TIPO_OPTIONS"
                style="width:100%"
              />
            </NFormItem>

            <NFormItem label="Descripción (opcional)" style="margin-bottom:8px">
              <NInput
                v-model:value="formNueva.descripcion"
                type="textarea"
                :autosize="{ minRows: 2, maxRows: 3 }"
                placeholder="Detalle breve de la actividad…"
                maxlength="500"
              />
            </NFormItem>

            <div class="flex justify-end">
              <NButton
                type="primary"
                size="small"
                :loading="loadingNueva"
                :disabled="!formNueva.nombre.trim()"
                :style="{ background: '#0f766e', borderColor: '#0f766e' }"
                @click="handleCrearNueva"
              >
                Crear y seleccionar
              </NButton>
            </div>
          </div>
          <NDivider style="margin: 0 0 12px" />
        </template>

        <NGrid :cols="2" :x-gap="12">
          <NGridItem>
            <NFormItem label="Hora inicio (HH:mm)">
              <NInput v-model:value="form.horaInicio" placeholder="07:00" maxlength="5" clearable />
            </NFormItem>
          </NGridItem>
          <NGridItem>
            <NFormItem label="Hora fin (HH:mm)">
              <NInput v-model:value="form.horaFin" placeholder="14:00" maxlength="5" clearable />
            </NFormItem>
          </NGridItem>
        </NGrid>

        <NFormItem label="Observaciones (opcional)">
          <NInput
            v-model:value="form.observaciones"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 3 }"
            placeholder="Notas sobre esta actividad…"
            maxlength="500"
          />
        </NFormItem>
      </NForm>

      <div class="flex justify-end gap-2">
        <NButton @click="modalApi.close()">Cancelar</NButton>
        <NButton type="primary" :loading="loading" :disabled="!form.idActividad"
          :style="{ background: '#0f766e', borderColor: '#0f766e' }"
          @click="handleSubmit"
        >
          Asignar Actividad
        </NButton>
      </div>

    </div>
  </Modal>
</template>