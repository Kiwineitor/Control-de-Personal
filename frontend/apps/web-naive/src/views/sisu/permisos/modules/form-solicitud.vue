<script lang="ts" setup>
import { ref, reactive, watch, computed, h, type VNodeChild } from 'vue';
import { message } from '#/adapter/naive';
import {
  NModal, NForm, NFormItem, NInput, NSelect, NButton, NSpin,
  NDatePicker, NTimePicker, NGrid, NGridItem, NTag, NText, NAlert,
  type SelectOption,
} from 'naive-ui';
import { requestClient } from '#/api/request';
import { crearSolicitudApi } from '#/api/sisu/permisos';
import { tipoPermisoOptions } from '../data';

// ── Props y emits ───────────────────────────────────────────────
const props = defineProps<{ show: boolean }>();
const emit  = defineEmits<{
  (e: 'update:show', val: boolean): void;
  (e: 'saved'): void;
}>();

const loading = ref(false);

const form = reactive({
  idTitular:            null as number | null,
  tipoPermiso:          null as string | null,
  motivoSolicitud:      '',
  fechaPermiso:         null as string | null,
  horaInicioSolicitada: null as string | null,
  horaFinSolicitada:    null as string | null,
  idJornada:            null as number | null,
});

// ── Estados de jornada para la UI ───────────────────────────────
interface JornadaTitular {
  idTitularJornada: number;
  idJornada: number;
  nombre: string;
  fechaInicio: string;
  fechaFin: string;
  estadoJornada: 'ACTIVA' | 'CERRADA' | 'CANCELADA';
  estadoAsignacion: 'PROGRAMADO' | 'CONFIRMADO' | 'EN_CURSO' | 'COMPLETADO' | 'CANCELADO';
  lugarNombre: string | null;
}

const titularOpts  = ref<{ label: string; value: number }[]>([]);
const jornadasTitular = ref<JornadaTitular[]>([]);
const loadingT     = ref(false);
const loadingJ     = ref(false);

// ── Helpers para interpretar estado ─────────────────────────────
function jornadaSeleccionable(j: JornadaTitular): boolean {
  if (j.estadoJornada !== 'ACTIVA') return false;
  return j.estadoAsignacion === 'PROGRAMADO'
      || j.estadoAsignacion === 'CONFIRMADO'
      || j.estadoAsignacion === 'EN_CURSO';
}

function jornadaTagType(j: JornadaTitular): 'success' | 'info' | 'default' | 'warning' | 'error' {
  if (j.estadoJornada !== 'ACTIVA') return 'default';
  if (j.estadoAsignacion === 'EN_CURSO')   return 'success';
  if (j.estadoAsignacion === 'CONFIRMADO') return 'success';
  if (j.estadoAsignacion === 'PROGRAMADO') return 'info';
  return 'default';
}

function jornadaTagLabel(j: JornadaTitular): string {
  if (j.estadoJornada === 'CERRADA')   return 'Culminada';
  if (j.estadoJornada === 'CANCELADA') return 'Cancelada';
  if (j.estadoAsignacion === 'COMPLETADO') return 'Culminada';
  if (j.estadoAsignacion === 'CANCELADO')  return 'Cancelada';
  if (j.estadoAsignacion === 'EN_CURSO')   return 'Activa';
  if (j.estadoAsignacion === 'CONFIRMADO') return 'Vigente';
  if (j.estadoAsignacion === 'PROGRAMADO') return 'Vigente';
  return j.estadoAsignacion;
}

// ── Opciones para el select de jornadas ─────────────────────────
const jornadaOpts = computed(() =>
  jornadasTitular.value.map((j) => ({
    label: j.nombre,
    value: j.idJornada,
    disabled: !jornadaSeleccionable(j),
    // guardamos la jornada completa para renderizado
    _jornada: j,
  }))
);

