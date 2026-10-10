<script setup lang="ts">
import { ref } from 'vue';

import { NButton, NInput, NModal } from 'naive-ui';

import { message } from '#/adapter/naive';
import {
  type BitacoraEntrada,
  crearBitacora,
  subirEvidenciaBitacora,
} from '#/api/sisu/bitacora';
import { encolarOperacion } from '#/composables/offlineQueue';

const props = defineProps<{ show: boolean }>();
const emit  = defineEmits<{
  (e: 'update:show', v: boolean): void;
  (e: 'creada', entrada: BitacoraEntrada): void;
}>();

// ── Estado del formulario ────────────────────────────────────────
const ahora        = ref(formatLocalDatetime(new Date()));
const descripcion  = ref('');
const archivo      = ref<File | null>(null);
const archivoName  = ref('');
const guardando    = ref(false);
const entradaCreada = ref<BitacoraEntrada | null>(null);

function formatLocalDatetime(d: Date) {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

function formatFechaLegible(iso: string) {
  const d = new Date(iso);
  return d.toLocaleDateString('es-PE', {
    weekday: 'long', day: 'numeric', month: 'long', year: 'numeric',
  }) + ' ' + d.toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

// ── Acciones ─────────────────────────────────────────────────────
function cerrar() {
  emit('update:show', false);
  resetForm();
}

function resetForm() {
  ahora.value       = formatLocalDatetime(new Date());
  descripcion.value = '';
  archivo.value     = null;
  archivoName.value = '';
  entradaCreada.value = null;
}

function capturarAhora() {
  ahora.value = formatLocalDatetime(new Date());
}

function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement;
  if (input.files?.length) {
    archivo.value     = input.files[0]!;
    archivoName.value = input.files[0]!.name;
  }
}

function quitarArchivo() {
  archivo.value     = null;
  archivoName.value = '';
}

async function guardar() {
  if (!descripcion.value.trim()) {
    message.warning('Escribe una descripción del evento');
    return;
  }

  guardando.value = true;
  try {
    // 1. Crear la entrada
    const entrada = await crearBitacora({
      fechaHoraEvento: ahora.value,
      descripcion: descripcion.value.trim(),
    });

    // 2. Subir evidencia si hay
    if (archivo.value) {
      const actualizada = await subirEvidenciaBitacora(entrada.idBitacora, archivo.value);
      entradaCreada.value = actualizada;
      emit('creada', actualizada);
    } else {
      entradaCreada.value = entrada;
      emit('creada', entrada);
    }

    message.success('Bitácora registrada');
    setTimeout(() => cerrar(), 600);
  } catch (err: any) {
    if (!navigator.onLine) {
      await encolarOperacion({
        tipo: 'bitacora',
        endpoint: '/api/v1/app/bitacora',
        method: 'POST',
        body: {
          fechaHoraEvento: ahora.value,
          descripcion: descripcion.value.trim(),
        },
      });
      const localEntry: BitacoraEntrada = {
        idBitacora: -Date.now(),
        fechaHoraEvento: ahora.value,
        descripcion: descripcion.value.trim(),
        evidenciaUrl: null,
        evidenciaDownloadUrl: null,
        nombreJornada: null,
        nombreLugar: null,
        createdAt: new Date().toISOString(),
      };
      entradaCreada.value = localEntry;
      emit('creada', localEntry);
      message.info('Guardado localmente — se sincronizará al conectar');
      setTimeout(() => cerrar(), 800);
    } else {
      message.error(err?.message || 'Error al guardar');
    }
  } finally {
    guardando.value = false;
  }
}
</script>

<template>
  <NModal
    :show="props.show"
    :mask-closable="!guardando"
    :close-on-esc="!guardando"
    @update:show="(v) => !v && cerrar()"
  >
    <div class="bit-modal">
      <!-- Header -->
      <div class="bit-modal__header">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22">
          <path d="M12 20h9" />
          <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z" />
        </svg>
        <span>Nueva Bitácora</span>
      </div>

      <div v-if="!entradaCreada" class="bit-modal__body">
        <!-- Fecha/Hora -->
        <div class="bit-field">
          <label class="bit-label">Fecha y hora del evento</label>
          <div class="bit-datetime-row">
            <input
              v-model="ahora"
              type="datetime-local"
              class="bit-datetime"
              step="1"
            />
            <NButton
              size="small"
              secondary
              @click="capturarAhora"
            >
              Ahora
            </NButton>
          </div>
          <span class="bit-hint">{{ formatFechaLegible(ahora) }}</span>
        </div>

        <!-- Descripción -->
        <div class="bit-field">
          <label class="bit-label">Descripción *</label>
          <NInput
            v-model:value="descripcion"
            type="textarea"
            placeholder="Describe el evento..."
            :rows="3"
            :autosize="{ minRows: 2, maxRows: 5 }"
            :disabled="guardando"
          />
        </div>

        <!-- Evidencia -->
        <div class="bit-field">
          <label class="bit-label">Evidencia (opcional)</label>
          <div v-if="!archivoName" class="bit-evidence-actions">
            <label class="bit-evidence-btn">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
                <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
                <circle cx="8.5" cy="8.5" r="1.5" />
                <polyline points="21 15 16 10 5 21" />
              </svg>
              Foto
              <input
                type="file"
                accept="image/*"
                capture="environment"
                class="bit-file-input"
                @change="onFileChange"
              />
            </label>
            <label class="bit-evidence-btn">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                <polyline points="14 2 14 8 20 8" />
              </svg>
              Archivo
              <input
                type="file"
                accept=".jpg,.jpeg,.png,.pdf,.mp4,.mov"
                class="bit-file-input"
                @change="onFileChange"
              />
            </label>
          </div>
          <div v-else class="bit-evidence-preview">
            <span class="bit-evidence-name">{{ archivoName }}</span>
            <button class="bit-evidence-remove" @click="quitarArchivo">✕</button>
          </div>
        </div>
      </div>

      <!-- Confirmación -->
      <div v-else class="bit-modal__confirm">
        <svg viewBox="0 0 24 24" fill="none" stroke="#22c55e" stroke-width="2.5" width="48" height="48">
          <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
          <polyline points="22 4 12 14.01 9 11.01" />
        </svg>
        <p class="bit-confirm-text">Registrado correctamente</p>
      </div>

      <!-- Footer -->
      <div v-if="!entradaCreada" class="bit-modal__footer">
        <NButton
          quaternary
          :disabled="guardando"
          @click="cerrar"
        >
          Cancelar
        </NButton>
        <NButton
          type="primary"
          :loading="guardando"
          :disabled="guardando"
          @click="guardar"
        >
          Guardar
        </NButton>
      </div>
    </div>
  </NModal>
</template>

<style scoped>
:deep(.n-input--textarea .n-input__input-el),
:deep(.n-input--textarea .n-input__textarea-el) {
  color: #1e293b !important;
  caret-color: #1e293b;
}
:deep(.n-input--textarea .n-input__border),
:deep(.n-input--textarea .n-input--state-focus .n-input__border) {
  border-color: #cbd5e1 !important;
}
:deep(.n-input--textarea:hover .n-input__border) {
  border-color: #94a3b8 !important;
}
:deep(.n-input--textarea .n-input__placeholder) {
  color: #94a3b8 !important;
}

.bit-modal {
  background: #fff;
  border-radius: 16px;
  width: 92%;
  max-width: 400px;
  max-height: 85dvh;
  overflow-y: auto;
  box-shadow: 0 8px 32px rgba(0,0,0,.18);
}

.bit-modal__header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 20px 12px;
  font-size: 17px;
  font-weight: 700;
  color: #1e293b;
  border-bottom: 1px solid #f1f5f9;
}

