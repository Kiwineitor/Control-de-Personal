<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import {
  NModal, NButton, NInput, NSelect, NSpace, NSpin,
  NForm, NFormItem, NText, NDivider,
} from 'naive-ui';
import type { SelectOption } from 'naive-ui';
import { message } from '#/adapter/naive';
import {
  crearMiActividad,
  getCatalogoActividades,
  type ActividadCatalogoDto,
  type ActividadJornadaAppDto,
} from '#/api/sisu/app';
import { createActividad } from '#/api/sisu/actividades';
import type { TipoActividad } from '#/api/sisu/actividades';

// ── Props / Emits ────────────────────────────────────────────────────────
interface Props { show: boolean }
const props = defineProps<Props>();
const emit  = defineEmits<{
  (e: 'update:show', v: boolean): void;
  (e: 'creada', v: ActividadJornadaAppDto): void;
}>();

// ── Estado ───────────────────────────────────────────────────────────────
const catalogo      = ref<ActividadCatalogoDto[]>([]);
const cargandoCat   = ref(false);
const guardando     = ref(false);

// Campo de nombre (texto libre)
const nombreInput    = ref('');
// ID si el usuario seleccionó una sugerencia del catálogo; null = actividad nueva
const idActividadSel = ref<number | null>(null);

// Solo visible cuando se va a crear una actividad nueva
const tipoNueva     = ref<TipoActividad>('CLINICA');

const horaInicio    = ref('');
const horaFin       = ref('');
const observaciones = ref('');

const HH_MM = /^([01]\d|2[0-3]):[0-5]\d$/;

// ── Opciones de tipo ──────────────────────────────────────────────────────
const TIPO_OPTIONS: SelectOption[] = [
  { label: 'Clínica',        value: 'CLINICA'        },
  { label: 'Administrativa', value: 'ADMINISTRATIVA' },
  { label: 'Académica',      value: 'ACADEMICA'      },
  { label: 'Supervisión',    value: 'SUPERVISION'    },
  { label: 'Emergencia',     value: 'EMERGENCIA'     },
  { label: 'Otra',           value: 'OTRA'           },
];

// ── Sugerencias de autocompletado ─────────────────────────────────────────
const sugerencias = computed<SelectOption[]>(() => {
  if (!nombreInput.value.trim()) return [];
  const q = nombreInput.value.toLowerCase();
  return catalogo.value
    .filter((a) => a.nombre.toLowerCase().includes(q))
    .slice(0, 8)
    .map((a) => ({
      label: `${a.nombre}  ·  ${a.tipoActividad}`,
      value: String(a.idActividad),
    }));
});

// Indica si el nombre escrito no coincide con ninguna entrada del catálogo
const esNueva = computed(() => idActividadSel.value === null && nombreInput.value.trim().length > 0);

// ── Al escribir en el campo nombre ───────────────────────────────────────
function onNombreInput(val: string) {
  nombreInput.value = val;
  // Si el texto ya no coincide con la actividad seleccionada, desmarcarla
  if (idActividadSel.value !== null) {
    const act = catalogo.value.find((a) => a.idActividad === idActividadSel.value);
    if (!act || !act.nombre.toLowerCase().includes(val.toLowerCase())) {
      idActividadSel.value = null;
    }
  }
}

// Al seleccionar una sugerencia
function onSugerenciaSelect(value: string) {
  const id  = Number(value);
  const act = catalogo.value.find((a) => a.idActividad === id);
  if (act) {
    idActividadSel.value = act.idActividad;
    nombreInput.value    = act.nombre;
    tipoNueva.value      = act.tipoActividad as TipoActividad;
  }
}

// ── Cargar catálogo al abrir ──────────────────────────────────────────────
watch(
  () => props.show,
  async (v) => {
    if (!v) return;
    cargandoCat.value = true;
    try {
      catalogo.value = (await getCatalogoActividades()) ?? [];
    } catch {
      message.error('Error al cargar actividades disponibles');
    } finally {
      cargandoCat.value = false;
    }
  },
);

// ── Submit ────────────────────────────────────────────────────────────────
async function guardar() {
  const nombre = nombreInput.value.trim();
  if (!nombre) { message.warning('Escribe el nombre de la actividad'); return; }
  if (horaInicio.value && !HH_MM.test(horaInicio.value))
    { message.warning('Hora inicio inválida (usa HH:mm)'); return; }
  if (horaFin.value && !HH_MM.test(horaFin.value))
    { message.warning('Hora fin inválida (usa HH:mm)'); return; }

  guardando.value = true;
  try {
    let idActividad = idActividadSel.value;

    // Si es una actividad nueva (no está en el catálogo), crearla primero
    if (idActividad === null) {
      const nueva = await createActividad({
        nombre,
        tipoActividad: tipoNueva.value,
        activo:        true,
      });
      idActividad = nueva.idActividad;
    }

    const act = await crearMiActividad({
      idActividad,
      horaInicio:    horaInicio.value    ? `${horaInicio.value}:00`    : undefined,
      horaFin:       horaFin.value       ? `${horaFin.value}:00`       : undefined,
      observaciones: observaciones.value || undefined,
    });

    message.success('Actividad registrada');
    emit('creada', act as ActividadJornadaAppDto);
    cerrar();
  } catch (e: any) {
    message.error(e?.message ?? 'Error al registrar la actividad');
  } finally {
    guardando.value = false;
  }
}

