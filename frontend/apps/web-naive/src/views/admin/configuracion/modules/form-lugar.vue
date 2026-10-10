<script lang="ts" setup>
import { nextTick, reactive, ref, watch }     from 'vue';
import { NButton, NForm, NFormItem, NInput,
         NInputNumber, NModal, NSelect,
         NDivider, NAlert }                    from 'naive-ui';
import { message }                             from '#/adapter/naive';
import {
  crearLugarApi, actualizarLugarApi,
  type LugarResponse,
} from '#/api/admin/configuracion';
import { TIPO_LUGAR_OPCIONES }                from '../data';
import MapPickerModal from './map-picker-modal.vue';

// Leaflet (instalado vía pnpm add leaflet @types/leaflet)
import type L from 'leaflet';

const props = defineProps<{ show: boolean; lugar: LugarResponse | null }>();
const emit  = defineEmits<{
  (e: 'update:show', val: boolean): void;
  (e: 'saved'): void;
}>();

const loading = ref(false);
const esEdicion = ref(false);
const showMapPicker = ref(false);

const form = reactive({
  nombre:         '',
  descripcion:    '',
  tipoLugar:      null as string | null,
  capacidad:      null as number | null,
  activo:         true,
  latitudCentro:  null as number | null,
  longitudCentro: null as number | null,
  radioMetros:    100 as number | null,
});

// ── Leaflet refs ───────────────────────────────────────────────────────
let mapInstance: L.Map | null          = null;
let markerInstance: L.Marker | null    = null;
let circleInstance: L.Circle | null    = null;

// ── Watch: esperar animación completa del NModal (~300ms) ─────────────
watch(() => props.show, (val) => {
  if (!val) return;
  resetForm();
  if (props.lugar) {
    esEdicion.value     = true;
    form.nombre         = props.lugar.nombre;
    form.descripcion    = props.lugar.descripcion  ?? '';
    form.tipoLugar      = props.lugar.tipoLugar    ?? null;
    form.capacidad      = props.lugar.capacidad    ?? null;
    form.activo         = props.lugar.activo;
    form.latitudCentro  = props.lugar.latitudCentro  ?? null;
    form.longitudCentro = props.lugar.longitudCentro ?? null;
    form.radioMetros    = props.lugar.radioMetros    ?? 100;
  }
});

function onMapSelected(data: { lat: number; lng: number; radio: number }) {
  form.latitudCentro  = data.lat;
  form.longitudCentro = data.lng;
  form.radioMetros    = data.radio;
}

function limpiarGeofencing() {
  form.latitudCentro  = null;
  form.longitudCentro = null;
  form.radioMetros    = 100;
}

function renderMapaMarker(L: any, lat: number, lng: number) {
  markerInstance?.remove();
  circleInstance?.remove();

  markerInstance = L.marker([lat, lng])
    .bindPopup(`📍 ${lat.toFixed(5)}, ${lng.toFixed(5)}`)
    .addTo(mapInstance!)
    .openPopup();

  if (form.radioMetros) {
    circleInstance = L.circle([lat, lng], {
      radius:      form.radioMetros,
      color:       '#3b82f6',
      fillColor:   '#3b82f6',
      fillOpacity: 0.15,
      weight:      2,
    }).addTo(mapInstance!);
  }
}

// Actualizar círculo al cambiar radio
watch(() => form.radioMetros, async (val) => {
  if (!circleInstance || !val) return;
  circleInstance.setRadius(val);
});


// ── CRUD ───────────────────────────────────────────────────────────────
function resetForm() {
  esEdicion.value     = false;
  form.nombre         = '';
  form.descripcion    = '';
  form.tipoLugar      = null;
  form.capacidad      = null;
  form.activo         = true;
  form.latitudCentro  = null;
  form.longitudCentro = null;
  form.radioMetros    = 100;
}

