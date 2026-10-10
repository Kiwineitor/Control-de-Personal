<script lang="ts" setup>
// Card de un turno, colapsable.
//
// La regla de diseño es que el header cerrado alcance para decidir: si no hay
// nada raro el operador no lo abre nunca. Por eso lleva el semáforo de personal,
// la barra de progreso del turno y los avatares — antes había que expandir las
// diez cards y leer diez tablas para encontrar al que faltaba.

import type { PersonalRow, TurnoGroup } from '../data';

import { computed, ref } from 'vue';

import { NButton, NEmpty, NTag } from 'naive-ui';

import { message } from '#/adapter/naive';
import { cambiarMonitoreoJornada } from '#/api/sisu/jornada';

import { TIPO_META } from '../../jornadas/data';
import {
  ESTADO_META, fmtDuracion, fmtHoraCorta, minutosDesde, minutosSinSenal,
  progresoTurno, TURNO_META,
} from '../data';

const props = defineProps<{
  ahora:    Date;
  /** Filtro de estado activo en la barra superior, o null si no hay. */
  filtro:   null | string;
  turno:    TurnoGroup;
}>();

const emit = defineEmits<{
  monitoreoCambiado: [];
  verFicha: [row: PersonalRow];
}>();

// ── Detener / reanudar el control del turno ────────────────────────────────
// Suspende el monitoreo sin tocar la programación: el turno sigue con su horario
// y su gente, pero el backend deja de abrir sesiones, de exigir ubicación y de
// registrar atrasos, faltas o abandonos.

const cambiandoMonitoreo = ref(false);
const pausado = computed(() => props.turno.jornada.monitoreoPausado === true);

async function alternarMonitoreo() {
  cambiandoMonitoreo.value = true;
  try {
    await cambiarMonitoreoJornada(props.turno.jornada.idJornada, !pausado.value);
    message.success(pausado.value
      ? 'Control reanudado: el sistema vuelve a evaluar a quien esté en horario'
      : 'Control detenido: no se registrarán atrasos ni faltas en este turno');
    emit('monitoreoCambiado');
  } catch (error: any) {
    message.error(error?.message ?? 'No se pudo cambiar el control del turno');
  } finally {
    cambiandoMonitoreo.value = false;
  }
}

const abierto = defineModel<boolean>('abierto', { required: true });

const meta      = computed(() => TURNO_META[props.turno.estado]);
const tipoMeta  = computed(() =>
  TIPO_META[props.turno.jornada.tipoJornada] ?? { label: props.turno.jornada.tipoJornada, color: '#94a3b8' },
);
const progreso  = computed(() => progresoTurno(props.turno.referencia, props.ahora));

/** Texto de horario del intervalo de referencia, con marca de nocturno. */
const horarioRef = computed(() => {
  const ref = props.turno.referencia;
  if (!ref) {
    const j = props.turno.jornada;
    return `${j.horaInicio?.slice(0, 5) ?? '?'} – ${j.horaFin?.slice(0, 5) ?? '?'}`;
  }
  const cruza = ref.inicio.getDate() !== ref.fin.getDate();
  return `${fmtHoraCorta(ref.inicio)} – ${fmtHoraCorta(ref.fin)}${cruza ? ' +1d' : ''}`;
});

/** "termina en 45m" / "empieza en 2h 10m" / "cerró 08:00". */
const cuenta = computed(() => {
  const ref = props.turno.referencia;
  if (!ref) return null;
  switch (props.turno.estado) {
    case 'AYER':
    case 'FINALIZADO': {
      return `cerró ${fmtHoraCorta(ref.fin)}`;
    }
    case 'EN_CURSO':
    case 'POR_CERRAR': {
      return `termina en ${fmtDuracion(minutosDesde(props.ahora, ref.fin))}`;
    }
    case 'PROXIMO': {
      return `empieza en ${fmtDuracion(minutosDesde(props.ahora, ref.inicio))}`;
    }
    default: {
      return null;
    }
  }
});

// ── Filtro interno de la card ──────────────────────────────────────────────
const filtroLocal = ref<null | string>(null);

/** El filtro global de la barra superior manda; el local solo afina dentro. */
const filtroEfectivo = computed(() => props.filtro ?? filtroLocal.value);

