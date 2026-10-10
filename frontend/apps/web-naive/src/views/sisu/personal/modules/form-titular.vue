<script lang="ts" setup>
import type { TitularDto, TitularRequest, PersonaResumenDto } from '#/api/sisu/personal';
import { ref, computed, watch } from 'vue';
import { useVbenModal } from '@vben/common-ui';
import { message } from '#/adapter/naive';
import {
  NForm, NFormItem, NInput, NInputNumber,
  NSwitch, NCheckbox, NDivider, NAlert,
  NGrid, NGridItem, NButton, NTag,
  NSelect, NSpin,
} from 'naive-ui';
import type { SelectOption } from 'naive-ui';
import { createTitular, updateTitular, buscarPersonas } from '#/api/sisu/personal';

const emit = defineEmits<{ success: [] }>();

// ── Modo de registro de persona ────────────────────────────────────────────
// 'buscar'  → seleccionar persona existente del sistema
// 'manual'  → ingresar datos de persona a mano (crea persona nueva)
type ModoPersona = 'buscar' | 'manual';
const modoPersona = ref<ModoPersona>('buscar');

// ── Estado ────────────────────────────────────────────────────────────────
const loading         = ref(false);
const buscandoPersona = ref(false);
const isEdit          = computed(() => !!form.value.idTitular);

// Opciones del selector de persona
const personaOptions   = ref<SelectOption[]>([]);
const personaSeleccionada = ref<PersonaResumenDto | null>(null);
// Mapa idPersona → objeto completo para pre-rellenar al seleccionar
const personaMap = ref<Map<number, PersonaResumenDto>>(new Map());

const EMPTY_FORM = (): TitularRequest & { idTitular?: number } => ({
  idTitular:              undefined,
  idPersona:              undefined,
  perId:                  0,
  contratoId:             0,
  nombre:                 '',
  apellido:               '',
  ci:                     '',
  email:                  '',
  telefono:               '',
  cargo:                  '',
  especialidad:           '',
  activo:                 true,
  sincronizarDesdeLondra: false,
});

const form = ref(EMPTY_FORM());

