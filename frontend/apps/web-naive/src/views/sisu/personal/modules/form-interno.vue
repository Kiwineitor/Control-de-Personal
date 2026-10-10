<script lang="ts" setup>
import type { InternoDto, InternoRequest, PersonaResumenDto } from '#/api/sisu/personal';
import { ref, computed, watch } from 'vue';
import { useVbenModal } from '@vben/common-ui';
import { message } from '#/adapter/naive';
import {
  NForm, NFormItem, NInput,
  NSelect, NDivider, NAlert, NGrid, NGridItem,
  NButton, NSwitch, NTag, NSpin,
} from 'naive-ui';
import type { SelectOption } from 'naive-ui';
import { createInterno, updateInterno, buscarPersonas } from '#/api/sisu/personal';

const emit = defineEmits<{ success: [] }>();

// ── Modo de registro de persona ────────────────────────────────────────────
type ModoPersona = 'buscar' | 'manual';
const modoPersona = ref<ModoPersona>('buscar');

// ── Opciones estáticas ────────────────────────────────────────────────────
const CARRERAS = [
  'MEDICINA', 'ENFERMERÍA', 'ODONTOLOGÍA', 'FARMACIA',
  'LABORATORIO CLÍNICO', 'FISIOTERAPIA', 'NUTRICIÓN',
].map(c => ({ label: c, value: c }));

const ANOS = [1,2,3,4,5,6].map(n => ({ label: `${n}° año`, value: n }));

// ── Estado ────────────────────────────────────────────────────────────────
const loading         = ref(false);
const buscandoPersona = ref(false);
const isEdit          = computed(() => !!form.value.idInterno);

// Opciones del selector de persona
const personaOptions      = ref<SelectOption[]>([]);
const personaSeleccionada = ref<PersonaResumenDto | null>(null);
const personaMap          = ref<Map<number, PersonaResumenDto>>(new Map());

const EMPTY_FORM = (): InternoRequest & { idInterno?: number } => ({
  idInterno:            undefined,
  idPersona:            undefined,
  nombre:               '',
  apellido:             '',
  ci:                   '',
  email:                '',
  telefono:             '',
  universidad:          '',
  carrera:              '',
  anoFormacion:         undefined,
  nroMatricula:         '',
  fechaInicioRotacion:  '',
  fechaFinRotacion:     '',
  activo:               true,
});

const form = ref(EMPTY_FORM());

// ── Modal ─────────────────────────────────────────────────────────────────
const [Modal, modalApi] = useVbenModal({
  onOpenChange(isOpen: boolean) {
    if (isOpen) {
      const data = modalApi.getData<InternoDto>();
      personaSeleccionada.value = null;
      personaOptions.value = [];
      personaMap.value.clear();

      if (data?.idInterno) {
        // Modo edición — datos personales editables directamente
        modoPersona.value = 'manual';
        form.value = {
          idInterno:            data.idInterno,
          idPersona:            undefined,
          nombre:               data.nombre               ?? '',
          apellido:             data.apellido             ?? '',
          ci:                   data.ci                   ?? '',
          email:                data.email                ?? '',
          telefono:             data.telefono             ?? '',
          universidad:          data.universidad          ?? '',
          carrera:              data.carrera              ?? '',
          anoFormacion:         data.anoFormacion,
          nroMatricula:         data.nroMatricula         ?? '',
          fechaInicioRotacion:  data.fechaInicioRotacion  ?? '',
          fechaFinRotacion:     data.fechaFinRotacion     ?? '',
          activo:               data.activo,
        };
      } else {
        modoPersona.value = 'buscar';
        form.value = EMPTY_FORM();
      }
    }
  },
});

// Al cambiar modo, limpiar selección de persona
watch(modoPersona, () => {
  form.value.idPersona = undefined;
  personaSeleccionada.value = null;
  form.value.nombre   = '';
  form.value.apellido = '';
  form.value.ci       = '';
  form.value.email    = '';
  form.value.telefono = '';
});

// Al seleccionar una persona del selector
function onPersonaChange(idPersona: number | null) {
  if (!idPersona) {
    personaSeleccionada.value = null;
    return;
  }
  personaSeleccionada.value = personaMap.value.get(idPersona) ?? null;
}

