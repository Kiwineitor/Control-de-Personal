<script setup lang="ts">
import type { PersonalActivoMapaDto } from '#/api/sisu/monitoreo';

import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue';

import { NEmpty, NSpin } from 'naive-ui';

import { getPersonalActivoMapa } from '#/api/sisu/monitoreo';
import { useWebSocket } from '#/composables/useWebSocket';

// ── Props ──────────────────────────────────────────────────
defineProps<{ activo: boolean }>();

// ── State ──────────────────────────────────────────────────
const cargando   = ref(false);
const personal   = ref<PersonalActivoMapaDto[]>([]);
const mapContainer = ref<HTMLDivElement | null>(null);

let L:          any = null;
let leafletMap: any = null;
let markers:    Map<number, any> = new Map();
let circulos:   Map<number, any> = new Map();

// ── STOMP: actualizar marcadores en tiempo real ─────────────
const { alertasMonitoreo, presenciaSignal } = useWebSocket();

watch(alertasMonitoreo, async () => {
  await cargarYActualizar();
}, { deep: true });

// Primera señal GPS de una sesión (DESCONOCIDO → conocido) y conexiones/
// desconexiones llegan por /topic/admin/presencia. Recargar para que el
// marcador del usuario aparezca apenas activa el GPS, sin esperar una alerta.
watch(presenciaSignal, async () => {
  await cargarYActualizar();
});

// ── Cargar datos y actualizar mapa ──────────────────────────
async function cargarYActualizar() {
  cargando.value = true;
  try {
    personal.value = await getPersonalActivoMapa();
    if (leafletMap) actualizarMarcadores();
  } catch { /* silencioso */ } finally {
    cargando.value = false;
  }
}

// ── Inicializar Leaflet ─────────────────────────────────────
async function initMap() {
  if (!mapContainer.value) return;
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

  leafletMap = L.map(mapContainer.value).setView([-17.4, -66.1], 14);

  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '© OpenStreetMap',
    maxZoom: 19,
  }).addTo(leafletMap);

  await cargarYActualizar();
}

function colorPorEstado(estado: string): string {
  if (estado === 'DENTRO')    return '#18a058';
  if (estado === 'FUERA')     return '#f08c00';
  return '#888';
}

function iconoPersonal(estado: string) {
  const color = colorPorEstado(estado);
  return L.divIcon({
    className: '',
    html: `<div style="
      width:32px;height:32px;border-radius:50%;
      background:${color};border:3px solid white;
      box-shadow:0 2px 8px rgba(0,0,0,.3);
      display:flex;align-items:center;justify-content:center;
      font-size:14px;color:white;font-weight:700;
    ">👤</div>`,
    iconSize: [32, 32],
    iconAnchor: [16, 16],
  });
}

