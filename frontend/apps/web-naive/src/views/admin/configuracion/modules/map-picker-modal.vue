<script lang="ts" setup>
import { nextTick, ref, watch } from 'vue';
import { NAlert, NButton, NInputNumber, NModal } from 'naive-ui';

const props = defineProps<{
  show:         boolean;
  initialLat?:  number | null;
  initialLng?:  number | null;
  initialRadio?: number | null;
  readonly?:    boolean;
  titulo?:      string;
}>();

const emit = defineEmits<{
  (e: 'update:show', val: boolean): void;
  (e: 'selected', data: { lat: number; lng: number; radio: number }): void;
}>();

const selLat   = ref<number | null>(null);
const selLng   = ref<number | null>(null);
const selRadio = ref<number>(100);

let L:        any = null;
let leafletMap:  any = null;
let marker:   any = null;
let circle:   any = null;

// ── Abrir modal ────────────────────────────────────────────────────────
watch(() => props.show, async (val) => {
  if (!val) { destroyMap(); return; }

  selLat.value   = props.initialLat   ?? null;
  selLng.value   = props.initialLng   ?? null;
  selRadio.value = props.initialRadio ?? 100;

  await nextTick();
  setTimeout(initMap, 350); // esperar animación de ESTE modal
});

// ── Inicializar Leaflet ────────────────────────────────────────────────
async function initMap() {
  if (!L) {
    L = (await import('leaflet')).default;
    await import('leaflet/dist/leaflet.css');

    const proto = L.Icon.Default.prototype as any;
    delete proto._getIconUrl;
    L.Icon.Default.mergeOptions({
      iconUrl:       new URL('leaflet/dist/images/marker-icon.png',    import.meta.url).href,
      iconRetinaUrl: new URL('leaflet/dist/images/marker-icon-2x.png', import.meta.url).href,
      shadowUrl:     new URL('leaflet/dist/images/marker-shadow.png',  import.meta.url).href,
    });
  }

  destroyMap();

  const el = document.getElementById('map-picker-el');
  if (!el || !props.show) return;

  const lat  = selLat.value  ?? -11.0233;  // Cobija, Bolivia
  const lng  = selLng.value  ?? -68.7500;
  const zoom = selLat.value ? 18 : 16;

  leafletMap = L.map(el, { preferCanvas: false }).setView([lat, lng], zoom);

  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '© <a href="https://osm.org/copyright">OpenStreetMap</a>',
    maxZoom: 19,
  }).addTo(leafletMap);

  // Recalcular tamaño — el contenedor ya está estable en este punto
  leafletMap.invalidateSize();

  requestAnimationFrame(() => {
    leafletMap?.invalidateSize();

    if (selLat.value && selLng.value) {
      dibujarMarcador(selLat.value, selLng.value, selRadio.value);
    }
  });

  if (!props.readonly) {
    leafletMap.on('click', (e: any) => {
      // Llamar invalidateSize ANTES de leer e.latlng
      leafletMap.invalidateSize();
      selLat.value = parseFloat(e.latlng.lat.toFixed(7));
      selLng.value = parseFloat(e.latlng.lng.toFixed(7));
      dibujarMarcador(selLat.value, selLng.value, selRadio.value);
    });
  }
}

function dibujarMarcador(lat: number, lng: number, radio: number) {
  marker?.remove();
  circle?.remove();

  marker = L.marker([lat, lng])
    .bindPopup(`📍 ${lat.toFixed(7)}, ${lng.toFixed(7)}`)
    .addTo(leafletMap)
    .openPopup();

  if (radio > 0) {
    circle = L.circle([lat, lng], {
      radius:      radio,
      color:       '#3b82f6',
      fillColor:   '#3b82f6',
      fillOpacity: 0.15,
      weight:      2,
    }).addTo(leafletMap);
  }
}

function destroyMap() {
  leafletMap?.remove();
  leafletMap = null;
  marker     = null;
  circle     = null;
}

// Actualizar círculo en tiempo real al cambiar radio
watch(selRadio, (val) => {
  if (circle && val > 0) circle.setRadius(val);
});

function limpiar() {
  selLat.value = null;
  selLng.value = null;
  marker?.remove(); marker = null;
  circle?.remove(); circle = null;
}

function confirmar() {
  if (!selLat.value || !selLng.value) return;
  emit('selected', { lat: selLat.value, lng: selLng.value, radio: selRadio.value });
  emit('update:show', false);
}
</script>

<template>
  <NModal
    :show="show"
    preset="card"
    :title="titulo ?? (readonly ? '🗺 Ver ubicación' : '📍 Seleccionar ubicación')"
    style="width: 700px; max-width: 96vw"
    :mask-closable="false"
    @update:show="$emit('update:show', $event)"
  >
    <NAlert v-if="!readonly" type="info" class="mb-3" :show-icon="false">
      <span class="text-xs">
        Haz clic en el mapa para fijar el centro del lugar.
        Ajusta el radio con el campo inferior.
      </span>
    </NAlert>

    <!-- Contenedor del mapa — sin scroll, tamaño fijo -->
    <div id="map-picker-el"
        style="height: 460px; width: 100%; border-radius: 8px;
         border: 1px solid #e0e0e0; overflow: hidden;" />

    <!-- Radio + coords -->
    <div class="mt-3 flex flex-wrap items-center gap-3">
      <template v-if="!readonly">
        <span class="text-sm text-gray-600">Radio:</span>
        <NInputNumber
          v-model:value="selRadio"
          :min="10" :max="10000" :step="10"
          style="width: 140px"
          :disabled="!selLat"
        />
        <span class="text-sm text-gray-400">metros</span>
        <NButton v-if="selLat" size="small" type="warning" @click="limpiar">
          🗑 Quitar punto
        </NButton>
      </template>

      <span v-if="selLat" class="ml-auto text-xs text-gray-500">
        Lat: <strong>{{ selLat }}</strong> |
        Lng: <strong>{{ selLng }}</strong> |
        Radio: <strong>{{ selRadio }}m</strong>
      </span>
    </div>

    <div class="mt-4 flex justify-end gap-2">
      <NButton @click="$emit('update:show', false)">
        {{ readonly ? 'Cerrar' : 'Cancelar' }}
      </NButton>
      <NButton
        v-if="!readonly"
        type="primary"
        :disabled="!selLat"
        @click="confirmar"
      >
        ✅ Confirmar ubicación
      </NButton>
    </div>
  </NModal>
</template>