async function guardar() {
  if (!form.nombre.trim()) {
    message.warning('El nombre del lugar es obligatorio');
    return;
  }

  const hayAlgeoGeo = form.latitudCentro || form.longitudCentro || form.radioMetros;
  const hayTodoGeo  = form.latitudCentro && form.longitudCentro && form.radioMetros;
  if (hayAlgeoGeo && !hayTodoGeo) {
    message.warning('Para geofencing debes definir latitud, longitud y radio juntos');
    return;
  }

  loading.value = true;
  try {
    const payload = {
      nombre:         form.nombre.trim(),
      descripcion:    form.descripcion || undefined,
      tipoLugar:      form.tipoLugar ?? undefined,
      capacidad:      form.capacidad ?? undefined,
      activo:         form.activo,
      latitudCentro:  form.latitudCentro ?? undefined,
      longitudCentro: form.longitudCentro ?? undefined,
      radioMetros:    form.radioMetros ?? undefined,
    };

    if (esEdicion.value && props.lugar) {
      await actualizarLugarApi(props.lugar.idLugar, payload);
      message.success('Lugar actualizado correctamente');
    } else {
      await crearLugarApi(payload);
      message.success('Lugar creado correctamente');
    }

    emit('saved');
    emit('update:show', false);
  } catch (err: any) {
    message.error(err?.response?.data?.message ?? 'Error al guardar el lugar');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <NModal
    :show="show"
    preset="card"
    :title="esEdicion ? '✏️ Editar Lugar' : '➕ Nuevo Lugar'"
    style="width: 680px"
    :mask-closable="false"
    @update:show="$emit('update:show', $event)"
  >
    <NForm label-placement="top" label-width="auto">

      <!-- Datos básicos -->
      <div class="grid grid-cols-2 gap-3">
        <NFormItem label="Nombre *" class="col-span-2">
          <NInput v-model:value="form.nombre" placeholder="Ej: Sala de Urgencias A" maxlength="150" />
        </NFormItem>

        <NFormItem label="Tipo de lugar">
          <NSelect v-model:value="form.tipoLugar" placeholder="Seleccionar tipo"
            clearable :options="TIPO_LUGAR_OPCIONES" />
        </NFormItem>

        <NFormItem label="Capacidad (personas)">
          <NInputNumber v-model:value="form.capacidad" placeholder="Ej: 30"
            :min="1" style="width: 100%" />
        </NFormItem>

        <NFormItem label="Descripción" class="col-span-2">
          <NInput v-model:value="form.descripcion" type="textarea"
            placeholder="Descripción opcional del lugar..." :rows="2" maxlength="300" />
        </NFormItem>

        <NFormItem label="Estado">
          <NSelect
            :value="form.activo ? 'true' : 'false'"
            placeholder="Estado"
            :options="[
                { label: 'Activo',   value: 'true'  },
                { label: 'Inactivo', value: 'false' },
            ]"
            @update:value="(v: string) => form.activo = v === 'true'"
            />
        </NFormItem>
      </div>

      <NDivider>
        <span class="text-sm text-gray-500">📍 Geofencing (opcional)</span>
      </NDivider>

       <!-- Resumen de coordenadas actuales -->
        <div v-if="form.latitudCentro" class="mb-3 rounded-lg bg-blue-50 p-3 text-sm">
            <div class="flex items-center justify-between">
            <span class="text-blue-700">
                📌 Lat: <strong>{{ form.latitudCentro }}</strong> |
                Lng: <strong>{{ form.longitudCentro }}</strong> |
                Radio: <strong>{{ form.radioMetros }}m</strong>
            </span>
            <NButton size="tiny" type="warning" @click="limpiarGeofencing">
                🗑 Quitar
            </NButton>
            </div>
        </div>

        <div v-else class="mb-3 text-sm text-gray-400">
            Sin geofencing configurado.
        </div>

        <NButton secondary @click="showMapPicker = true">
            🗺 {{ form.latitudCentro ? 'Editar ubicación en mapa' : 'Seleccionar en mapa' }}
        </NButton>

        <!-- Modal de mapa independiente -->
        <MapPickerModal
            v-model:show="showMapPicker"
            :initial-lat="form.latitudCentro"
            :initial-lng="form.longitudCentro"
            :initial-radio="form.radioMetros"
            @selected="onMapSelected"
        />
    </NForm>

    <!-- Footer -->
    <div class="mt-4 flex justify-end gap-2">
      <NButton @click="$emit('update:show', false)">Cancelar</NButton>
      <NButton type="primary" :loading="loading" @click="guardar">
        {{ esEdicion ? 'Actualizar' : 'Crear lugar' }}
      </NButton>
    </div>
  </NModal>
</template>