const personalVisible = computed(() => {
  const f = filtroEfectivo.value;
  const base = f === 'PROBLEMA'
    ? props.turno.personal.filter(p => p.estado === 'AUSENTE' || p.estado === 'SIN_SENAL')
    : (f ? props.turno.personal.filter(p => p.estado === f) : props.turno.personal);
  // El texto GPS se resuelve una vez por fila y no tres veces desde el template.
  return base.map(row => ({ row, gps: gpsTexto(row) }));
});

/** Avatares del header: primero quien tiene problema. */
const avatares = computed(() => props.turno.personal.slice(0, 8));

function iniciales(nombre: string): string {
  return nombre.split(' ').slice(0, 2).map(p => p[0] ?? '').join('').toUpperCase();
}

function chipFiltro(clave: string) {
  filtroLocal.value = filtroLocal.value === clave ? null : clave;
}

/** Detalle GPS de una fila, ya resuelto a texto. */
function gpsTexto(row: PersonalRow): { detalle: null | string; principal: string } {
  if (!row.sesion) return { principal: '📵 Sin sesión', detalle: null };
  const mins = minutosSinSenal(row.sesion);
  const dist = row.sesion.ultimaDistanciaMetros == null
    ? null
    : `${Math.round(Number(row.sesion.ultimaDistanciaMetros))} m al límite`;

  if (row.estado === 'SIN_SENAL') {
    return {
      principal: '📴 Sin señal reciente',
      detalle:   mins == null ? 'Nunca envió GPS' : `última hace ${mins} min`,
    };
  }
  return {
    principal: dist ?? '—',
    detalle:   mins == null ? null : `señal hace ${mins} min`,
  };
}
</script>

<template>
  <div
    class="tc"
    :class="[`tc--${props.turno.estado.toLowerCase()}`, { 'tc--cerrado': !abierto }]"
    :style="{ '--tc-color': meta.color }"
  >
    <!-- ── Header (siempre visible) ──────────────────────────────────────── -->
    <!-- El botón de control va FUERA del <button> colapsable: anidar botones es
         HTML inválido y el clic se comía el toggle en vez de disparar la acción. -->
    <div class="tc__headrow">
    <button class="tc__head" @click="abierto = !abierto">
      <span class="tc__chevron" :class="{ 'tc__chevron--open': abierto }">▸</span>

      <div class="tc__main">
        <!-- Fila 1: estado + tipo + nombre + horario -->
        <div class="tc__linea1">
          <span class="tc__estado">
            <span v-if="props.turno.estado === 'EN_CURSO'" class="tc__dot"></span>
            {{ meta.label }}
          </span>
          <span class="tc__tipo" :style="{ background: tipoMeta.color }">{{ tipoMeta.label }}</span>
          <span class="tc__nombre">
            {{ props.turno.jornada.nombre || `Jornada #${props.turno.jornada.idJornada}` }}
          </span>
          <span class="tc__horario">🕐 {{ horarioRef }}</span>
          <span v-if="cuenta" class="tc__cuenta">{{ cuenta }}</span>
        </div>

        <!-- Fila 2: progreso + semáforo de personal -->
        <div class="tc__linea2">
          <div
            v-if="props.turno.estado === 'EN_CURSO' || props.turno.estado === 'POR_CERRAR'"
            class="tc__barra"
            :title="`${progreso}% del turno transcurrido`"
          >
            <span class="tc__barra-fill" :style="{ width: `${progreso}%` }"></span>
          </div>

          <div class="tc__semaforo">
            <span v-if="props.turno.stats.ausentes > 0" class="tc__sem tc__sem--red">
              🔴 {{ props.turno.stats.ausentes }} fuera
            </span>
            <span v-if="props.turno.stats.sinSenal > 0" class="tc__sem tc__sem--orange">
              ⚠ {{ props.turno.stats.sinSenal }} sin señal
            </span>
            <span class="tc__sem tc__sem--green">
              ✅ {{ props.turno.stats.enZona }} en zona
            </span>
            <span class="tc__sem tc__sem--gray">{{ props.turno.stats.total }} total</span>
          </div>
        </div>
      </div>

      <!-- Avatares -->
      <div class="tc__avatares">
        <span
          v-for="p in avatares"
          :key="`${p.tipo}-${p.idPersona}`"
          class="tc__av"
          :style="{ borderColor: ESTADO_META[p.estado].color }"
          :title="`${p.nombreCompleto} · ${ESTADO_META[p.estado].corto}`"
        >{{ iniciales(p.nombreCompleto) }}</span>
        <span v-if="props.turno.personal.length > avatares.length" class="tc__av tc__av--mas">
          +{{ props.turno.personal.length - avatares.length }}
        </span>
      </div>
    </button>

      <NButton
        class="tc__control"
        size="tiny"
        round
        :loading="cambiandoMonitoreo"
        :type="pausado ? 'primary' : 'default'"
        :title="pausado
          ? 'Reanudar el control: el sistema vuelve a exigir ubicación y a registrar atrasos y faltas'
          : 'Detener el control: el turno sigue, pero deja de exigir ubicación y de registrar atrasos y faltas'"
        @click="alternarMonitoreo"
      >
        {{ pausado ? '▶ Activar control' : '⏸ Detener control' }}
      </NButton>
    </div>

    <!-- ── Cuerpo expandido ──────────────────────────────────────────────── -->
    <div v-if="abierto" class="tc__body">
      <!-- Info de la jornada + filtros internos -->
      <div class="tc__barra-sup">
        <span class="tc__meta-jornada">
          📍 {{ props.turno.jornada.nombreLugar ?? 'Sin lugar' }}
          · 📅 {{ props.turno.jornada.fechaInicio }} → {{ props.turno.jornada.fechaFin }}
        </span>
        <div class="tc__filtros">
          <button
            class="tc__fchip"
            :class="{ 'tc__fchip--on': filtroLocal === 'PROBLEMA' }"
            :disabled="!!props.filtro"
            @click="chipFiltro('PROBLEMA')"
          >
