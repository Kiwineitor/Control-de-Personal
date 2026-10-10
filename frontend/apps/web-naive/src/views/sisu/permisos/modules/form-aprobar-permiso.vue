<script lang="ts" setup>
import { ref, reactive, watch, computed } from 'vue';
import { message } from '#/adapter/naive';
import {
  NModal, NForm, NFormItem, NInput, NSelect,
  NButton, NSpace, NText, NDivider, NAlert,
} from 'naive-ui';
import { requestClient } from '#/api/request';
import { aprobarSolicitudApi } from '#/api/sisu/permisos';
import type { SolicitudPermisoResponse } from '#/api/sisu/permisos';
import { TIPO_PERMISO_ICON } from '../data';

// ── Props y emits ───────────────────────────────────────────────
const props = defineProps<{
  show: boolean;
  solicitud: SolicitudPermisoResponse | null;
}>();
const emit = defineEmits<{
  (e: 'update:show', val: boolean): void;
  (e: 'saved'): void;
}>();

// ── Estado ──────────────────────────────────────────────────────
const loading      = ref(false);
const crearDel     = ref(false);
const internoOpts  = ref<{ label: string; value: number }[]>([]);
const loadingInts  = ref(false);

const form = reactive({
  idInternoResponsable:  null as number | null,
  horaInicioPlanificada: '',
  horaFinPlanificada:    '',
  observacion:           '',
});

const tieneJornada = computed(() => !!props.solicitud?.idJornada);

// ── Carga de internos ───────────────────────────────────────────
async function loadInternos(idJornada: number) {
  loadingInts.value = true;
  try {
    const res  = await requestClient.get(`/api/v1/jornadas/${idJornada}/internos`);
    const data = Array.isArray(res) ? res
               : (res?.content ?? res?.data?.content ?? res?.data ?? []);
    internoOpts.value = data.map((ij: any) => {
      const i = ij.interno ?? ij;
      return {
        label: i.nombreCompleto ? i.nombreCompleto : `${i.apellido ?? ''}, ${i.nombre ?? ''}`,
        value: i.idInterno ?? i.idInternoJornada,
      };
    });
  } catch {
    internoOpts.value = [];
  } finally {
    loadingInts.value = false;
  }
}

// ── Watch: cuando se abre el modal ──────────────────────────────
watch(() => props.show, async (val) => {
  if (val && props.solicitud) {
    crearDel.value                = false;
    form.idInternoResponsable     = null;
    form.horaInicioPlanificada    = props.solicitud.horaInicioSolicitada ?? '';
    form.horaFinPlanificada       = props.solicitud.horaFinSolicitada    ?? '';
    form.observacion              = '';
    if (props.solicitud.idJornada) {
      await loadInternos(props.solicitud.idJornada);
    }
  }
});

watch(crearDel, (val) => { if (!val) form.idInternoResponsable = null; });

// ── Guardar ─────────────────────────────────────────────────────
async function aprobar() {
  if (crearDel.value && !form.idInternoResponsable) {
    message.warning('Seleccione el interno que cubrirá la delegación');
    return;
  }
  loading.value = true;
  try {
    await aprobarSolicitudApi(props.solicitud!.idSolicitudPermiso, {
      idInternoResponsable:  crearDel.value ? form.idInternoResponsable : null,
      idTitularJornada:      crearDel.value ? props.solicitud?.idTitularJornada : undefined,
      horaInicioPlanificada: crearDel.value && form.horaInicioPlanificada
        ? form.horaInicioPlanificada : undefined,
      horaFinPlanificada:    crearDel.value && form.horaFinPlanificada
        ? form.horaFinPlanificada : undefined,
      observacion:           form.observacion || undefined,
    });
    message.success(crearDel.value
      ? 'Solicitud aprobada con delegación ✓'
      : 'Solicitud aprobada ✓');
    emit('saved');
  } catch {
    message.error('Error al aprobar la solicitud');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <NModal
    :show="show"
    preset="card"
    title="✅ Aprobar Solicitud de Permiso"
    style="width: 580px"
    :mask-closable="false"
    @update:show="$emit('update:show', $event)"
  >
    <template v-if="solicitud">
      <!-- Info de la solicitud (read-only) -->
      <div class="p-3 rounded mb-4"
           style="background:rgba(59,130,246,.06); border:1px solid rgba(59,130,246,.2)">
        <NSpace :size="5" vertical>
          <NText>
            <span>{{ TIPO_PERMISO_ICON[solicitud.tipoPermiso] ?? '📝' }}</span>
            <b> {{ solicitud.tipoPermisoLabel }}</b>
            &nbsp;·&nbsp; {{ solicitud.apellidoTitular }}, {{ solicitud.nombreTitular }}
          </NText>
          <NText depth="2" style="font-size:0.9em">
            <b>Motivo:</b> {{ solicitud.motivoSolicitud }}
          </NText>
          <NText depth="2" style="font-size:0.88em" v-if="solicitud.fechaPermiso">
            <b>Fecha:</b> {{ solicitud.fechaPermiso }}
            <template v-if="solicitud.horaInicioSolicitada">
              &nbsp;·&nbsp; {{ solicitud.horaInicioSolicitada }}
              <template v-if="solicitud.horaFinSolicitada">
                – {{ solicitud.horaFinSolicitada }}
              </template>
            </template>
          </NText>
          <NText depth="2" style="font-size:0.88em" v-if="solicitud.nombreJornada">
            <b>Jornada:</b> {{ solicitud.nombreJornada }}
          </NText>
        </NSpace>
      </div>

      <NDivider style="margin: 10px 0" />

      <NAlert v-if="!tieneJornada" type="warning" class="mb-3" :bordered="false">
        Sin jornada vinculada. Se aprobará <b>sin crear delegación</b>.
      </NAlert>

      <template v-if="tieneJornada">
        <div class="flex items-center gap-3 mb-4">
          <NText>¿Asignar interno para cubrir la ausencia?</NText>
          <NButton size="small"
            :type="crearDel ? 'success' : 'default'"
            @click="crearDel = !crearDel"
          >
            {{ crearDel ? '✓ Sí, crear delegación' : 'No (sin reemplazo)' }}
          </NButton>
        </div>

        <NForm v-if="crearDel" label-placement="top">
          <NFormItem label="Interno responsable" required>
            <NSelect
              v-model:value="form.idInternoResponsable"
              :options="internoOpts"
              :loading="loadingInts"
              filterable clearable
              placeholder="Seleccionar interno de la jornada..."
            />
          </NFormItem>
          <div style="display:grid; grid-template-columns:1fr 1fr; gap:12px">
            <NFormItem label="Hora inicio delegación">
              <NInput v-model:value="form.horaInicioPlanificada"
                      placeholder="HH:mm" maxlength="5" />
            </NFormItem>
            <NFormItem label="Hora fin delegación">
              <NInput v-model:value="form.horaFinPlanificada"
                      placeholder="HH:mm" maxlength="5" />
            </NFormItem>
          </div>
        </NForm>
      </template>

      <NFormItem label="Observación (opcional)" style="margin-top: 8px">
        <NInput
          v-model:value="form.observacion"
          type="textarea" :rows="2"
          placeholder="Comentarios adicionales..."
          maxlength="500"
        />
      </NFormItem>

      <div class="flex justify-end gap-2 mt-4">
        <NButton @click="$emit('update:show', false)">Cancelar</NButton>
        <NButton type="success" :loading="loading" @click="aprobar">
          ✓ Aprobar Solicitud
        </NButton>
      </div>
    </template>
  </NModal>
</template>