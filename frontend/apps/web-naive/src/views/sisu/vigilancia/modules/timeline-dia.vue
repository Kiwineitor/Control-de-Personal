<script lang="ts" setup>
// Franja horaria 00–24h del día de hoy con un carril por turno.
//
// Responde de un vistazo la pregunta que la lista de cards no contesta: qué
// turno corrió, cuál corre y cuál viene, y cómo se solapan entre sí. Los
// nocturnos se recortan al borde del día y se marcan con una flecha, para que
// se vea que vienen de ayer o siguen mañana sin mentir sobre el horario.

import type { Intervalo, TurnoGroup } from '../data';

import { computed } from 'vue';

import { TIPO_META } from '../../jornadas/data';
import { fmtHoraCorta, TURNO_META } from '../data';

const props = defineProps<{
  ahora:   Date;
  turnos:  TurnoGroup[];
}>();

const emit = defineEmits<{ irA: [idJornada: number] }>();

const HORAS_GUIA = [0, 3, 6, 9, 12, 15, 18, 21, 24];

const inicioDia = computed(() => new Date(
  props.ahora.getFullYear(), props.ahora.getMonth(), props.ahora.getDate(), 0, 0, 0, 0,
));
const finDia = computed(() => new Date(inicioDia.value.getTime() + 86_400_000));

/** Posición porcentual de un instante dentro del día de hoy. */
function pct(d: Date): number {
  const t = (d.getTime() - inicioDia.value.getTime()) / 86_400_000;
  return Math.min(100, Math.max(0, t * 100));
}

const ahoraPct = computed(() => pct(props.ahora));

interface Bloque {
  /** El intervalo sigue después de medianoche de hoy. */
  cortaDerecha: boolean;
  /** El intervalo venía de antes de las 00:00 de hoy. */
  cortaIzquierda: boolean;
  enCurso:  boolean;
  izq:      number;
  ancho:    number;
  titulo:   string;
}

interface Carril {
  bloques:  Bloque[];
  color:    string;
  estado:   TurnoGroup['estado'];
  idJornada: number;
  nombre:   string;
  tipoColor: string;
}

/** Intervalos que se solapan con el día de hoy, recortados a sus bordes. */
function bloquesDe(intervalos: Intervalo[]): Bloque[] {
  const out: Bloque[] = [];
  for (const i of intervalos) {
    if (i.fin <= inicioDia.value || i.inicio >= finDia.value) continue;
    const izq   = pct(i.inicio);
    const ancho = pct(i.fin) - izq;
    // Un bloque de ancho 0 (turno que cierra exactamente a las 00:00) no se ve;
    // se le da un mínimo para que quede clickeable.
    out.push({
      izq,
      ancho:          Math.max(ancho, 0.6),
      cortaIzquierda: i.inicio < inicioDia.value,
      cortaDerecha:   i.fin > finDia.value,
      enCurso:        i.inicio <= props.ahora && props.ahora <= i.fin,
      titulo:         `${fmtHoraCorta(i.inicio)} – ${fmtHoraCorta(i.fin)}`,
    });
  }
  return out;
}

const carriles = computed<Carril[]>(() => {
  return props.turnos
    .map((t) => {
      // Solo interesan los intervalos que toquen hoy; los de mañana no se dibujan.
      const relevantes = t.intervalos.filter(
        i => i.fin > inicioDia.value && i.inicio < finDia.value,
      );
      return {
        idJornada: t.jornada.idJornada,
        nombre:    t.jornada.nombre || `Jornada #${t.jornada.idJornada}`,
        estado:    t.estado,
        color:     TURNO_META[t.estado].color,
        tipoColor: TIPO_META[t.jornada.tipoJornada]?.color ?? '#94a3b8',
        bloques:   bloquesDe(relevantes),
      };
    })
    .filter(c => c.bloques.length > 0);
});
</script>