Con problema
</button>
          <button
            class="tc__fchip"
            :class="{ 'tc__fchip--on': filtroLocal === 'EN_ZONA' }"
            :disabled="!!props.filtro"
            @click="chipFiltro('EN_ZONA')"
          >
En zona
</button>
          <button
            v-if="filtroLocal"
            class="tc__fchip tc__fchip--clear"
            @click="filtroLocal = null"
          >
✕
</button>
        </div>
      </div>

      <NEmpty
        v-if="props.turno.personal.length === 0"
        description="Sin personal asignado a este turno"
        size="small"
        class="py-4"
      />
      <NEmpty
        v-else-if="personalVisible.length === 0"
        description="Nadie coincide con el filtro"
        size="small"
        class="py-4"
      />

      <!-- Filas de personal -->
      <div v-else class="tc__lista">
        <div
          v-for="f in personalVisible"
          :key="`${f.row.tipo}-${f.row.idPersona}`"
          class="pf"
          :class="[`pf--${f.row.estado.toLowerCase()}`]"
        >
          <span
            class="pf__av"
            :style="{ borderColor: ESTADO_META[f.row.estado].color, color: ESTADO_META[f.row.estado].color }"
          >{{ iniciales(f.row.nombreCompleto) }}</span>

          <div class="pf__ident">
            <div class="pf__nombre">{{ f.row.nombreCompleto }}</div>
            <div class="pf__sub">
              <NTag size="tiny" round :type="f.row.tipo === 'INTERNO' ? 'info' : 'default'">
                {{ f.row.tipo }}
              </NTag>
              <span v-if="f.row.cargo" class="pf__cargo">{{ f.row.cargo }}</span>
            </div>
          </div>

          <div class="pf__estado">
            <NTag :type="ESTADO_META[f.row.estado].type" size="small" round>
              {{ ESTADO_META[f.row.estado].label }}
            </NTag>
          </div>

          <div class="pf__gps">
            <div class="pf__gps-main">{{ f.gps.principal }}</div>
            <div v-if="f.gps.detalle" class="pf__gps-sub">{{ f.gps.detalle }}</div>
          </div>

          <div class="pf__horario">
            {{ f.row.horaInicio?.slice(0, 5) ?? '—' }} – {{ f.row.horaFin?.slice(0, 5) ?? '—' }}
          </div>

          <div class="pf__acc">
            <NButton size="tiny" secondary @click="emit('verFicha', f.row)">
              👁 Ver ficha
            </NButton>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.tc {
  margin-bottom: 10px;
  overflow: hidden;
  background: hsl(var(--card));
  border: 1px solid hsl(var(--border));
  border-left: 4px solid var(--tc-color);
  border-radius: 12px;
}

