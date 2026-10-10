<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import {
  NModal, NButton, NSelect, NInput, NSpace, NForm,
  NFormItem, NSpin, NAlert, NDivider,
  type FormInst,
} from 'naive-ui';
import { useUserStore } from '@vben/stores';
import { message, dialog } from '#/adapter/naive';
import {
  crearMiPermiso, modificarMiPermiso, getMiJornada, getTiposPermiso, subirJustificanteApp,
  type MiJornadaResponse, type SolicitudPermisoResponse, type TipoPermisoOpcion,
} from '#/api/sisu/app';

interface Props {
  show: boolean;
  solicitudEditar?: SolicitudPermisoResponse | null;
}
const props = defineProps<Props>();
const emit  = defineEmits<{
  (e: 'update:show', v: boolean): void;
  (e: 'creada',     v: SolicitudPermisoResponse): void;
  (e: 'modificada', v: SolicitudPermisoResponse): void;
}>();

const modoEdicion = computed(() => !!props.solicitudEditar);
const tituloModal = computed(() =>
  modoEdicion.value ? 'Corregir solicitud de permiso' : 'Nueva solicitud de permiso'
);

// ── Stores ─────────────────────────────────────────────────────
const userStore = useUserStore();
const solicitanteNombre = computed(
  () => userStore.userInfo?.realName ?? userStore.userInfo?.username ?? 'Usuario actual'
);