// ── Carga de titulares ───────────────────────────────────────────
async function loadTitulares() {
  loadingT.value = true;
  try {
    const res  = await requestClient.get('/api/v1/titulares',
      { params: { page: 0, size: 100, sort: 'apellido' } });
    const data = res?.content ?? res?.data?.content ?? [];
    titularOpts.value = data.map((t: any) => ({
      label: `${t.apellido ?? ''}, ${t.nombre ?? ''}`,
      value: t.idTitular,
    }));
  } catch { titularOpts.value = []; }
  finally  { loadingT.value = false; }
}

// ── Carga de jornadas cuando cambia el titular ───────────────────
async function loadJornadasDeTitular(idTitular: number) {
  jornadasTitular.value = [];
  form.idJornada = null;
  loadingJ.value = true;
  try {
    const res  = await requestClient.get(`/api/v1/jornadas/por-titular/${idTitular}`);
    const data = Array.isArray(res) ? res : (res?.data ?? []);
    jornadasTitular.value = data;
  } catch { jornadasTitular.value = []; }
  finally  { loadingJ.value = false; }
}

watch(() => form.idTitular, (val) => {
  if (val) loadJornadasDeTitular(val);
  else { jornadasTitular.value = []; form.idJornada = null; }
});

// ── Watch: apertura del modal ───────────────────────────────────
watch(() => props.show, async (val) => {
  if (val) {
    reset();
    await loadTitulares();
  }
});

function reset() {
  form.idTitular = null;  form.tipoPermiso = null;  form.motivoSolicitud = '';
  form.fechaPermiso = null; form.horaInicioSolicitada = null; form.horaFinSolicitada = null;
  form.idJornada = null;
  jornadasTitular.value = [];
}

// ── Render personalizado para opciones de jornada ───────────────
type JornadaSelectOption = SelectOption & { _jornada?: JornadaTitular };

function renderJornadaLabel(option: JornadaSelectOption): VNodeChild {
  const j = option._jornada;
  if (!j) return option.label as string;
  return h('div', { class: 'jornada-option' }, [
    h('span', { class: 'jornada-nombre' }, option.label as string),
    h(NTag, { type: jornadaTagType(j), size: 'tiny', bordered: false },
      () => jornadaTagLabel(j)),
  ]);
}