/* Los turnos que ya no corren se atenúan, pero vuelven al pasar el mouse */
.tc--finalizado, .tc--ayer, .tc--no_programado { opacity: 0.72; }
.tc--finalizado:hover, .tc--ayer:hover, .tc--no_programado:hover { opacity: 1; }
.tc--en_curso { box-shadow: 0 1px 10px color-mix(in srgb, #18a058 14%, transparent); }

/* ── Header ─────────────────────────────────────────────────────────────── */
/* El header es una fila: el área colapsable ocupa lo que sobra y el control
   de monitoreo queda fijo a la derecha, visible sin tener que expandir. */
.tc__headrow { display: flex; gap: 8px; align-items: center; padding-right: 10px; }
.tc__control { flex-shrink: 0; }

.tc__head {
  display: flex;
  gap: 10px;
  align-items: center;
  flex: 1;
  min-width: 0;
  padding: 9px 12px;
  font: inherit;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: none;
}
.tc__head:hover { background: hsl(var(--accent) / 40%); }

.tc__chevron {
  flex-shrink: 0;
  font-size: 0.8rem;
  color: hsl(var(--muted-foreground));
  transition: transform 0.16s;
}
.tc__chevron--open { transform: rotate(90deg); }

.tc__main   { flex: 1; min-width: 0; }
.tc__linea1 { display: flex; gap: 7px; align-items: center; flex-wrap: wrap; }

.tc__estado {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 2px 7px; border-radius: 4px;
  font-size: 0.66rem; font-weight: 800; letter-spacing: 0.7px;
  color: var(--tc-color);
  background: color-mix(in srgb, var(--tc-color) 14%, transparent);
  border: 1px solid color-mix(in srgb, var(--tc-color) 38%, transparent);
  white-space: nowrap;
}
.tc__dot {
  width: 6px; height: 6px; border-radius: 50%; flex-shrink: 0;
  background: var(--tc-color);
  animation: vj-pulse 2s ease-in-out infinite;
}

.tc__tipo {
  padding: 2px 8px; border-radius: 12px;
  font-size: 0.66rem; font-weight: 700; color: #fff; white-space: nowrap;
}
.tc__nombre   { font-size: 0.9rem; font-weight: 700; color: hsl(var(--foreground)); }
.tc__horario  { font-size: 0.76rem; font-weight: 600; color: hsl(var(--muted-foreground)); font-variant-numeric: tabular-nums; }
.tc__cuenta   { font-size: 0.72rem; font-style: italic; color: var(--tc-color); font-weight: 600; }

.tc__linea2 {
  display: flex; gap: 10px; align-items: center; flex-wrap: wrap;
  margin-top: 5px;
}

.tc__barra {
  position: relative;
  width: 120px; height: 5px;
  overflow: hidden;
  background: hsl(var(--muted));
  border-radius: 3px;
  flex-shrink: 0;
}
.tc__barra-fill {
  position: absolute; top: 0; left: 0; bottom: 0;
  background: var(--tc-color);
  border-radius: 3px;
}

.tc__semaforo { display: flex; gap: 5px; align-items: center; flex-wrap: wrap; }
.tc__sem {
  padding: 1px 7px; border-radius: 10px;
  font-size: 0.68rem; font-weight: 700; white-space: nowrap;
  border: 1px solid transparent;
}
.tc__sem--red    { color: #c92a2a; background: color-mix(in srgb, #e03131 10%, transparent); border-color: color-mix(in srgb, #e03131 28%, transparent); }
.tc__sem--orange { color: #c2410c; background: color-mix(in srgb, #f08c00 10%, transparent); border-color: color-mix(in srgb, #f08c00 28%, transparent); }
.tc__sem--green  { color: #15803d; background: color-mix(in srgb, #18a058 10%, transparent); border-color: color-mix(in srgb, #18a058 25%, transparent); }
.tc__sem--gray   { color: hsl(var(--muted-foreground)); background: hsl(var(--muted)); }

.tc__avatares { display: flex; gap: 3px; flex-shrink: 0; flex-wrap: wrap; max-width: 180px; justify-content: flex-end; }
.tc__av {
  display: flex; align-items: center; justify-content: center;
  width: 25px; height: 25px; border-radius: 50%;
  font-size: 8.5px; font-weight: 800;
  background: hsl(var(--muted));
  color: hsl(var(--foreground));
  border: 2px solid transparent;
}
.tc__av--mas { font-size: 9px; color: hsl(var(--muted-foreground)); border-color: hsl(var(--border)); }

/* ── Cuerpo ─────────────────────────────────────────────────────────────── */
.tc__body {
  padding: 8px 12px 12px;
  border-top: 1px solid hsl(var(--border));
}

.tc__barra-sup {
  display: flex; align-items: center; justify-content: space-between;
  gap: 10px; flex-wrap: wrap; margin-bottom: 8px;
}
.tc__meta-jornada { font-size: 0.7rem; color: hsl(var(--muted-foreground)); }

.tc__filtros { display: flex; gap: 4px; }
.tc__fchip {
  padding: 2px 9px; border-radius: 12px;
  font-size: 0.68rem; font-weight: 600;
  color: hsl(var(--muted-foreground));
  background: transparent;
  border: 1px solid hsl(var(--border));
  cursor: pointer;
}
.tc__fchip:hover:not(:disabled) { background: hsl(var(--accent)); color: hsl(var(--foreground)); }
.tc__fchip:disabled { opacity: 0.4; cursor: not-allowed; }
.tc__fchip--on {
  color: #fff;
  background: #0369a1;
  border-color: #0369a1;
}
.tc__fchip--clear { padding: 2px 7px; }

/* ── Fila de persona ────────────────────────────────────────────────────── */
.tc__lista { display: flex; flex-direction: column; gap: 4px; }

.pf {
  display: grid;
  grid-template-columns: 32px minmax(140px, 1.4fr) minmax(150px, 1fr) minmax(110px, 0.9fr) 92px auto;
  gap: 10px;
  align-items: center;
  padding: 6px 8px;
  border: 1px solid hsl(var(--border));
  border-radius: 8px;
}
.pf--ausente   { background: color-mix(in srgb, #e03131 6%, transparent); border-color: color-mix(in srgb, #e03131 25%, transparent); }
.pf--sin_senal { background: color-mix(in srgb, #f08c00 6%, transparent); border-color: color-mix(in srgb, #f08c00 25%, transparent); }
.pf--no_hoy, .pf--turno_finalizado { opacity: 0.62; }

.pf__av {
  display: flex; align-items: center; justify-content: center;
  width: 32px; height: 32px; border-radius: 50%;
  font-size: 10px; font-weight: 800;
  background: hsl(var(--muted));
  border: 2px solid transparent;
}

.pf__ident { min-width: 0; }
.pf__nombre {
  font-size: 0.85rem; font-weight: 600; color: hsl(var(--foreground));
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.pf__sub    { display: flex; gap: 5px; align-items: center; margin-top: 2px; }
.pf__cargo  { font-size: 0.7rem; color: hsl(var(--muted-foreground)); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.pf__gps-main { font-size: 0.78rem; color: hsl(var(--foreground)); }
.pf__gps-sub  { font-size: 0.68rem; color: hsl(var(--muted-foreground)); }

.pf__horario {
  font-size: 0.76rem; font-weight: 600; color: hsl(var(--muted-foreground));
  font-variant-numeric: tabular-nums; white-space: nowrap;
}

.pf__acc { display: flex; gap: 4px; justify-content: flex-end; }

@keyframes vj-pulse {
  0%, 100% { opacity: 1; }
  50%      { opacity: 0.45; }
}

/* En pantallas angostas la grilla de la fila se apila */
@media (width <= 900px) {
  .pf {
    grid-template-columns: 32px 1fr auto;
    grid-template-areas:
      'av ident acc'
      'av estado estado'
      'av gps horario';
    row-gap: 4px;
  }
  .pf__av       { grid-area: av; }
  .pf__ident    { grid-area: ident; }
  .pf__estado   { grid-area: estado; }
  .pf__gps      { grid-area: gps; }
  .pf__horario  { grid-area: horario; }
  .pf__acc      { grid-area: acc; }
}
</style>
