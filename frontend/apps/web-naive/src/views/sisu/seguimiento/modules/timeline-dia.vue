<script lang="ts" setup>
import { computed } from 'vue';
import type {
  SesionMonitoreoDto, EventoAbandonoDto, AusenciaJornadaDto,
} from '#/api/sisu/monitoreo';
import type { ActividadJornadaDto } from '#/api/sisu/actividades';
import type { BitacoraEntrada } from '#/api/sisu/bitacora';
import PersonaDiaCard from './persona-dia-card.vue';

interface PersonaAsignada {
  tipo: 'TITULAR' | 'INTERNO';
  id: number;
  idAsignacion: number;
  nombre: string;
  horaInicio: string;
  horaFin: string;
  estado: string;
}

interface DiaData {
  fecha: string;
  info: { diaSemana: string; dia: number; mes: string; anio: number; label: string };
  personas: PersonaAsignada[];
  sesiones: SesionMonitoreoDto[];
  ausencias: AusenciaJornadaDto[];
  incidencias: EventoAbandonoDto[];
  bitacoras: BitacoraEntrada[];
  actividades: ActividadJornadaDto[];
}

const props = defineProps<{ dia: DiaData }>();

function sesionDe(tipo: string, id: number): SesionMonitoreoDto | undefined {
  return props.dia.sesiones.find(s => {
    if (tipo === 'TITULAR') return s.tipoPersonal === 'TITULAR' && s.idPersonal === id;
    return s.tipoPersonal === 'INTERNO' && s.idPersonal === id;
  });
}

function ausenciasDe(nombre: string): AusenciaJornadaDto[] {
  return props.dia.ausencias.filter(a => a.nombrePersonal === nombre);
}

function incidenciasDe(_tipo: string, _id: number, nombre: string): EventoAbandonoDto[] {
  return props.dia.incidencias.filter(i => i.nombrePersonal === nombre);
}

function bitacorasDe(nombre: string): BitacoraEntrada[] {
  return props.dia.bitacoras.filter(b => b.nombrePersonal === nombre);
}

function actividadesDe(tipo: string, idAsignacion: number): ActividadJornadaDto[] {
  return props.dia.actividades.filter(a => {
    if (tipo === 'TITULAR') return a.asignadoA === 'TITULAR' && a.idTitularJornada === idAsignacion;
    return a.asignadoA === 'INTERNO' && a.idInternoJornada === idAsignacion;
  });
}

const resumen = computed(() => {
  const p = props.dia.personas.length;
  const conSesion = props.dia.sesiones.length;
  const ausentes = props.dia.ausencias.length;
  const incidencias = props.dia.incidencias.length;
  return { total: p, conSesion, ausentes, incidencias };
});
</script>

<template>
  <div class="space-y-2 py-2">
    <div v-if="dia.personas.length === 0" class="text-xs text-muted-foreground py-4 text-center">
      No hay personal asignado este día
    </div>

    <div v-for="persona in dia.personas" :key="`${persona.tipo}-${persona.id}`">
      <PersonaDiaCard
        :persona="persona"
        :sesion="sesionDe(persona.tipo, persona.id)"
        :ausencias="ausenciasDe(persona.nombre)"
        :incidencias="incidenciasDe(persona.tipo, persona.id, persona.nombre)"
        :bitacoras="bitacorasDe(persona.nombre)"
        :actividades="actividadesDe(persona.tipo, persona.idAsignacion)"
      />
    </div>

    <div
      v-if="dia.personas.length > 0"
      class="flex gap-4 pt-1 text-xs text-muted-foreground"
    >
      <span>📋 {{ resumen.total }} asignados</span>
      <span v-if="resumen.conSesion > 0">📡 {{ resumen.conSesion }} con GPS</span>
      <span v-if="resumen.ausentes > 0" style="color: #ef4444">
        ❌ {{ resumen.ausentes }} ausente(s)
      </span>
      <span v-if="resumen.incidencias > 0" style="color: #f59e0b">
        ⚠️ {{ resumen.incidencias }} incidencia(s)
      </span>
    </div>
  </div>
</template>