<template>
  <div v-if="carriles.length > 0" class="tl">
    <div class="tl__head">
      <span class="tl__titulo">🕐 Línea de tiempo del día</span>
      <span class="tl__ahora">ahora {{ fmtHoraCorta(props.ahora) }}</span>
    </div>

    <!-- Guía de horas -->
    <div class="tl__guia">
      <span
        v-for="h in HORAS_GUIA"
        :key="h"
        class="tl__hora"
        :style="{ left: `${(h / 24) * 100}%` }"
      >{{ String(h % 24).padStart(2, '0') }}</span>
    </div>

    <div class="tl__pista">
      <!-- Rejilla cada 3h -->
      <span
        v-for="h in HORAS_GUIA"
        :key="`g-${h}`"
        class="tl__rejilla"
        :style="{ left: `${(h / 24) * 100}%` }"
      ></span>

      <!-- Marcador de ahora, sobre todos los carriles -->
      <span class="tl__now" :style="{ left: `${ahoraPct}%` }">
        <span class="tl__now-dot"></span>
      </span>

      <!-- Un carril por turno -->
      <div v-for="c in carriles" :key="c.idJornada" class="tl__carril">
        <button
          v-for="(b, bi) in c.bloques"
          :key="bi"
          class="tl__bloque"
          :class="{
            'tl__bloque--curso': b.enCurso,
            'tl__bloque--izq': b.cortaIzquierda,
            'tl__bloque--der': b.cortaDerecha,
          }"
          :style="{
            left: `${b.izq}%`,
            width: `${b.ancho}%`,
            background: c.tipoColor,
            borderColor: c.color,
          }"
          :title="`${c.nombre} · ${b.titulo}${b.cortaIzquierda ? ' (viene de ayer)' : ''}${b.cortaDerecha ? ' (sigue mañana)' : ''}`"
          @click="emit('irA', c.idJornada)"
        >
          <span v-if="b.cortaIzquierda" class="tl__flecha">‹</span>
          <span class="tl__bloque-txt">{{ c.nombre }}</span>
          <span v-if="b.cortaDerecha" class="tl__flecha">›</span>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.tl {
  padding: 10px 14px 12px;
  margin-bottom: 12px;
  background: hsl(var(--card));
  border: 1px solid hsl(var(--border));
  border-radius: 12px;
}

.tl__head {
  display: flex; align-items: center; justify-content: space-between;
  gap: 8px; margin-bottom: 6px;
}
.tl__titulo { font-size: 0.78rem; font-weight: 700; color: hsl(var(--foreground)); }
.tl__ahora  { font-size: 0.72rem; color: hsl(var(--muted-foreground)); font-variant-numeric: tabular-nums; }

/* Guía de horas */
.tl__guia { position: relative; height: 14px; }
.tl__hora {
  position: absolute; top: 0;
  font-size: 0.62rem; color: hsl(var(--muted-foreground));
  transform: translateX(-50%);
  font-variant-numeric: tabular-nums;
}

/* Pista con los carriles */
.tl__pista {
  position: relative;
  padding: 4px 0;
  border-top: 1px solid hsl(var(--border));
}

.tl__rejilla {
  position: absolute; top: 0; bottom: 0;
  width: 1px;
  background: hsl(var(--border));
  opacity: 0.6;
}

.tl__now {
  position: absolute; top: 0; bottom: 0;
  z-index: 3;
  width: 2px;
  background: #e03131;
  transform: translateX(-1px);
}
.tl__now-dot {
  position: absolute; top: -3px; left: -3px;
  width: 8px; height: 8px; border-radius: 50%;
  background: #e03131;
  box-shadow: 0 0 0 3px color-mix(in srgb, #e03131 25%, transparent);
}

.tl__carril { position: relative; height: 22px; margin: 3px 0; }

.tl__bloque {
  position: absolute; top: 0;
  display: flex; align-items: center; gap: 2px;
  height: 22px; padding: 0 6px;
  overflow: hidden;
  font-size: 0.66rem; font-weight: 700; line-height: 1;
  color: #fff;
  cursor: pointer;
  border: 1.5px solid transparent;
  border-radius: 5px;
  opacity: 0.55;
  transition: opacity 0.15s, transform 0.15s;
}
.tl__bloque:hover { opacity: 1; transform: translateY(-1px); }
.tl__bloque--curso {
  z-index: 2;
  opacity: 1;
  box-shadow: 0 1px 6px rgb(0 0 0 / 20%);
}
/* Recorte en los bordes del día: el turno continúa fuera de la vista */
.tl__bloque--izq { border-top-left-radius: 0; border-bottom-left-radius: 0; border-left-style: dashed; }
.tl__bloque--der { border-top-right-radius: 0; border-bottom-right-radius: 0; border-right-style: dashed; }

.tl__bloque-txt {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tl__flecha { flex-shrink: 0; font-size: 0.8rem; opacity: 0.9; }
</style>