// ── Helpers ────────────────────────────────────────────────────
function hoyStr(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
const hFmt = (h: string | null | undefined) => h?.substring(0, 5) ?? '--:--';

function detectarDuracion(sol: SolicitudPermisoResponse): 'horas' | 'dia' | 'dias' {
  if (sol.horaInicioSolicitada) return 'horas';
  return 'dia';
}

// ── State ──────────────────────────────────────────────────────
const formRef      = ref<FormInst | null>(null);
const tipos        = ref<TipoPermisoOpcion[]>([]);
const jornada      = ref<MiJornadaResponse | null>(null);
const guardando    = ref(false);
const cargandoInit = ref(false);

const tipoDuracion = ref<'horas' | 'dia' | 'dias'>('dia');
const tipoDuracionOpts = [
  { label: 'Permiso por horas',  value: 'horas' },
  { label: 'Día completo',       value: 'dia'   },
  { label: 'Varios días (rango)', value: 'dias'  },
];

const fileInputRef = ref<HTMLInputElement | null>(null);
const archivoAdj   = ref<File | null>(null);

const form = ref({
  tipoPermiso:     null as string | null,
  motivoSolicitud: '',
  fechaPermiso:    hoyStr(),
  horaInicio:      '',
  horaFin:         '',
  fechaInicio:     hoyStr(),
  fechaFin:        '',
});

// ── Validaciones ───────────────────────────────────────────────
const rules = {
  tipoPermiso:    [{ required: true, message: 'Selecciona el tipo de permiso', trigger: ['blur','change'] }],
  motivoSolicitud:[{ required: true, message: 'El motivo es obligatorio',      trigger: 'blur' }],
};

function validarCamposCondicionales(): string | null {
  if (tipoDuracion.value === 'horas') {
    if (!form.value.fechaPermiso) return 'Indica la fecha del permiso';
    if (!form.value.horaInicio)   return 'Indica la hora de inicio';
    if (!form.value.horaFin)      return 'Indica la hora de fin';
    if (form.value.horaFin <= form.value.horaInicio)
      return 'La hora de fin debe ser posterior a la de inicio';
  }
  if (tipoDuracion.value === 'dia') {
    if (!form.value.fechaPermiso) return 'Indica la fecha del permiso';
  }
  if (tipoDuracion.value === 'dias') {
    if (!form.value.fechaInicio) return 'Indica la fecha de inicio';
    if (!form.value.fechaFin)    return 'Indica la fecha de fin';
    if (form.value.fechaFin < form.value.fechaInicio)
      return 'La fecha de fin debe ser igual o posterior al inicio';
  }
  return null;
}

// ── Carga inicial (y pre-relleno en modo edición) ──────────────
watch(() => props.show, async (v) => {
  if (!v) return;
  cargandoInit.value = true;
  try {
    const [tiposData, jornadaData] = await Promise.allSettled([
      getTiposPermiso(),
      getMiJornada(),
    ]);
    if (tiposData.status === 'fulfilled' && tiposData.value)
      tipos.value = tiposData.value;
    if (jornadaData.status === 'fulfilled')
      jornada.value = jornadaData.value ?? null;
  } finally {
    cargandoInit.value = false;
  }

  // Pre-rellenar formulario si venimos en modo edición
  if (props.solicitudEditar) {
    const sol = props.solicitudEditar;
    tipoDuracion.value = detectarDuracion(sol);
    form.value = {
      tipoPermiso:     sol.tipoPermiso,
      motivoSolicitud: sol.motivoSolicitud,
      fechaPermiso:    sol.fechaPermiso    ?? hoyStr(),
      horaInicio:      sol.horaInicioSolicitada?.substring(0, 5) ?? '',
      horaFin:         sol.horaFinSolicitada?.substring(0, 5)    ?? '',
      fechaInicio:     sol.fechaPermiso    ?? hoyStr(),
      fechaFin:        '',
    };
  }
});

// ── Guardar (crea o modifica según modo) ───────────────────────
async function guardar() {
  try { await formRef.value?.validate(); } catch { return; }
  const err = validarCamposCondicionales();
  if (err) { message.warning(err); return; }

  guardando.value = true;
  try {
    const payload: import('#/api/sisu/app').MiSolicitudRequest = {
      tipoPermiso:     form.value.tipoPermiso!,
      motivoSolicitud: form.value.motivoSolicitud,
    };

    if (tipoDuracion.value === 'horas') {
      payload.fechaPermiso = form.value.fechaPermiso || undefined;
      payload.horaInicio   = form.value.horaInicio   || undefined;
      payload.horaFin      = form.value.horaFin      || undefined;
    } else if (tipoDuracion.value === 'dia') {
      payload.fechaPermiso = form.value.fechaPermiso || undefined;
    } else {
      payload.fechaPermiso = form.value.fechaInicio || undefined;
      payload.fechaFin     = form.value.fechaFin    || undefined;
    }

    if (modoEdicion.value) {
      // ── Modo edición: modificar solicitud existente ────────────
      const actualizada = await modificarMiPermiso(
        props.solicitudEditar!.idSolicitudPermiso, payload
      ) as SolicitudPermisoResponse;
      emit('modificada', actualizada);
      cerrar();
      dialog.success({
        title:        'Solicitud corregida',
        content:      'Tu solicitud fue reenviada y está pendiente de revisión.',
        positiveText: 'Entendido',
      });
    } else {
      // ── Modo creación ──────────────────────────────────────────
      const nueva = await crearMiPermiso(payload) as SolicitudPermisoResponse;
      if (archivoAdj.value) {
        try {
          const actualizada = await subirJustificanteApp(nueva.idSolicitudPermiso, archivoAdj.value);
          emit('creada', actualizada);
        } catch {
          message.warning('Solicitud creada, pero no se pudo subir el adjunto. Adjúntalo desde la lista.');
          emit('creada', nueva);
        }
      } else {
        emit('creada', nueva);
      }
      cerrar();
      dialog.info({
        title:        'Solicitud enviada',
        content:
          'Tu solicitud de permiso ha sido registrada y será revisada por tu ' +
          'inmediato superior.\n\n' +
          'Espera la notificación de aprobación antes de ausentarte. ' +
          'Una vez aprobada, deberás registrar tu permiso en el sistema LONDRA ' +
          'adjuntando las evidencias, y también actualizarlo en esta aplicación.',
        positiveText: 'Entendido',
      });
    }
  } catch (e: any) {
    message.error(e?.message ?? 'Error al enviar la solicitud');
  } finally {
    guardando.value = false;
  }
}

function cerrar() {
  form.value = {
    tipoPermiso: null, motivoSolicitud: '',
    fechaPermiso: hoyStr(), horaInicio: '', horaFin: '',
    fechaInicio: hoyStr(), fechaFin: '',
  };
  tipoDuracion.value = 'dia';
  archivoAdj.value   = null;
  emit('update:show', false);
}
</script>

<template>
  <NModal
    :show="show"
    preset="card"
    style="width:96%;max-width:480px;border-radius:20px"
    :title="tituloModal"
    :mask-closable="false"
    @update:show="$emit('update:show', $event)"
  >
    <NSpin :show="guardando || cargandoInit">

      <NForm ref="formRef" :model="form" :rules="rules" label-placement="top" size="medium">

        <!-- ── Solicitante (solo lectura) ─────────────── -->
        <NFormItem label="Solicitante">
          <NInput :value="solicitanteNombre" readonly style="cursor:default" />
        </NFormItem>

        <!-- ── Jornada activa ──────────────────────────── -->
        <div v-if="jornada" class="jornada-info">
          <div class="jornada-info__icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
              <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
            </svg>
          </div>
          <div class="jornada-info__body">
            <span class="jornada-info__nombre">{{ jornada.nombre }}</span>
            <span class="jornada-info__meta">
              {{ hFmt(jornada.horaInicio) }} – {{ hFmt(jornada.horaFin) }}
              <template v-if="jornada.nombreLugar"> · {{ jornada.nombreLugar }}</template>
            </span>
          </div>
          <span class="jornada-info__badge">{{ jornada.tipoAsignacion }}</span>
        </div>

        <NDivider style="margin:8px 0" />

        <!-- ── Tipo de permiso ─────────────────────────── -->
        <NFormItem label="Tipo de permiso" path="tipoPermiso">
          <NSelect
            v-model:value="form.tipoPermiso"
            :options="tipos"
            placeholder="— Seleccione una opción —"
            filterable
            :loading="cargandoInit"
            style="width:100%"
          />
        </NFormItem>

        <!-- ── Duración ────────────────────────────────── -->
        <NFormItem label="Duración del permiso">
          <NSelect
            v-model:value="tipoDuracion"
            :options="tipoDuracionOpts"
            style="width:100%"
          />
        </NFormItem>

        <!-- ── Campos según tipo de duración ──────────── -->

        <!-- Por HORAS -->
        <template v-if="tipoDuracion === 'horas'">
          <NFormItem label="Fecha del permiso">
            <input
              v-model="form.fechaPermiso"
              type="date"
              class="native-input"
            />
          </NFormItem>
          <div class="horario-row">
            <div class="horario-item">
              <NFormItem label="Hora inicio">
                <input
                  v-model="form.horaInicio"
                  type="time"
                  class="native-input"
                />
              </NFormItem>
            </div>
            <div class="horario-arrow">→</div>
            <div class="horario-item">
              <NFormItem label="Hora fin">
                <input
                  v-model="form.horaFin"
                  type="time"
                  class="native-input"
                />
              </NFormItem>
            </div>
          </div>
        </template>

        <!-- DÍA COMPLETO -->
        <template v-else-if="tipoDuracion === 'dia'">
          <NFormItem label="Fecha del permiso">
            <input
              v-model="form.fechaPermiso"
              type="date"
              class="native-input"
            />
          </NFormItem>
        </template>

        <!-- VARIOS DÍAS -->
        <template v-else>
          <div class="horario-row">
            <div class="horario-item">
              <NFormItem label="Fecha inicio">
                <input
                  v-model="form.fechaInicio"
                  type="date"
                  class="native-input"
                />
              </NFormItem>
            </div>
            <div class="horario-arrow">→</div>
            <div class="horario-item">
              <NFormItem label="Fecha fin">
                <input
                  v-model="form.fechaFin"
                  type="date"
                  class="native-input"
                />
              </NFormItem>
            </div>
          </div>
        </template>

        <!-- ── Motivo ──────────────────────────────────── -->
        <NFormItem label="Motivo de la solicitud" path="motivoSolicitud">
          <NInput
            v-model:value="form.motivoSolicitud"
            type="textarea"
            :rows="3"
            placeholder="Describe brevemente el motivo de tu solicitud..."
            maxlength="500"
            show-count
          />
        </NFormItem>

        <!-- ── Adjunto opcional ────────────────────────── -->
        <NFormItem label="Evidencia adjunta (opcional)">
          <input
            ref="fileInputRef"
            type="file"
            accept=".jpg,.jpeg,.png,.pdf"
            style="display:none"
            @change="(e) => archivoAdj = (e.target as HTMLInputElement).files?.[0] ?? null"
          />
          <div class="file-picker" @click="fileInputRef?.click()">
            <svg class="file-picker__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22">
              <path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z" />
              <polyline points="14 2 14 8 20 8" />
              <line x1="12" y1="18" x2="12" y2="12" /><line x1="9" y1="15" x2="15" y2="15" />
            </svg>
            <div class="file-picker__text">
              <span v-if="archivoAdj" class="file-picker__name">{{ archivoAdj.name }}</span>
              <span v-else class="file-picker__placeholder">Toca para adjuntar JPG, PNG o PDF</span>
              <span class="file-picker__hint">Puedes adjuntarla ahora o desde la lista tras enviar</span>
            </div>
            <button
              v-if="archivoAdj"
              class="file-picker__clear"
              @click.stop="archivoAdj = null; if(fileInputRef) fileInputRef.value = ''"
            >✕</button>
          </div>
        </NFormItem>

      </NForm>

      <!-- Aviso -->
      <NAlert type="info" :show-icon="false" style="font-size:12px;border-radius:10px;margin-top:4px">
        Al enviar, la solicitud llegará a tu inmediato superior. La evidencia
        adjunta es necesaria para la validación final; puedes adjuntarla ahora
        o más tarde desde la lista de solicitudes.
      </NAlert>

    </NSpin>

    <template #footer>
      <NSpace justify="end">
        <NButton :disabled="guardando || cargandoInit" @click="cerrar">Cancelar</NButton>
        <NButton
          type="primary"
          :loading="guardando"
          :disabled="cargandoInit"
          @click="guardar"
        >
          {{ modoEdicion ? 'Guardar cambios' : 'Enviar solicitud' }}
        </NButton>
      </NSpace>
    </template>
  </NModal>
</template>

<style scoped>
/* ── Modal header gradient ──────────────────────────────────── */
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

/* ── Jornada info ───────────────────────────────────────────── */
.jornada-info {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #eff6ff;
  border: 1px solid #dbeafe;
  border-radius: 10px;
  padding: 10px 12px;
  margin-bottom: 4px;
}
.jornada-info__icon {
  width: 36px; height: 36px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  background: #dbeafe; color: #1e40af;
  flex-shrink: 0;
}
.jornada-info__body {
  display: flex;
  flex-direction: column;
  gap: 1px;
  flex: 1;
  min-width: 0;
}
.jornada-info__nombre {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.jornada-info__meta { font-size: 11px; color: #64748b; }
.jornada-info__badge {
  font-size: 10px;
  font-weight: 600;
  background: #2563eb;
  color: #ffffff;
  padding: 2px 8px;
  border-radius: 20px;
  flex-shrink: 0;
}

/* ── Input nativo (date / time) ─────────────────────────────── */
.native-input {
  width: 100%;
  height: 36px;
  padding: 0 12px;
  border: 1.5px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
  color: #1e293b;
  font-size: 14px;
  font-family: inherit;
  outline: none;
  box-sizing: border-box;
  -webkit-appearance: none;
  appearance: none;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.native-input:focus {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}

/* ── Horario row (horas + → + horas / fecha → fecha) ─────── */
.horario-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.horario-item  { flex: 1; min-width: 0; }
.horario-arrow {
  color: #94a3b8;
  font-size: 1.1rem;
  padding-top: 30px;
  flex-shrink: 0;
}

/* ── File picker ────────────────────────────────────────────── */
.file-picker {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 12px 14px;
  border: 1.5px dashed #e2e8f0;
  border-radius: 10px;
  background: #f8fafc;
  cursor: pointer;
  transition: border-color .15s, background .15s;
}
.file-picker:hover {
  border-color: #2563eb;
  background: #eff6ff;
}
.file-picker__icon { width: 22px; height: 22px; color: #64748b; flex-shrink: 0; }
.file-picker:hover .file-picker__icon { color: #2563eb; }
.file-picker__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}
.file-picker__name {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.file-picker__placeholder { font-size: 13px; color: #64748b; }
.file-picker__hint        { font-size: 11px; color: #94a3b8; }
.file-picker__clear {
  background: none;
  border: none;
  font-size: 14px;
  color: #94a3b8;
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 4px;
  flex-shrink: 0;
}
.file-picker__clear:hover { color: #dc2626; }
</style>
