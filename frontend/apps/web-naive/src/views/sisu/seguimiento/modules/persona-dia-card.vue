<script lang="ts" setup>
import { computed } from 'vue';
import { NTag, NTooltip } from 'naive-ui';
import type {
  SesionMonitoreoDto, EventoAbandonoDto, AusenciaJornadaDto,
} from '#/api/sisu/monitoreo';
import type { ActividadJornadaDto } from '#/api/sisu/actividades';
import type { BitacoraEntrada } from '#/api/sisu/bitacora';

interface PersonaAsignada {
  tipo: 'TITULAR' | 'INTERNO';
  id: number;
  idAsignacion: number;
  nombre: string;
  horaInicio: string;
  horaFin: string;
  estado: string;
}

const props = defineProps<{
  persona: PersonaAsignada;
  sesion?: SesionMonitoreoDto;
  ausencias: AusenciaJornadaDto[];
  incidencias: EventoAbandonoDto[];
  bitacoras: BitacoraEntrada[];
  actividades: ActividadJornadaDto[];
}>();

const esTitular = computed(() => props.persona.tipo === 'TITULAR');

// ── Estado GPS ──────────────────────────────────────────────────────────────

const estadoGps = computed(() => {
  if (!props.sesion) return { label: 'Sin sesión', color: '#94a3b8', icon: '⚫' };
  const z = props.sesion.estadoZona;
  if (z === 'DENTRO') return { label: 'En zona', color: '#22c55e', icon: '🟢' };
  if (z === 'FUERA') return { label: 'Fuera de zona', color: '#ef4444', icon: '🔴' };
  return { label: 'Sin señal', color: '#f59e0b', icon: '🟡' };
});

// ── Estado de llegada ───────────────────────────────────────────────────────

const estadoLlegada = computed(() => {
  if (props.ausencias.length > 0) {
    const a = props.ausencias[0]!;
    if (a.finAusencia) return { label: 'Ausente (resuelta)', color: 'default' as const };
    return { label: 'Ausente', color: 'error' as const };
  }
  if (!props.sesion) return { label: 'Sin registro', color: 'warning' as const };
  if (props.sesion.timestampPrimeraPresencia) {
    const horaLlegada = new Date(props.sesion.timestampPrimeraPresencia);
    const parts = props.persona.horaInicio.split(':');
    const hh = Number(parts[0] ?? 0);
    const mm = Number(parts[1] ?? 0);
    const horaEsperada = new Date(horaLlegada);
    horaEsperada.setHours(hh, mm, 0, 0);
    const diffMin = (horaLlegada.getTime() - horaEsperada.getTime()) / 60_000;
    if (diffMin > 5) return { label: `Tardanza ${Math.round(diffMin)}min`, color: 'warning' as const };
    return { label: 'Puntual', color: 'success' as const };
  }
  return { label: 'No llegó', color: 'error' as const };
});

// ── Actividades ─────────────────────────────────────────────────────────────

const resumenActividades = computed(() => {
  const total = props.actividades.length;
  const completadas = props.actividades.filter(a => a.estadoActividad === 'COMPLETADA' || a.estadoActividad === 'VALIDADA').length;
  return { total, completadas };
});

// ── Incidencias ─────────────────────────────────────────────────────────────

const tieneIncidencias = computed(() => props.incidencias.length > 0);

// ── Color card ──────────────────────────────────────────────────────────────

const borderColor = computed(() => {
  if (props.ausencias.length > 0 && !props.ausencias.some(a => a.finAusencia)) return '#ef4444';
  if (tieneIncidencias.value) return '#f59e0b';
  if (estadoLlegada.value.color === 'success') return '#22c55e';
  return '#94a3b8';
});

function formatearHora(h: string): string {
  return h?.substring(0, 5) ?? '--:--';
}

function formatearTiempo(ts: string | null | undefined): string {
  if (!ts) return '--:--';
  const d = new Date(ts);
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

function tipoBadgeColor(tipo: string): { bg: string; color: string } {
  return tipo === 'TITULAR'
    ? { bg: '#fee2e2', color: '#dc2626' }
    : { bg: '#dbeafe', color: '#1d4ed8' };
}
</script>

<template>
  <div
    class="rounded-lg px-3 py-2.5 transition-shadow hover:shadow-sm"
    :style="{ borderLeft: `4px solid ${borderColor}`, background: 'hsl(var(--card))', border: `1px solid hsl(var(--border))`, borderLeftWidth: '4px', borderLeftColor: borderColor }"
  >
    <div class="flex items-center justify-between gap-2 mb-1.5">
      <div class="flex items-center gap-2 min-w-0">
        <span
          class="inline-flex items-center justify-center w-6 h-6 rounded-full text-white text-xs font-bold flex-shrink-0"
          :style="{ background: esTitular ? '#dc2626' : '#1d4ed8' }"
        >
          {{ persona.nombre.charAt(0).toUpperCase() }}
        </span>
        <span class="text-sm font-semibold truncate">{{ persona.nombre }}</span>
        <span
          class="inline-block px-1.5 py-0.5 rounded text-xs font-semibold"
          :style="{ background: tipoBadgeColor(persona.tipo).bg, color: tipoBadgeColor(persona.tipo).color }"
        >
          {{ persona.tipo === 'TITULAR' ? 'Titular' : 'Interno' }}
        </span>
      </div>

      <div class="flex items-center gap-2 flex-shrink-0">
        <NTag :type="estadoLlegada.color" size="small" round :bordered="false">
          {{ estadoLlegada.label }}
        </NTag>
        <NTooltip v-if="sesion" trigger="hover">
          <template #trigger>
            <span class="text-xs">{{ estadoGps.icon }}</span>
          </template>
          GPS: {{ estadoGps.label }}
          <template v-if="sesion.ultimaDistanciaMetros != null">
            · {{ Math.round(sesion.ultimaDistanciaMetros) }}m del puesto
          </template>
        </NTooltip>
      </div>
    </div>

    <div class="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground">
      <span>
        🕐 {{ formatearHora(persona.horaInicio) }} – {{ formatearHora(persona.horaFin) }}
      </span>

      <template v-if="sesion && sesion.timestampPrimeraPresencia">
        <span>
          📍 Llegó {{ formatearTiempo(sesion.timestampPrimeraPresencia) }}
        </span>
      </template>

      <span v-if="resumenActividades.total > 0">
        📋 {{ resumenActividades.completadas }}/{{ resumenActividades.total }} actividades
      </span>

      <span v-if="bitacoras.length > 0">
        📝 {{ bitacoras.length }} bitácora(s)
      </span>

      <span v-if="tieneIncidencias" style="color: #f59e0b; font-weight: 600">
        ⚠️ {{ incidencias.length }} incidencia(s)
      </span>

      <span v-if="sesion?.tiempoFueraMinutos && sesion.tiempoFueraMinutos > 0" style="color: #ef4444">
        ⏱ {{ sesion.tiempoFueraMinutos }}min fuera
      </span>
    </div>
  </div>
</template>