// ── Modal ─────────────────────────────────────────────────────────────────
const [Modal, modalApi] = useVbenModal({
  onOpenChange(isOpen: boolean) {
    if (isOpen) {
      const data = modalApi.getData<TitularDto>();
      personaSeleccionada.value = null;
      personaOptions.value = [];
      personaMap.value.clear();

      if (data?.idTitular) {
        // Modo edición — los datos personales son editables directamente
        modoPersona.value = 'manual';
        form.value = {
          idTitular:              data.idTitular,
          idPersona:              undefined,
          perId:                  data.perId        ?? 0,
          contratoId:             data.contratoId   ?? 0,
          nombre:                 data.nombre       ?? '',
          apellido:               data.apellido     ?? '',
          ci:                     data.ci           ?? '',
          email:                  data.email        ?? '',
          telefono:               data.telefono     ?? '',
          cargo:                  data.cargo        ?? '',
          especialidad:           data.especialidad ?? '',
          activo:                 data.activo,
          sincronizarDesdeLondra: false,
        };
      } else {
        // Modo creación — por defecto buscar persona
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

// Al seleccionar una persona del selector, pre-rellenar sus datos
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
  if (!form.value.perId || form.value.perId <= 0)
    return 'El Per ID de Londra es obligatorio';
  if (!form.value.contratoId || form.value.contratoId <= 0)
    return 'El Contrato ID de Londra es obligatorio';
  if (modoPersona.value === 'buscar' && !form.value.idPersona)
    return 'Selecciona una persona o cambia a "Registrar nueva persona"';
  if (modoPersona.value === 'manual' && !form.value.nombre?.trim())
    return 'El nombre de la persona es obligatorio';
  if (modoPersona.value === 'manual' && !form.value.apellido?.trim())
    return 'El apellido de la persona es obligatorio';
  return null;
}

// ── Submit ────────────────────────────────────────────────────────────────
async function handleSubmit() {
  const error = validar();
  if (error) { message.warning(error); return; }

  loading.value = true;
  try {
    const payload: TitularRequest = {
      idPersona:              modoPersona.value === 'buscar' ? form.value.idPersona : undefined,
      perId:                  form.value.perId,
      contratoId:             form.value.contratoId,
      // Solo se envían datos manuales si el modo es manual
      nombre:                 modoPersona.value === 'manual' ? (form.value.nombre   || undefined) : undefined,
      apellido:               modoPersona.value === 'manual' ? (form.value.apellido || undefined) : undefined,
      ci:                     modoPersona.value === 'manual' ? (form.value.ci       || undefined) : undefined,
      email:                  modoPersona.value === 'manual' ? (form.value.email    || undefined) : undefined,
      telefono:               modoPersona.value === 'manual' ? (form.value.telefono || undefined) : undefined,
      cargo:                  form.value.cargo           || undefined,
      especialidad:           form.value.especialidad    || undefined,
      activo:                 form.value.activo,
      sincronizarDesdeLondra: form.value.sincronizarDesdeLondra,
    };

    if (isEdit.value) {
      await updateTitular(form.value.idTitular!, payload);
      message.success('Titular actualizado correctamente');
    } else {
      await createTitular(payload);
      message.success('Titular registrado correctamente');
    }

    modalApi.close();
    emit('success');
  } catch (e: any) {
    message.error(e?.message ?? 'Error al guardar el titular');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <Modal
    :title="isEdit ? '✏️ Editar Titular' : '➕ Nuevo Titular'"
    :footer="false"
    style="width: 720px"
  >
    <NForm label-placement="top" :show-require-mark="false">

      <!-- Aviso Londra -->
      <NAlert type="info" :show-icon="true" class="mb-4" style="font-size:.82rem">
        Los datos se obtendrán automáticamente de <strong>Londra</strong> cuando la API
        esté disponible. El Per ID y Contrato ID son siempre requeridos.
      </NAlert>

      <!-- ── SECCIÓN PERSONA (solo en creación) ─────────────────── -->
      <template v-if="!isEdit">
        <div class="flex items-center gap-2 mb-3">
          <NTag type="success" size="small">Persona</NTag>
          <span class="text-sm font-semibold text-gray-600 dark:text-gray-300">Datos de la persona</span>
        </div>

        <!-- Toggle de modo -->
        <div class="flex gap-2 mb-3">
          <NButton
            :type="modoPersona === 'buscar' ? 'primary' : 'default'"
            size="small"
            @click="modoPersona = 'buscar'"
          >
            🔍 Buscar persona existente
          </NButton>
          <NButton
            :type="modoPersona === 'manual' ? 'primary' : 'default'"
            size="small"
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
                  placeholder="Ej: María Elena"
                  maxlength="100"
                  clearable
                />
              </NFormItem>
            </NGridItem>
            <NGridItem>
              <NFormItem label="Apellido(s) *">
                <NInput
                  v-model:value="form.apellido"
                  placeholder="Ej: Rodríguez Paz"
                  maxlength="100"
                  clearable
                />
              </NFormItem>
            </NGridItem>
            <NGridItem>
              <NFormItem label="CI">
                <NInput
                  v-model:value="form.ci"
                  placeholder="Ej: 7654321"
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
              placeholder="correo@sisu.bo"
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
            <NFormItem label="Nombre(s)">
              <NInput v-model:value="form.nombre" placeholder="Ej: María Elena" maxlength="100" clearable />
            </NFormItem>
          </NGridItem>
          <NGridItem>
            <NFormItem label="Apellido(s)">
              <NInput v-model:value="form.apellido" placeholder="Ej: Rodríguez Paz" maxlength="100" clearable />
            </NFormItem>
          </NGridItem>
          <NGridItem>
            <NFormItem label="CI">
              <NInput v-model:value="form.ci" placeholder="Ej: 7654321" maxlength="20" clearable />
            </NFormItem>
          </NGridItem>
          <NGridItem>
            <NFormItem label="Teléfono">
              <NInput v-model:value="form.telefono" placeholder="+591 7xxxxxxx" maxlength="20" clearable />
            </NFormItem>
          </NGridItem>
        </NGrid>
        <NFormItem label="Email">
          <NInput v-model:value="form.email" placeholder="correo@sisu.bo" maxlength="150" clearable />
        </NFormItem>
      </template>

      <NDivider style="margin:12px 0">
        <span style="font-size:.78rem;color:#94a3b8;font-weight:500">REFERENCIAS LONDRA</span>
      </NDivider>

      <!-- ── Referencias Londra ─────────────────────────────────── -->
      <NGrid :cols="2" :x-gap="16">
        <NGridItem>
          <NFormItem label="Per ID (Londra) *">
            <NInputNumber
              v-model:value="form.perId"
              :min="1"
              placeholder="Ej: 1023"
              class="w-full"
            />
          </NFormItem>
        </NGridItem>
        <NGridItem>
          <NFormItem label="Contrato ID (Londra) *">
            <NInputNumber
              v-model:value="form.contratoId"
              :min="1"
              placeholder="Ej: 5041"
              class="w-full"
            />
          </NFormItem>
        </NGridItem>
      </NGrid>

      <NDivider style="margin:12px 0">
        <span style="font-size:.78rem;color:#94a3b8;font-weight:500">DATOS DEL CARGO</span>
      </NDivider>

      <!-- ── Cargo y Especialidad ──────────────────────────────── -->
      <NGrid :cols="2" :x-gap="16">
        <NGridItem>
          <NFormItem label="Cargo">
            <NInput
              v-model:value="form.cargo"
              placeholder="Ej: MÉDICO GENERAL"
              maxlength="150"
              clearable
            />
          </NFormItem>
        </NGridItem>
        <NGridItem>
          <NFormItem label="Especialidad">
            <NInput
              v-model:value="form.especialidad"
              placeholder="Ej: CARDIOLOGÍA"
              maxlength="150"
              clearable
            />
          </NFormItem>
        </NGridItem>
      </NGrid>

      <!-- ── Opciones ──────────────────────────────────────────── -->
      <NGrid :cols="2" :x-gap="16">
        <NGridItem>
          <NFormItem label="Estado">
            <NSwitch v-model:value="form.activo">
              <template #checked>Activo</template>
              <template #unchecked>Inactivo</template>
            </NSwitch>
          </NFormItem>
        </NGridItem>
        <NGridItem>
          <NFormItem label="Sincronizar con Londra al guardar">
            <NCheckbox v-model:checked="form.sincronizarDesdeLondra">
              Sincronizar desde Londra
            </NCheckbox>
          </NFormItem>
        </NGridItem>
      </NGrid>

      <!-- ── Botones ───────────────────────────────────────────── -->
      <div class="flex justify-end gap-2 mt-4">
        <NButton @click="modalApi.close()">Cancelar</NButton>
        <NButton
          type="primary"
          :loading="loading"
          :style="{ background: '#e63946', borderColor: '#e63946' }"
          @click="handleSubmit"
        >
          {{ isEdit ? 'Actualizar Titular' : 'Registrar Titular' }}
        </NButton>
      </div>

    </NForm>
  </Modal>
</template>
