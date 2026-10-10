<script setup lang="ts">
import { ref, computed } from 'vue';
import {
  NModal, NButton, NSpace, NSpin, NAlert,
} from 'naive-ui';
import { message } from '#/adapter/naive';
import { useCamera } from '#/composables/useCamera';
import { encolarOperacion } from '#/composables/offlineQueue';
import {
  subirEvidenciaApp,
  type ActividadJornadaAppDto,
} from '#/api/sisu/app';

// ── Props / Emits ─────────────────────────────────────────────
interface Props {
  show:      boolean;
  actividad: ActividadJornadaAppDto;
}
const { isNative, capturarImagen } = useCamera();
async function usarCamara() {
  const foto = await capturarImagen();
  if (foto) archivoSel.value = foto;
}

const props = defineProps<Props>();
const emit  = defineEmits<{
  (e: 'update:show', v: boolean): void;
  (e: 'subida', v: ActividadJornadaAppDto): void;
}>();

// ── State ─────────────────────────────────────────────────────
const fileInputRef  = ref<HTMLInputElement | null>(null);
const archivoSel    = ref<File | null>(null);
const subiendo      = ref(false);

const nombreArchivo = computed(() =>
  archivoSel.value ? archivoSel.value.name : 'Ningún archivo seleccionado',
);

const tieneEvidencia = computed(() => !!props.actividad?.evidenciaUrl);

// ── Handlers ──────────────────────────────────────────────────
function seleccionar() {
  fileInputRef.value?.click();
}

function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement;
  archivoSel.value = input.files?.[0] ?? null;
}

async function subir() {
  if (!archivoSel.value) {
    message.warning('Selecciona un archivo primero');
    return;
  }
  subiendo.value = true;
  try {
    const res = await subirEvidenciaApp(
      props.actividad.idActividadJornada,
      archivoSel.value,
    );
    message.success('Evidencia cargada correctamente');

    // Actualizar la actividad localmente
    const actActualizada: ActividadJornadaAppDto = {
      ...props.actividad,
      evidenciaUrl:         (res as any)?.evidenciaUrl   ?? props.actividad.evidenciaUrl,
      evidenciaDownloadUrl: (res as any)?.downloadUrl    ?? props.actividad.evidenciaDownloadUrl,
    };
    emit('subida', actActualizada);
    cerrar();
  } catch (e: any) {
    if (!navigator.onLine && archivoSel.value) {
      const buf = await archivoSel.value.arrayBuffer();
      await encolarOperacion({
        tipo: 'subir-evidencia',
        endpoint: `/api/v1/actividades/asignacion/${props.actividad.idActividadJornada}/subir-evidencia`,
        method: 'POST',
        idActividadJornada: props.actividad.idActividadJornada,
        fileName: archivoSel.value.name,
        fileType: archivoSel.value.type,
        fileData: buf,
      });
      const actActualizada: ActividadJornadaAppDto = {
        ...props.actividad,
        evidenciaUrl:         'pendiente',
        evidenciaDownloadUrl: null,
      };
      emit('subida', actActualizada);
      message.info('Evidencia guardada localmente — se subirá al conectar');
      cerrar();
    } else {
      message.error(e?.message ?? 'Error al subir la evidencia');
    }
  } finally {
    subiendo.value = false;
  }
}

function cerrar() {
  archivoSel.value = null;
  if (fileInputRef.value) fileInputRef.value.value = '';
  emit('update:show', false);
}
</script>

<template>
  <NModal
    :show="show"
    preset="card"
    style="width: 96%; max-width: 420px; border-radius: 20px"
    title="Subir evidencia"
    :mask-closable="false"
    @update:show="$emit('update:show', $event)"
  >
    <NSpin :show="subiendo">
      <NSpace vertical size="large">

        <!-- Actividad -->
        <div class="evid-label">
          Actividad: <strong>{{ actividad.nombreActividad }}</strong>
        </div>

        <!-- Advertencia de reemplazo -->
        <NAlert v-if="tieneEvidencia" type="warning" :show-icon="true">
          Ya tienes una evidencia adjunta. Si subes una nueva,
          <strong>reemplazará</strong> la anterior.
        </NAlert>

        <!-- Input oculto + botón -->
        <input
          ref="fileInputRef"
          type="file"
          accept=".jpg,.jpeg,.png,.pdf,.mp4,.mov"
          style="display:none"
          @change="onFileChange"
        />

        <div class="file-picker">
          <!-- Botón cámara (solo en APK) -->
          <button v-if="isNative" class="file-picker__btn file-picker__btn--cam" @click="usarCamara">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
              <path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z" />
              <circle cx="12" cy="13" r="4" />
            </svg>
            Tomar foto / Galería
          </button>

          <!-- Botón archivo (siempre disponible) -->
          <button class="file-picker__btn file-picker__btn--file" :style="isNative ? 'margin-top:8px' : ''"
                  @click="fileInputRef?.click()">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
              <path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z" />
              <polyline points="14 2 14 8 20 8" />
              <line x1="12" y1="18" x2="12" y2="12" /><line x1="9" y1="15" x2="15" y2="15" />
            </svg>
            {{ isNative ? 'Seleccionar archivo' : 'Seleccionar archivo' }}
          </button>

          <span class="file-picker__name">
            {{ archivoSel ? archivoSel.name : 'Ningún archivo seleccionado' }}
          </span>
        </div>

        <div class="evid-formatos">
          Formatos: JPG, PNG, PDF, MP4, MOV — Máx. 150 MB
        </div>

      </NSpace>
    </NSpin>

    <template #footer>
      <NSpace justify="end">
        <NButton :disabled="subiendo" @click="cerrar">Cancelar</NButton>
        <NButton
          type="primary"
          :loading="subiendo"
          :disabled="!archivoSel"
          @click="subir"
        >
          {{ tieneEvidencia ? 'Reemplazar evidencia' : 'Subir evidencia' }}
        </NButton>
      </NSpace>
    </template>
  </NModal>
</template>

<style scoped>
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

.file-picker {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.file-picker__btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 16px;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  -webkit-tap-highlight-color: transparent;
}
.file-picker__btn--cam {
  border: 2px dashed #2563eb;
  background: #eff6ff;
  color: #1e40af;
}
.file-picker__btn--cam:active {
  background: #dbeafe;
}
.file-picker__btn--file {
  border: 2px dashed #e2e8f0;
  background: #f8fafc;
  color: #475569;
}
.file-picker__btn--file:active {
  border-color: #2563eb;
  background: #eff6ff;
  color: #1e40af;
}
.file-picker__name {
  font-size: 12px;
  color: #64748b;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.evid-label {
  font-size: 13px;
  color: #475569;
}
.evid-label strong {
  color: #1e293b;
}
.evid-formatos {
  font-size: 12px;
  color: #94a3b8;
}
</style>