function cerrar() {
  nombreInput.value    = '';
  idActividadSel.value = null;
  tipoNueva.value      = 'CLINICA';
  horaInicio.value     = '';
  horaFin.value        = '';
  observaciones.value  = '';
  emit('update:show', false);
}
</script>

<template>
  <NModal
    :show="show"
    preset="card"
    style="width: 96%; max-width: 460px; border-radius: 18px"
    title="Registrar actividad"
    :mask-closable="false"
    @update:show="$emit('update:show', $event)"
  >
    <NSpin :show="cargandoCat || guardando">
      <NForm label-placement="top" :show-require-mark="false">

        <!-- Nombre de la actividad — campo principal -->
        <NFormItem label="Actividad realizada *">
          <div style="width: 100%; position: relative">
            <NInput
              :value="nombreInput"
              placeholder="Escribe el nombre de la actividad…"
              clearable
              maxlength="150"
              :status="esNueva ? 'warning' : undefined"
              @input="onNombreInput"
              @clear="() => { nombreInput = ''; idActividadSel = null; }"
            />

            <!-- Sugerencias del catálogo -->
            <div v-if="sugerencias.length > 0 && !idActividadSel" class="sugerencias">
              <div
                v-for="s in sugerencias"
                :key="s.value"
                class="sugerencia-item"
                @mousedown.prevent="onSugerenciaSelect(s.value as string)"
              >
                <span class="sugerencia-nombre">{{ (s.label as string).split('  ·  ')[0] }}</span>
                <span class="sugerencia-tipo">{{ (s.label as string).split('  ·  ')[1] }}</span>
              </div>
            </div>
          </div>

          <!-- Indicador: actividad del catálogo vs nueva -->
          <template v-if="nombreInput.trim()">
            <NText
              v-if="idActividadSel"
              depth="3"
              style="font-size: 12px; margin-top: 4px; display: block; color: #059669"
            >
              Del catálogo — se registrará directamente
            </NText>
            <NText
              v-else-if="esNueva"
              depth="3"
              style="font-size: 12px; margin-top: 4px; display: block; color: #d97706"
            >
              Actividad nueva — se añadirá al catálogo automáticamente
            </NText>
          </template>
        </NFormItem>

        <!-- Tipo (solo si es actividad nueva) -->
        <NFormItem v-if="esNueva" label="Tipo de actividad">
          <NSelect
            v-model:value="tipoNueva"
            :options="TIPO_OPTIONS"
            style="width: 100%"
          />
        </NFormItem>

        <NDivider style="margin: 4px 0 8px" />

        <!-- Horas -->
        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px">
          <NFormItem label="Hora inicio (HH:mm)">
            <NInput
              v-model:value="horaInicio"
              placeholder="07:30"
              maxlength="5"
              clearable
            />
          </NFormItem>
          <NFormItem label="Hora fin (HH:mm)">
            <NInput
              v-model:value="horaFin"
              placeholder="08:30"
              maxlength="5"
              clearable
            />
          </NFormItem>
        </div>

        <!-- Observaciones -->
        <NFormItem label="Observaciones (opcional)">
          <NInput
            v-model:value="observaciones"
            type="textarea"
            placeholder="Notas adicionales sobre la actividad…"
            :rows="3"
            maxlength="500"
            show-count
          />
        </NFormItem>

      </NForm>
    </NSpin>

    <template #footer>
      <NSpace justify="end">
        <NButton :disabled="guardando" @click="cerrar">Cancelar</NButton>
        <NButton
          type="primary"
          :loading="guardando"
          :disabled="!nombreInput.trim()"
          @click="guardar"
        >
          Registrar actividad
        </NButton>
      </NSpace>
    </template>
  </NModal>
</template>

<style scoped>
/* ── Modal custom overrides ──────────────────────────────────────────────── */
:deep(.n-modal-card) {
  border-radius: 18px !important;
}
:deep(.n-modal-card-header) {
  background: linear-gradient(90deg, #2563eb, #1d4ed8);
  margin: -1px -1px 0 -1px;
  border-radius: 18px 18px 0 0 !important;
  padding: 14px 20px !important;
}
:deep(.n-modal-card-header .n-card-header__main) {
  color: #ffffff !important;
  font-size: 16px !important;
  font-weight: 700 !important;
}
:deep(.n-modal-card-header .n-card-header__close) {
  color: rgba(255,255,255,0.8) !important;
}
:deep(.n-modal-card-body) {
  padding: 20px !important;
}

/* ── Sugerencias de autocompletado ───────────────────────────────────────── */
.sugerencias {
  position: absolute; top: calc(100% + 4px); left: 0; right: 0; z-index: 9999;
  background: #ffffff; border: 1px solid #e2e8f0;
  border-radius: 10px; box-shadow: 0 8px 24px rgba(30, 64, 175, 0.12);
  max-height: 240px; overflow-y: auto;
}

.sugerencia-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 14px; cursor: pointer; gap: 8px;
  transition: background 0.15s;
}
.sugerencia-item:hover { background: #f1f5f9; }
.sugerencia-item:first-child { border-radius: 10px 10px 0 0; }
.sugerencia-item:last-child  { border-radius: 0 0 10px 10px; }
.sugerencia-item + .sugerencia-item { border-top: 1px solid #f1f5f9; }

.sugerencia-nombre {
  font-size: 13px; font-weight: 500; color: #1e293b; flex: 1;
}
.sugerencia-tipo {
  font-size: 11px; color: #1e40af;
  background: #eff6ff; padding: 2px 8px; border-radius: 6px;
  white-space: nowrap;
}
</style>