function actualizarMarcadores() {
  // Agrupar por lugar para dibujar círculos únicos
  const lugaresVistos = new Set<number>();
  let primerLatLng: [number, number] | null = null;

  for (const p of personal.value) {
    // Círculo de geofencing por lugar
    if (p.idLugar && !lugaresVistos.has(p.idLugar)
        && p.latitudLugar && p.longitudLugar && p.radioMetrosLugar) {
      lugaresVistos.add(p.idLugar);
      if (!circulos.has(p.idLugar)) {
        const c = L.circle([p.latitudLugar, p.longitudLugar], {
          radius:      p.radioMetrosLugar,
          color:       '#4a90d9',
          fillColor:   '#4a90d9',
          fillOpacity: 0.1,
          weight:      2,
        }).addTo(leafletMap).bindTooltip(p.nombreLugar ?? 'Lugar', { permanent: false });
        circulos.set(p.idLugar, c);
      }
    }

    // Marcador del personal
    if (p.latitudPersonal && p.longitudPersonal) {
      const latlng: [number, number] = [Number(p.latitudPersonal), Number(p.longitudPersonal)];
      if (!primerLatLng) primerLatLng = latlng;

      const popupContent = `
        <div style="min-width:160px">
          <strong>${p.nombrePersonal}</strong><br/>
          <span style="font-size:12px;color:#666">${p.cargo ?? ''}</span><br/>
          <span style="font-size:12px">
            Estado: <strong style="color:${colorPorEstado(p.estadoZona)}">${p.estadoZona}</strong>
          </span><br/>
          ${p.distanciaMetros ? `<span style="font-size:11px">Distancia: ${Math.round(Number(p.distanciaMetros))}m</span><br/>` : ''}
          ${p.tiempoFueraMinutos ? `<span style="font-size:11px;color:#e03131">Fuera: ${p.tiempoFueraMinutos} min</span>` : ''}
        </div>
      `;

      if (markers.has(p.idSesion)) {
        // Actualizar posición e ícono
        markers.get(p.idSesion)!
          .setLatLng(latlng)
          .setIcon(iconoPersonal(p.estadoZona))
          .setPopupContent(popupContent);
      } else {
        const m = L.marker(latlng, { icon: iconoPersonal(p.estadoZona) })
          .addTo(leafletMap)
          .bindPopup(popupContent);
        markers.set(p.idSesion, m);
      }
    }
  }

  // Eliminar marcadores de sesiones que ya no están activas
  const activosIds = new Set(personal.value.map(p => p.idSesion));
  for (const [id, m] of markers) {
    if (!activosIds.has(id)) {
      leafletMap.removeLayer(m);
      markers.delete(id);
    }
  }

  // Auto-centrar: si hay marcadores nuevos, ajustar vista para verlos todos
  const allLatLngs = [...markers.values()].map(m => m.getLatLng());
  if (allLatLngs.length > 0) {
    const bounds = L.latLngBounds(allLatLngs);
    // Agregar círculos al bounds también
    for (const c of circulos.values()) {
      bounds.extend(c.getBounds());
    }
    leafletMap.fitBounds(bounds, { padding: [40, 40], maxZoom: 16 });
  }
}

// ── Lifecycle ───────────────────────────────────────────────
// Polling de seguridad: refresca el mapa cada 30s aunque no llegue señal WS.
// Cubre el caso donde el GPS llegó al backend antes de que el admin abriera la tab.
let pollTimer: null | ReturnType<typeof setInterval> = null;

onMounted(async () => {
  await nextTick();
  await initMap();
  pollTimer = setInterval(cargarYActualizar, 30_000);
});

onUnmounted(() => {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
  leafletMap?.remove();
  leafletMap = null;
  markers.clear();
  circulos.clear();
});
</script>

<template>
  <div class="mapa-wrapper">
    <div class="mapa-header">
      <span class="mapa-legend">
        <span class="legend-dot" style="background:#18a058"></span> Dentro
        <span class="legend-dot" style="background:#f08c00"></span> Fuera de zona
        <span class="legend-dot" style="background:#888"></span> Sin datos
      </span>
      <span v-if="cargando" style="font-size:12px;color:#888">Actualizando...</span>
      <span v-else style="font-size:12px;color:#888">{{ personal.length }} en turno</span>
    </div>

    <div v-if="personal.length === 0 && !cargando" class="mapa-empty">
      <NEmpty description="Sin personal activo — el mapa se ubicará automáticamente cuando haya turnos en curso" />
    </div>

    <div ref="mapContainer" class="mapa-leaflet"></div>

    <NSpin v-if="cargando && personal.length === 0" class="mapa-spin" />
  </div>
</template>

<style scoped>
.mapa-wrapper  { position: relative; display: flex; flex-direction: column; height: 520px; }
.mapa-header   { display: flex; justify-content: space-between; align-items: center;
                 padding: 6px 4px 10px; font-size: 13px; }
.mapa-legend   { display: flex; align-items: center; gap: 12px; color: #555; }
.legend-dot    { display: inline-block; width: 10px; height: 10px;
                 border-radius: 50%; margin-right: 3px; }
.mapa-leaflet  { flex: 1; border-radius: 12px; overflow: hidden;
                 border: 1.5px solid #e0e0e0; }
.mapa-empty    { position: absolute; inset: 0; display: flex;
                 align-items: center; justify-content: center; z-index: 1; }
.mapa-spin     { position: absolute; inset: 0; display: flex;
                 align-items: center; justify-content: center; }
</style>