// ── Guardar ─────────────────────────────────────────────────────
async function guardar() {
  if (!form.idTitular)              { message.warning('Seleccione el titular');           return; }
  if (!form.tipoPermiso)            { message.warning('Seleccione el tipo de permiso');   return; }
  if (!form.motivoSolicitud.trim()) { message.warning('Ingrese el motivo');               return; }
  if (!form.fechaPermiso)           { message.warning('Seleccione la fecha del permiso'); return; }

  loading.value = true;
  try {
    await crearSolicitudApi({
      idTitular:            form.idTitular,
      tipoPermiso:          form.tipoPermiso,
      motivoSolicitud:      form.motivoSolicitud,
      fechaPermiso:         form.fechaPermiso         ?? undefined,
      horaInicioSolicitada: form.horaInicioSolicitada ?? undefined,
      horaFinSolicitada:    form.horaFinSolicitada    ?? undefined,
      idJornada:            form.idJornada            ?? undefined,
    });
    message.success('Solicitud registrada correctamente');
    emit('saved');
  } catch {
    message.error('Error al registrar la solicitud');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <NModal
    :show="show"
    preset="card"
    title="Nueva Solicitud de Permiso"
    style="width: 580px"
    :mask-closable="false"
    @update:show="$emit('update:show', $event)"
  >
    <NSpin :show="loading">
      <NForm label-placement="top">

        <NFormItem label="Titular" required>
          <NSelect
            v-model:value="form.idTitular"
            :options="titularOpts"
            :loading="loadingT"
            filterable clearable
            placeholder="Buscar titular..."
          />
        </NFormItem>

        <!-- Jornadas del titular con estados visuales -->
        <NFormItem label="Jornada relacionada">
          <template v-if="!form.idTitular">
            <NText depth="3" style="font-size: 0.88em">
              Seleccione un titular para ver sus jornadas
            </NText>
          </template>
          <template v-else-if="loadingJ">
            <NText depth="3" style="font-size: 0.88em">Cargando jornadas...</NText>
          </template>
          <template v-else-if="jornadasTitular.length === 0">
            <NAlert type="info" :bordered="false" style="font-size: 0.85em; width: 100%">
              Este titular no tiene jornadas asignadas
            </NAlert>
          </template>
          <template v-else>
            <div style="width: 100%">
              <NSelect
                v-model:value="form.idJornada"
                :options="jornadaOpts"
                :render-label="renderJornadaLabel"
                clearable
                placeholder="Seleccionar jornada..."
              />

              <!-- Leyenda de estados de las jornadas -->
              <div class="jornadas-leyenda">
                <div
                  v-for="j in jornadasTitular"
                  :key="j.idJornada"
                  :class="['jornada-item', { 'jornada-bloqueada': !jornadaSeleccionable(j) }]"
                >
                  <NTag :type="jornadaTagType(j)" size="tiny" :bordered="false">
                    {{ jornadaTagLabel(j) }}
                  </NTag>
                  <span class="jornada-info">
                    <b>{{ j.nombre }}</b>
                    <span class="jornada-fechas">
                      {{ j.fechaInicio }} — {{ j.fechaFin }}
                      <template v-if="j.lugarNombre"> · {{ j.lugarNombre }}</template>
                    </span>
                  </span>
                  <span v-if="!jornadaSeleccionable(j)" class="jornada-bloq-label">
                    No disponible
                  </span>
                </div>
              </div>
            </div>
          </template>
        </NFormItem>

        <NFormItem label="Tipo de Permiso" required>
          <NSelect
            v-model:value="form.tipoPermiso"
            :options="tipoPermisoOptions"
            placeholder="Seleccionar tipo..."
          />
        </NFormItem>

        <NFormItem label="Motivo" required>
          <NInput
            v-model:value="form.motivoSolicitud"
            type="textarea" :rows="3"
            placeholder="Describa el motivo del permiso"
            maxlength="500" show-count
          />
        </NFormItem>

        <NGrid :cols="3" :x-gap="12">
          <NGridItem>
            <NFormItem label="Fecha del permiso *">
              <NDatePicker
                v-model:formatted-value="form.fechaPermiso"
                type="date"
                value-format="yyyy-MM-dd"
                clearable
                style="width: 100%"
              />
            </NFormItem>
          </NGridItem>
          <NGridItem>
            <NFormItem label="Hora inicio">
              <NTimePicker
                v-model:formatted-value="form.horaInicioSolicitada"
                format="HH:mm"
                clearable
                :minutes-step="5"
                style="width: 100%"
              />
            </NFormItem>
          </NGridItem>
          <NGridItem>
            <NFormItem label="Hora fin">
              <NTimePicker
                v-model:formatted-value="form.horaFinSolicitada"
                format="HH:mm"
                clearable
                :minutes-step="5"
                style="width: 100%"
              />
            </NFormItem>
          </NGridItem>
        </NGrid>

      </NForm>

      <div class="flex justify-end gap-2 mt-3">
        <NButton @click="$emit('update:show', false)">Cancelar</NButton>
        <NButton type="primary" :loading="loading" @click="guardar">
          Enviar Solicitud
        </NButton>
      </div>
    </NSpin>
  </NModal>
</template>

<style scoped>
.jornada-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
}
.jornada-nombre {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.jornadas-leyenda {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.jornada-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 5px 8px;
  border-radius: 6px;
  background: rgba(128, 128, 128, 0.06);
  font-size: 0.82em;
}
.jornada-bloqueada {
  opacity: 0.55;
}
.jornada-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.jornada-fechas {
  font-size: 0.9em;
  opacity: 0.65;
}
.jornada-bloq-label {
  font-size: 0.78em;
  opacity: 0.5;
  align-self: center;
  white-space: nowrap;
}
</style>