// Búsqueda remota de personas (mínimo 2 caracteres)
async function onBuscarPersona(q: string) {
  if (!q || q.length < 2) {
    personaOptions.value = [];
    return;
  }
  buscandoPersona.value = true;
  try {
    const personas = await buscarPersonas(q);
    personaMap.value.clear();
    personas.forEach(p => personaMap.value.set(p.idPersona, p));
    personaOptions.value = personas.map(p => ({
      label: `${p.nombreCompleto}${p.ci ? ` — CI: ${p.ci}` : ''}`,
      value: p.idPersona,
    }));
  } finally {
    buscandoPersona.value = false;
  }
}

// ── Validación ────────────────────────────────────────────────────────────
function validar(): string | null {
  if (modoPersona.value === 'buscar' && !form.value.idPersona)
    return 'Selecciona una persona o cambia a "Registrar nueva persona"';
  if (modoPersona.value === 'manual' && !form.value.nombre?.trim())
    return 'El nombre es obligatorio';
  if (modoPersona.value === 'manual' && !form.value.apellido?.trim())
    return 'El apellido es obligatorio';
  if (!form.value.universidad?.trim())    return 'La universidad es obligatoria';
  if (!form.value.carrera)               return 'La carrera es obligatoria';
  if (!form.value.fechaInicioRotacion)   return 'La fecha de inicio es obligatoria';
  if (!form.value.fechaFinRotacion)      return 'La fecha de fin es obligatoria';
  if (form.value.fechaFinRotacion < form.value.fechaInicioRotacion)
    return 'La fecha de fin debe ser posterior al inicio';
  return null;
}