.bit-modal__body {
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.bit-field { display: flex; flex-direction: column; gap: 6px; }

.bit-label {
  font-size: 13px;
  font-weight: 600;
  color: #475569;
}

.bit-hint {
  font-size: 11px;
  color: #94a3b8;
  margin-top: -2px;
}

.bit-datetime-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.bit-datetime {
  flex: 1;
  padding: 8px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 14px;
  color: #1e293b;
  background: #f8fafc;
}

.bit-datetime:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59,130,246,.15);
}

.bit-evidence-actions {
  display: flex;
  gap: 10px;
}

.bit-evidence-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 12px;
  border: 2px dashed #cbd5e1;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
  cursor: pointer;
  transition: all .15s;
}

.bit-evidence-btn:active {
  background: #f1f5f9;
  border-color: #3b82f6;
  color: #3b82f6;
}

.bit-file-input {
  display: none;
}

.bit-evidence-preview {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: 10px;
}

.bit-evidence-name {
  flex: 1;
  font-size: 13px;
  color: #166534;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bit-evidence-remove {
  background: none;
  border: none;
  font-size: 16px;
  color: #94a3b8;
  cursor: pointer;
  padding: 2px;
}

.bit-modal__confirm {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 40px 20px;
}

.bit-confirm-text {
  font-size: 16px;
  font-weight: 600;
  color: #166534;
}

.bit-modal__footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 12px 20px 18px;
  border-top: 1px solid #f1f5f9;
}
</style>
