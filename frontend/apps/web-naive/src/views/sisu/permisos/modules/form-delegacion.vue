<script lang="ts" setup>
import { ref, reactive, watch } from 'vue';
import { message } from '#/adapter/naive';
import { NModal, NForm, NFormItem, NInput, NSelect, NButton, NText } from 'naive-ui';
import { requestClient } from '#/api/request';
import { crearDelegacionApi } from '#/api/sisu/permisos';

// ── Props y emits ───────────────────────────────────────────────
const props = defineProps<{ show: boolean }>();
const emit  = defineEmits<{
  (e: 'update:show', val: boolean): void;
  (e: 'saved'): void;
}>();

// ── Estado ──────────────────────────────────────────────────────
const loading = ref(false);

const form = reactive({
  idJornada:             null as number | null,
  idTitularJornada:      null as number | null,
  idInternoResponsable:  null as number | null,
  motivo:                '',
  horaInicioPlanificada: '',
  horaFinPlanificada:    '',
});

const jornadaOpts = ref<{ label: string; value: number }[]>([]);
const titularOpts = ref<{ label: string; value: number }[]>([]);
const internoOpts = ref<{ label: string; value: number }[]>([]);
const loadingJ    = ref(false);
const loadingT    = ref(false);
const loadingI    = ref(false);

// ── Carga de datos ──────────────────────────────────────────────
async function loadJornadas() {
  loadingJ.value = true;
  try {
    const res  = await requestClient.get('/api/v1/jornadas/activas');
    const data = Array.isArray(res) ? res : (res?.data ?? []);
    jornadaOpts.value = data.map((j: any) => ({ label: j.nombre, value: j.idJornada }));
  } catch { jornadaOpts.value = []; }
  finally  { loadingJ.value = false; }
}

async function loadByJornada(id: number) {
  loadingT.value = loadingI.value = true;
  titularOpts.value = []; internoOpts.value = [];
  form.idTitularJornada = form.idInternoResponsable = null;
  try {
    const [resTJ, resIJ] = await Promise.all([
      requestClient.get(`/api/v1/jornadas/${id}/titulares`),
      requestClient.get(`/api/v1/jornadas/${id}/internos`),
    ]);
    const dataTJ = Array.isArray(resTJ) ? resTJ
                 : (resTJ?.content ?? resTJ?.data?.content ?? resTJ?.data ?? []);
    const dataIJ = Array.isArray(resIJ) ? resIJ
                 : (resIJ?.content ?? resIJ?.data?.content ?? resIJ?.data ?? []);

    titularOpts.value = dataTJ.map((tj: any) => ({
      label: `${tj.titular?.persona?.apellido ?? ''}, ${tj.titular?.persona?.nombre ?? ''}`,
      value: tj.idTitularJornada,
    }));
    internoOpts.value = dataIJ.map((ij: any) => {
      const i = ij.interno ?? ij;
      return { label: `${i.persona?.apellido ?? ''}, ${i.persona?.nombre ?? ''}`, value: i.idInterno };
    });
  } catch {}
  finally { loadingT.value = loadingI.value = false; }
}

// ── Watches ─────────────────────────────────────────────────────
watch(() => props.show, async (val) => {
  if (val) {
    resetForm();
    await loadJornadas();
  }
});

watch(() => form.idJornada, (val) => {
  if (val) loadByJornada(val);
  else {
    titularOpts.value = []; internoOpts.value = [];
    form.idTitularJornada = form.idInternoResponsable = null;
  }
});

function resetForm() {
  form.idJornada = null; form.idTitularJornada = null;
  form.idInternoResponsable = null; form.motivo = '';
  form.horaInicioPlanificada = ''; form.horaFinPlanificada = '';
  titularOpts.value = []; internoOpts.value = [];
}

// ── Guardar ─────────────────────────────────────────────────────
async function guardar() {
  if (!form.idTitularJornada)     { message.warning('Seleccione el titular');   return; }
  if (!form.idInternoResponsable) { message.warning('Seleccione el interno');    return; }
  if (!form.motivo.trim())        { message.warning('Ingrese el motivo');        return; }

  loading.value = true;
  try {
    await crearDelegacionApi({
      idTitularJornada:      form.idTitularJornada,
      idInternoResponsable:  form.idInternoResponsable,
      motivo:                form.motivo,
      horaInicioPlanificada: form.horaInicioPlanificada || undefined,
      horaFinPlanificada:    form.horaFinPlanificada    || undefined,
    });
    message.success('Delegación creada correctamente');
    emit('saved');
  } catch {
    message.error('Error al crear la delegación');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <NModal
    :show="show"
    preset="card"
    title="🔄 Nueva Delegación Manual"
    style="width: 560px"
    :mask-closable="false"
    @update:show="$emit('update:show', $event)"
  >
    <NText depth="3" style="font-size:0.85em; display:block; margin-bottom:12px">
      Crea una delegación directamente sin necesidad de solicitud de permiso previa.
    </NText>

    <NForm label-placement="top">
      <NFormItem label="Jornada Activa" required>
        <NSelect v-model:value="form.idJornada"
          :options="jornadaOpts" :loading="loadingJ"
          filterable clearable placeholder="Seleccionar jornada activa..." />
      </NFormItem>

      <NFormItem label="Titular que delega" required>
        <NSelect v-model:value="form.idTitularJornada"
          :options="titularOpts" :loading="loadingT"
          :disabled="!form.idJornada"
          filterable clearable placeholder="Seleccionar titular de la jornada..." />
      </NFormItem>

      <NFormItem label="Interno responsable" required>
        <NSelect v-model:value="form.idInternoResponsable"
          :options="internoOpts" :loading="loadingI"
          :disabled="!form.idJornada"
          filterable clearable placeholder="Seleccionar interno de la jornada..." />
      </NFormItem>

      <NFormItem label="Motivo" required>
        <NInput v-model:value="form.motivo"
          type="textarea" :rows="3"
          placeholder="Motivo de la delegación"
          maxlength="500" show-count />
      </NFormItem>

      <div style="display:grid; grid-template-columns:1fr 1fr; gap:12px">
        <NFormItem label="Hora inicio planificada">
          <NInput v-model:value="form.horaInicioPlanificada"
                  placeholder="HH:mm" maxlength="5" />
        </NFormItem>
        <NFormItem label="Hora fin planificada">
          <NInput v-model:value="form.horaFinPlanificada"
                  placeholder="HH:mm" maxlength="5" />
        </NFormItem>
      </div>
    </NForm>

    <div class="flex justify-end gap-2 mt-3">
      <NButton @click="$emit('update:show', false)">Cancelar</NButton>
      <NButton type="primary" :loading="loading" @click="guardar">
        Crear Delegación
      </NButton>
    </div>
  </NModal>
</template>