// ── Submit ────────────────────────────────────────────────────────────────
async function handleSubmit() {
  const error = validar();
  if (error) { message.warning(error); return; }

  loading.value = true;
  try {
    const payload: InternoRequest = {
      idPersona:           modoPersona.value === 'buscar' ? form.value.idPersona : undefined,
      nombre:              modoPersona.value === 'manual' ? (form.value.nombre    || undefined) : undefined,
      apellido:            modoPersona.value === 'manual' ? (form.value.apellido  || undefined) : undefined,
      ci:                  modoPersona.value === 'manual' ? (form.value.ci        || undefined) : undefined,
      email:               modoPersona.value === 'manual' ? (form.value.email     || undefined) : undefined,
      telefono:            modoPersona.value === 'manual' ? (form.value.telefono  || undefined) : undefined,
      universidad:         form.value.universidad,
      carrera:             form.value.carrera,
      anoFormacion:        form.value.anoFormacion,
      nroMatricula:        form.value.nroMatricula || undefined,
      fechaInicioRotacion: form.value.fechaInicioRotacion,
      fechaFinRotacion:    form.value.fechaFinRotacion,
      activo:              form.value.activo,
    };

    if (isEdit.value) {
      await updateInterno(form.value.idInterno!, payload);
      message.success('Interno actualizado correctamente');
    } else {
      await createInterno(payload);
      message.success('Interno registrado correctamente');
    }

    modalApi.close();
    emit('success');
  } catch (e: any) {
    message.error(e?.message ?? 'Error al guardar el interno');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <Modal
    :title="isEdit ? '✏️ Editar Interno' : '➕ Nuevo Interno'"
    :footer="false"
    style="width: 720px"
  >
    <NForm label-placement="top" :show-require-mark="false">

      <NAlert type="success" :show-icon="true" class="mb-4" style="font-size:.82rem">
        Los internos se gestionan <strong>manualmente</strong> en SISU.
        El responsable se asigna desde la pestaña <strong>Responsable</strong>.
      </NAlert>

      <!-- ── SECCIÓN PERSONA (solo en creación) ─────────────────── -->
      <template v-if="!isEdit">
        <NDivider style="margin:4px 0 12px">
          <span style="font-size:.78rem;color:#94a3b8;font-weight:500">DATOS PERSONALES</span>
        </NDivider>

        <!-- Toggle de modo -->
        <div class="flex gap-2 mb-3">
          <NButton
            :type="modoPersona === 'buscar' ? 'primary' : 'default'"
            size="small"
            :style="modoPersona === 'buscar' ? { background: '#1d4ed8', borderColor: '#1d4ed8' } : {}"
            @click="modoPersona = 'buscar'"
          >
            🔍 Buscar persona existente
          </NButton>
          <NButton
            :type="modoPersona === 'manual' ? 'primary' : 'default'"
            size="small"
            :style="modoPersona === 'manual' ? { background: '#1d4ed8', borderColor: '#1d4ed8' } : {}"
            @click="modoPersona = 'manual'"
          >
            ✏️ Registrar nueva persona
          </NButton>
        </div>

        <!-- Modo: buscar persona existente -->
        <template v-if="modoPersona === 'buscar'">
          <NFormItem label="Buscar persona *">
            <NSpin :show="buscandoPersona" style="width:100%">
              <NSelect
                v-model:value="form.idPersona"
                :options="personaOptions"
                placeholder="Escribir nombre o CI para buscar…"
                filterable
                remote
                clearable
                :loading="buscandoPersona"
                :clear-filter-after-select="false"
                style="width:100%"
                @search="onBuscarPersona"
                @update:value="onPersonaChange"
              />
            </NSpin>
          </NFormItem>

          <!-- Tarjeta de persona seleccionada -->
          <div
            v-if="personaSeleccionada"
            class="mb-3 p-3 rounded-lg border border-blue-200 bg-blue-50 dark:border-blue-800/60 dark:bg-blue-950/40"
          >
            <div class="flex items-center gap-2 mb-1">
              <NTag type="info" size="small" round>Seleccionada</NTag>
              <span class="font-semibold text-sm text-foreground">{{ personaSeleccionada.nombreCompleto }}</span>
            </div>
            <div class="grid grid-cols-3 gap-x-4 text-xs text-gray-500 dark:text-gray-400">
              <span>CI: {{ personaSeleccionada.ci || '—' }}</span>
              <span>Email: {{ personaSeleccionada.email || '—' }}</span>
              <span>Tel: {{ personaSeleccionada.telefono || '—' }}</span>
            </div>
          </div>

          <NAlert v-else type="default" :show-icon="false" style="font-size:.78rem" class="mb-3">
            Escribe al menos 2 caracteres para buscar personas registradas en el sistema.
          </NAlert>
        </template>

        <!-- Modo: registrar nueva persona manualmente -->
        <template v-else>
          <NGrid :cols="2" :x-gap="16">
            <NGridItem>
              <NFormItem label="Nombre(s) *">
                <NInput
                  v-model:value="form.nombre"
                  placeholder="Ej: Juan Carlos"
                  maxlength="100"
                  clearable
                />
              </NFormItem>
            </NGridItem>
            <NGridItem>
              <NFormItem label="Apellido(s) *">
                <NInput
                  v-model:value="form.apellido"
                  placeholder="Ej: Mamani Quispe"
                  maxlength="100"
                  clearable
                />
              </NFormItem>
            </NGridItem>
            <NGridItem>
              <NFormItem label="CI">
                <NInput
                  v-model:value="form.ci"
                  placeholder="Ej: 8754321"
                  maxlength="20"
                  clearable
                />
              </NFormItem>
            </NGridItem>
            <NGridItem>
              <NFormItem label="Teléfono">
                <NInput
                  v-model:value="form.telefono"
                  placeholder="+591 7xxxxxxx"
                  maxlength="20"
                  clearable
                />
              </NFormItem>
            </NGridItem>
          </NGrid>
          <NFormItem label="Email">
            <NInput
              v-model:value="form.email"
              placeholder="juan@uap.edu.bo"
              maxlength="150"
              clearable
            />
          </NFormItem>
        </template>
      </template>

      <!-- MODO EDICIÓN: datos personales editables directamente -->
      <template v-else>
        <NDivider style="margin:4px 0 12px">
          <span style="font-size:.78rem;color:#94a3b8;font-weight:500">DATOS PERSONALES</span>
        </NDivider>
        <NGrid :cols="2" :x-gap="16">
          <NGridItem>
            <NFormItem label="Nombre(s) *">
              <NInput v-model:value="form.nombre" placeholder="Ej: Juan Carlos" maxlength="100" clearable />
            </NFormItem>
          </NGridItem>
          <NGridItem>
            <NFormItem label="Apellido(s) *">
              <NInput v-model:value="form.apellido" placeholder="Ej: Mamani Quispe" maxlength="100" clearable />
            </NFormItem>
          </NGridItem>
          <NGridItem>
            <NFormItem label="CI">
              <NInput v-model:value="form.ci" placeholder="Ej: 8754321" maxlength="20" clearable />
            </NFormItem>
          </NGridItem>
          <NGridItem>
            <NFormItem label="Teléfono">
              <NInput v-model:value="form.telefono" placeholder="+591 7xxxxxxx" maxlength="20" clearable />
            </NFormItem>
          </NGridItem>
        </NGrid>
        <NFormItem label="Email">
          <NInput v-model:value="form.email" placeholder="juan@uap.edu.bo" maxlength="150" clearable />
        </NFormItem>
      </template>

      <!-- ── Datos Académicos ─────────────────────────────────── -->
      <NDivider style="margin:12px 0">
        <span style="font-size:.78rem;color:#94a3b8;font-weight:500">DATOS ACADÉMICOS</span>
      </NDivider>

      <NGrid :cols="2" :x-gap="16">
        <NGridItem>
          <NFormItem label="Universidad *">
            <NInput
              v-model:value="form.universidad"
              placeholder="Ej: UAP, UMSA, UAGRM…"
              maxlength="150"
              clearable
            />
          </NFormItem>
        </NGridItem>
        <NGridItem>
          <NFormItem label="Carrera *">
            <NSelect
              v-model:value="form.carrera"
              :options="CARRERAS"
              placeholder="Seleccionar carrera"
              clearable
            />
          </NFormItem>
        </NGridItem>
        <NGridItem>
          <NFormItem label="Año de formación">
            <NSelect
              v-model:value="form.anoFormacion"
              :options="ANOS"
              placeholder="— No especificado —"
              clearable
            />
          </NFormItem>
        </NGridItem>
        <NGridItem>
          <NFormItem label="N° Matrícula universitaria">
            <NInput
              v-model:value="form.nroMatricula"
              placeholder="Ej: MED-2025-001"
              maxlength="50"
              clearable
            />
          </NFormItem>
        </NGridItem>
      </NGrid>

      <!-- ── Período de Rotación ──────────────────────────────── -->
      <NDivider style="margin:12px 0">
        <span style="font-size:.78rem;color:#94a3b8;font-weight:500">PERÍODO DE ROTACIÓN</span>
      </NDivider>

      <NGrid :cols="2" :x-gap="16">
        <NGridItem>
          <NFormItem label="Inicio de rotación *">
            <NInput
              v-model:value="form.fechaInicioRotacion"
              placeholder="YYYY-MM-DD"
              maxlength="10"
              clearable
            />
          </NFormItem>
        </NGridItem>
        <NGridItem>
          <NFormItem label="Fin de rotación *">
            <NInput
              v-model:value="form.fechaFinRotacion"
              placeholder="YYYY-MM-DD"
              maxlength="10"
              clearable
            />
          </NFormItem>
        </NGridItem>
      </NGrid>

      <!-- ── Estado ───────────────────────────────────────────── -->
      <NFormItem label="Estado">
        <NSwitch v-model:value="form.activo">
          <template #checked>Activo</template>
          <template #unchecked>Inactivo</template>
        </NSwitch>
      </NFormItem>

      <!-- ── Botones ───────────────────────────────────────────── -->
      <div class="flex justify-end gap-2 mt-4">
        <NButton @click="modalApi.close()">Cancelar</NButton>
        <NButton
          type="primary"
          :loading="loading"
          :style="{ background: '#1d4ed8', borderColor: '#1d4ed8' }"
          @click="handleSubmit"
        >
          {{ isEdit ? 'Actualizar Interno' : 'Registrar Interno' }}
        </NButton>
      </div>

    </NForm>
  </Modal>
</template>
