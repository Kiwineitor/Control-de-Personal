<script lang="ts" setup>
// Ficha de una persona vigilada: estado GPS, incidencias, notificaciones que ya
// se le enviaron y su rol de turno del mes. Se comparte entre Vigilancia en Vivo
// y Mapa GPS en Vivo, que antes tenían cada una su propio panel duplicado.
//
// Es un modal ancho y no un cajón lateral: en 440px las incidencias y el
// calendario quedaban en una columna angosta y había que leerlos en vertical.
//
// El componente carga sus propios datos al abrirse; los llamadores solo pasan a
// quién mirar.

import type { FichaTarget } from '../data';

import type { DiaTurnoDto, IncidenciaPersonalDto, RolTurnoDto } from '#/api/sisu/monitoreo';
import type { NotificacionHistorial } from '#/api/sisu/notificaciones';

import { computed, ref, watch } from 'vue';

import { NEmpty, NModal, NSpin, NTag } from 'naive-ui';

import {
  getIncidenciasPersonal, getRolTurnoByJornada, getRolTurnoByTitularJornada,
} from '#/api/sisu/monitoreo';
import { getNotificacionesPersonal } from '#/api/sisu/notificaciones';

import { fmtHora, isoFecha, minutosSinSenal } from '../data';

const props = defineProps<{ target: FichaTarget | null }>();
const visible = defineModel<boolean>('show', { required: true });

// ── Datos cargados por la ficha ────────────────────────────────────────────
const incidencias   = ref<IncidenciaPersonalDto[]>([]);
const cargandoInc   = ref(false);
const notificaciones = ref<NotificacionHistorial[]>([]);
const cargandoNotif = ref(false);
const rolTurno      = ref<null | RolTurnoDto>(null);
const cargandoRol   = ref(false);

const HOY         = isoFecha(new Date());
const DIAS_SEMANA = ['L', 'M', 'X', 'J', 'V', 'S', 'D'];
const MESES_ES    = [
  'Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
  'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre',
];

const ABANDONO_TIPOS = new Set(['ABANDONO_ZONA', 'AUSENCIA_SENAL', 'LOGOUT_EN_TURNO', 'SALIDA_TEMPRANA']);

const resumenInc = computed(() => {
  const r = { abandono: 0, atraso: 0, falta: 0, total: incidencias.value.length };
  for (const i of incidencias.value) {
    if (i.tipo === 'ATRASO') r.atraso++;
    else if (i.tipo === 'FALTA') r.falta++;
    else if (ABANDONO_TIPOS.has(i.tipo)) r.abandono++;
  }
  return r;
});

/** Color del borde izquierdo de cada incidencia, por severidad. */
function colorInc(tipo: string): string {
  if (ABANDONO_TIPOS.has(tipo)) return '#e03131';
  if (tipo === 'ATRASO') return '#f08c00';
  if (tipo === 'FALTA') return '#c2410c';
  return '#94a3b8';
}

function tipoTag(tipo: string): 'default' | 'error' | 'warning' {
  if (ABANDONO_TIPOS.has(tipo)) return 'error';
  if (tipo === 'ATRASO' || tipo === 'FALTA') return 'warning';
  return 'default';
}

function fmtFechaHora(ts: null | string) {
  if (!ts) return '—';
  try {
    return new Date(ts).toLocaleString('es', {
      day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit',
    });
  } catch { return ts; }
}

function fmtFechaLarga(fecha: string) {
  return new Date(`${fecha}T00:00:00`)
    .toLocaleDateString('es', { weekday: 'short', day: 'numeric', month: 'short' });
}

// ── Carga ──────────────────────────────────────────────────────────────────
async function cargarIncidencias(t: FichaTarget) {
  incidencias.value = [];
  cargandoInc.value = true;
  try {
    incidencias.value = await getIncidenciasPersonal(t.tipo, t.idPersona) ?? [];
  } catch {
    incidencias.value = [];
  } finally {
    cargandoInc.value = false;
  }
}

async function cargarNotificaciones(t: FichaTarget) {
  notificaciones.value = [];
  cargandoNotif.value  = true;
  try {
    notificaciones.value = await getNotificacionesPersonal(t.tipo, t.idPersona) ?? [];
  } catch {
    // Persona sin usuario asociado o sin permisos: la sección queda vacía.
    notificaciones.value = [];
  } finally {
    cargandoNotif.value = false;
  }
}

async function cargarRolTurno(t: FichaTarget) {
  rolTurno.value = null;
  // Los internos no tienen rol de turno: se saltea la llamada.
  if (t.tipo !== 'TITULAR') return;

  cargandoRol.value = true;
  const mes  = new Date().getMonth() + 1;
  const anio = new Date().getFullYear();
  try {
    // Con la asignación se pide directo; si no, se filtra el de la jornada.
    const lista = t.idAsignacion
      ? await getRolTurnoByTitularJornada(t.idAsignacion)
      : (t.idJornada ? await getRolTurnoByJornada(t.idJornada) : []);

    rolTurno.value = lista.find(
      rt => rt.activo && rt.mes === mes && rt.anio === anio
        && (t.idAsignacion ? true : rt.idTitular === t.idPersona),
    ) ?? null;
  } catch {
    rolTurno.value = null;
  } finally {
    cargandoRol.value = false;
  }
}

watch([visible, () => props.target], ([abierto, t]) => {
  if (!abierto || !t) return;
  cargarIncidencias(t);
  cargarNotificaciones(t);
  cargarRolTurno(t);
}, { immediate: true });

// ── Rol turno derivado ─────────────────────────────────────────────────────
const diasTurnoSet = computed(() => {
  const set = new Set<string>();
  rolTurno.value?.dias.forEach(d => set.add(d.fecha));
  return set;
});

const diasTurnoMap = computed(() => {
  const map = new Map<string, DiaTurnoDto>();
  rolTurno.value?.dias.forEach(d => map.set(d.fecha, d));
  return map;
});

const proximosDias = computed(() => {
  if (!rolTurno.value) return [];
  return rolTurno.value.dias
    .filter(d => d.fecha >= HOY)
    .toSorted((a, b) => a.fecha.localeCompare(b.fecha))
    .slice(0, 5);
});

// ── Calendario del mes ─────────────────────────────────────────────────────
interface CalDia { fecha: null | string; num: null | number }

function generarCalendario(anio: number, mes: number): CalDia[][] {
  const primer = new Date(anio, mes - 1, 1);
  const total  = new Date(anio, mes, 0).getDate();
  const offset = (primer.getDay() + 6) % 7; // lunes=0 … domingo=6

  const semanas: CalDia[][] = [];
  let semana: CalDia[] = Array.from({ length: offset }, () => ({ num: null, fecha: null }));

  for (let d = 1; d <= total; d++) {
    const mm = String(mes).padStart(2, '0');
    const dd = String(d).padStart(2, '0');
    semana.push({ num: d, fecha: `${anio}-${mm}-${dd}` });
    if (semana.length === 7) { semanas.push(semana); semana = []; }
  }
  if (semana.length > 0) {
    while (semana.length < 7) semana.push({ num: null, fecha: null });
    semanas.push(semana);
  }
  return semanas;
}

const calendario = computed(() => {
  const rt    = rolTurno.value;
  const ahora = new Date();
  const anio  = rt ? rt.anio : ahora.getFullYear();
  const mes   = rt ? rt.mes  : ahora.getMonth() + 1;
  return { semanas: generarCalendario(anio, mes), anio, mes };
});

// ── GPS ────────────────────────────────────────────────────────────────────
const GPS_META: Record<string, { label: string; type: 'default' | 'success' | 'warning' }> = {
  DENTRO:      { label: '✅ En zona',        type: 'success' },
  FUERA:       { label: '⚠️ Fuera de zona',  type: 'warning' },
  DESCONOCIDO: { label: '📵 Sin datos GPS',  type: 'default' },
};

const senalMin = computed(() => minutosSinSenal(props.target?.sesion ?? null));
</script>

<template>
  <NModal
    v-model:show="visible"
    preset="card"
    style="width: min(1080px, 94vw)"
    :bordered="false"
    size="small"
    :segmented="{ content: true }"
  >
    <template #header>
      <div v-if="props.target" class="fh-head">
        <div class="fh-avatar">{{ props.target.nombreCompleto.charAt(0) }}</div>
        <div class="fh-head__info">
          <div class="fh-head__nombre">{{ props.target.nombreCompleto }}</div>
          <div class="fh-head__meta">
            <NTag :type="props.target.tipo === 'INTERNO' ? 'info' : 'default'" size="tiny" round>
              {{ props.target.tipo }}
            </NTag>
            <span v-if="props.target.cargo" class="fh-muted">{{ props.target.cargo }}</span>
            <span v-if="props.target.nombreJornada" class="fh-muted">
              · {{ props.target.nombreJornada }}
            </span>
            <span v-if="props.target.nombreLugar" class="fh-muted">
              · 📍 {{ props.target.nombreLugar }}
            </span>
          </div>
        </div>
      </div>
    </template>

    <div v-if="props.target" class="fh-body">
      <!-- ── Estado GPS: tira de datos a lo ancho ─────────────────────────── -->
      <div class="fh-sec-title">📡 Estado GPS actual</div>
      <div v-if="!props.target.sesion" class="fh-vacio">Sin sesión de monitoreo abierta</div>
      <div v-else class="fh-gps">
        <div class="fh-kv">
          <span class="fh-kv__lbl">Estado</span>
          <NTag :type="GPS_META[props.target.sesion.estadoZona]?.type ?? 'default'" size="small" round>
            {{ GPS_META[props.target.sesion.estadoZona]?.label ?? props.target.sesion.estadoZona }}
          </NTag>
        </div>
        <div class="fh-kv">
          <span class="fh-kv__lbl">Distancia al límite</span>
          <span class="fh-kv__val">
            {{ props.target.sesion.ultimaDistanciaMetros != null
              ? `${Math.round(Number(props.target.sesion.ultimaDistanciaMetros))} m`
              : '—' }}
          </span>
        </div>
        <div class="fh-kv">
          <span class="fh-kv__lbl">Tiempo fuera</span>
          <span
            class="fh-kv__val"
            :class="{ 'fh-kv__val--alerta': props.target.sesion.tiempoFueraMinutos > 0 }"
          >
            {{ props.target.sesion.tiempoFueraMinutos > 0
              ? `${props.target.sesion.tiempoFueraMinutos} min`
              : '—' }}
          </span>
        </div>
        <div class="fh-kv">
          <span class="fh-kv__lbl">Última señal</span>
          <span class="fh-kv__val">
            <template v-if="props.target.sesion.ultimaActualizacion">
              {{ new Date(props.target.sesion.ultimaActualizacion)
                .toLocaleTimeString('es', { hour: '2-digit', minute: '2-digit' }) }}
              <span v-if="senalMin != null" class="fh-muted">· hace {{ senalMin }} min</span>
            </template>
            <template v-else>Sin señal</template>
          </span>
        </div>
        <div class="fh-kv">
          <span class="fh-kv__lbl">Inicio de sesión</span>
          <span class="fh-kv__val">
            {{ new Date(props.target.sesion.timestampInicio)
              .toLocaleTimeString('es', { hour: '2-digit', minute: '2-digit' }) }}
          </span>
        </div>
        <div class="fh-kv">
          <span class="fh-kv__lbl">Alerta activa</span>
          <NTag :type="props.target.sesion.tieneEventoAbierto ? 'error' : 'success'" size="small" round>
            {{ props.target.sesion.tieneEventoAbierto ? '🔴 Sí' : '✅ No' }}
          </NTag>
        </div>
      </div>

      <!-- ── Dos columnas: incidencias | notificaciones ───────────────────── -->
      <div class="fh-cols">
        <!-- Incidencias -->
        <section class="fh-col">
          <div class="fh-sec-title">⚠️ Incidencias (faltas, atrasos, abandonos)</div>
          <NSpin :show="cargandoInc">
            <div class="fh-chips">
              <span class="fh-chip fh-chip--red"><b>{{ resumenInc.abandono }}</b> abandonos</span>
              <span class="fh-chip fh-chip--orange"><b>{{ resumenInc.atraso }}</b> atrasos</span>
              <span class="fh-chip fh-chip--neutral"><b>{{ resumenInc.falta }}</b> faltas</span>
            </div>
            <div v-if="incidencias.length === 0 && !cargandoInc" class="fh-vacio">
              Sin incidencias registradas
            </div>
            <div v-else class="fh-lista">
              <div
                v-for="i in incidencias.slice(0, 12)"
                :key="i.idEvento"
                class="fh-item"
                :style="{ '--fh-acento': colorInc(i.tipo) }"
              >
                <div class="fh-item__head">
                  <NTag size="tiny" :type="tipoTag(i.tipo)" round>{{ i.tipoLabel }}</NTag>
                  <span class="fh-item__fecha">{{ fmtFechaHora(i.timestampSalida) }}</span>
                </div>
                <div class="fh-item__body">
                  <span v-if="i.minutosIncidencia != null">{{ i.minutosIncidencia }} min</span>
                  <span v-else-if="i.tiempoFueraMinutos > 0">{{ i.tiempoFueraMinutos }} min fuera</span>
                  <span class="fh-muted">· {{ i.nombreLugar ?? '—' }}</span>
                  <NTag
                    v-if="i.estadoJustificacion && i.estadoJustificacion !== 'NO_REQUERIDA'"
                    size="tiny" :bordered="false" class="fh-item__just"
                  >
{{ i.estadoJustificacionLabel }}
</NTag>
                </div>
              </div>
              <div v-if="incidencias.length > 12" class="fh-mas">
                +{{ incidencias.length - 12 }} más
              </div>
            </div>
          </NSpin>
        </section>

        <!-- Notificaciones enviadas -->
        <section class="fh-col">
          <div class="fh-sec-title">📣 Notificaciones enviadas</div>
          <NSpin :show="cargandoNotif">
            <div v-if="notificaciones.length === 0 && !cargandoNotif" class="fh-vacio">
              Todavía no se le envió ninguna notificación personal
            </div>
            <div v-else class="fh-lista">
              <div
                v-for="n in notificaciones"
                :key="n.idNotificacion"
                class="fh-item"
                :style="{ '--fh-acento': n.leida ? '#18a058' : '#1c7ed6' }"
              >
                <div class="fh-item__head">
                  <span class="fh-notif__titulo">{{ n.titulo }}</span>
                  <span class="fh-item__fecha">{{ fmtFechaHora(n.timestamp) }}</span>
                </div>
                <div class="fh-item__body">{{ n.mensaje }}</div>
                <div class="fh-item__pie">
                  <NTag size="tiny" round :type="n.leida ? 'success' : 'info'">
                    {{ n.leida ? '✓ Leída' : '• Sin leer' }}
                  </NTag>
                  <span class="fh-muted">{{ n.tipo }}</span>
                  <span v-if="n.remitenteUsername" class="fh-muted">
                    · por {{ n.remitenteUsername }}
                  </span>
                </div>
              </div>
            </div>
          </NSpin>
        </section>
      </div>

      <!-- ── Rol turno ────────────────────────────────────────────────────── -->
      <NSpin :show="cargandoRol">
        <div v-if="rolTurno" class="fh-cols">
          <section class="fh-col">
            <div class="fh-sec-title">⏭ Próximos días de turno</div>
            <div class="fh-kv fh-kv--inline">
              <span class="fh-kv__lbl">Horario jornada</span>
              <span class="fh-kv__val">
                {{ fmtHora(rolTurno.horaInicioJornada) ?? '—' }} – {{ fmtHora(rolTurno.horaFinJornada) ?? '—' }}
                <span class="fh-muted">· {{ rolTurno.dias.length }} días asignados</span>
              </span>
            </div>
            <div v-if="proximosDias.length === 0" class="fh-vacio">
              No hay días de turno pendientes este mes
            </div>
            <div v-else class="fh-lista">
              <div
                v-for="d in proximosDias"
                :key="d.fecha"
                class="fh-item fh-item--fila"
                :style="{ '--fh-acento': d.fecha === HOY ? '#18a058' : '#64748b' }"
              >
                <span class="fh-prox__fecha">{{ fmtFechaLarga(d.fecha) }}</span>
                <span class="fh-prox__hora">
                  <template v-if="fmtHora(d.horaInicio) && fmtHora(d.horaFin)">
                    {{ fmtHora(d.horaInicio) }} – {{ fmtHora(d.horaFin) }}
                  </template>
                  <template v-else-if="rolTurno.horaInicioJornada">
                    {{ fmtHora(rolTurno.horaInicioJornada) }} – {{ fmtHora(rolTurno.horaFinJornada) }}
                  </template>
                  <template v-else>—</template>
                </span>
                <NTag v-if="d.fecha === HOY" type="success" size="tiny" round>Hoy</NTag>
              </div>
            </div>
          </section>

          <section class="fh-col">
            <div class="fh-sec-title">
              📅 {{ MESES_ES[calendario.mes - 1] }} {{ calendario.anio }}
            </div>
            <div class="cal-grid cal-grid--header">
              <span v-for="d in DIAS_SEMANA" :key="d" class="cal-cell cal-cell--head">{{ d }}</span>
            </div>
            <div v-for="(semana, si) in calendario.semanas" :key="si" class="cal-grid">
              <div
                v-for="(dia, di) in semana"
                :key="di"
                class="cal-cell"
                :class="[
                  !dia.fecha ? 'cal-cell--empty' : '',
                  dia.fecha && diasTurnoSet.has(dia.fecha) ? 'cal-cell--turno' : '',
                  dia.fecha === HOY ? 'cal-cell--hoy' : '',
                  dia.fecha && dia.fecha < HOY ? 'cal-cell--pasado' : '',
                ]"
              >
                <span v-if="dia.num">{{ dia.num }}</span>
                <span
                  v-if="dia.fecha && diasTurnoMap.get(dia.fecha)?.horaInicio"
                  class="cal-cell__dot"
                  :title="`${fmtHora(diasTurnoMap.get(dia.fecha)?.horaInicio)} – ${fmtHora(diasTurnoMap.get(dia.fecha)?.horaFin)}`"
                ></span>
              </div>
            </div>
            <div class="cal-leyenda">
              <span class="cal-ley"><span class="cal-ley__box cal-ley__box--turno"></span> Día de turno</span>
              <span class="cal-ley"><span class="cal-ley__box cal-ley__box--hoy"></span> Hoy</span>
              <span class="cal-ley"><span class="cal-ley__dot"></span> Horario custom</span>
            </div>
            <div v-if="rolTurno.observaciones" class="fh-obs">💬 {{ rolTurno.observaciones }}</div>
          </section>
        </div>

        <NEmpty
          v-else-if="!cargandoRol"
          :description="props.target.tipo === 'INTERNO'
            ? 'Los internos no tienen rol de turno asignado'
            : 'Sin rol turno para este mes'"
          size="small"
          class="py-4"
        />
      </NSpin>
    </div>
  </NModal>
</template>

<style scoped>
/* El modal crece con el contenido; el scroll vive acá adentro y no en la página */
.fh-body { max-height: 74vh; overflow-y: auto; padding-right: 4px; }

/* ── Cabecera ───────────────────────────────────────────────────────────── */
.fh-head { display: flex; gap: 12px; align-items: center; }
.fh-avatar {
  display: flex; align-items: center; justify-content: center;
  width: 44px; height: 44px; border-radius: 50%; flex-shrink: 0;
  font-size: 1.2rem; font-weight: 800;
  color: hsl(var(--primary));
  background: hsl(var(--primary) / 12%);
  border: 2px solid hsl(var(--primary) / 30%);
}
.fh-head__info   { min-width: 0; }
.fh-head__nombre { font-size: 1rem; font-weight: 700; color: hsl(var(--foreground)); }
.fh-head__meta   { display: flex; gap: 5px; align-items: center; flex-wrap: wrap; margin-top: 2px; }

.fh-muted { font-size: 0.75rem; color: hsl(var(--muted-foreground)); }

.fh-sec-title {
  margin-bottom: 8px;
  font-size: 0.74rem; font-weight: 700; letter-spacing: 0.5px;
  color: hsl(var(--muted-foreground));
  text-transform: uppercase;
}

.fh-vacio {
  padding: 14px 4px;
  font-size: 0.8rem; text-align: center;
  color: hsl(var(--muted-foreground));
}

/* ── Tira de estado GPS ─────────────────────────────────────────────────── */
.fh-gps {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 10px 18px;
  padding: 10px 14px;
  margin-bottom: 16px;
  background: hsl(var(--muted) / 45%);
  border: 1px solid hsl(var(--border));
  border-radius: 10px;
}
.fh-kv { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.fh-kv--inline { flex-direction: row; gap: 8px; align-items: baseline; margin-bottom: 8px; }
.fh-kv__lbl { font-size: 0.7rem; color: hsl(var(--muted-foreground)); }
.fh-kv__val { font-size: 0.86rem; font-weight: 600; color: hsl(var(--foreground)); }
.fh-kv__val--alerta { color: #e03131; font-weight: 700; }

/* ── Dos columnas ───────────────────────────────────────────────────────── */
.fh-cols {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 18px;
  margin-bottom: 16px;
}
.fh-col { min-width: 0; }

/* ── Chips de resumen ───────────────────────────────────────────────────── */
.fh-chips { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 10px; }
.fh-chip {
  padding: 3px 10px; border-radius: 20px;
  font-size: 0.74rem;
  color: hsl(var(--foreground));
  background: hsl(var(--muted) / 60%);
  border: 1px solid hsl(var(--border));
}
.fh-chip b { font-size: 0.9rem; }
.fh-chip--red     { color: #c92a2a; border-color: color-mix(in srgb, #e03131 40%, transparent); }
.fh-chip--orange  { color: #c2410c; border-color: color-mix(in srgb, #f08c00 40%, transparent); }
.fh-chip--neutral { color: hsl(var(--muted-foreground)); }

/* ── Listas (incidencias / notificaciones / próximos días) ──────────────── */
/* Sin relleno de color: el tipo se marca con una barra lateral, así el texto
   siempre conserva el contraste del tema. */
.fh-lista {
  display: flex; flex-direction: column; gap: 6px;
  max-height: 300px; overflow-y: auto;
}
.fh-item {
  padding: 7px 10px;
  background: transparent;
  border: 1px solid hsl(var(--border));
  border-left: 3px solid var(--fh-acento, hsl(var(--border)));
  border-radius: 7px;
}
.fh-item--fila { display: flex; gap: 8px; align-items: center; }
.fh-item__head { display: flex; gap: 8px; align-items: center; justify-content: space-between; }
.fh-item__fecha { font-size: 0.71rem; color: hsl(var(--muted-foreground)); white-space: nowrap; }
.fh-item__body { margin-top: 3px; font-size: 0.8rem; color: hsl(var(--foreground)); }
.fh-item__pie  { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; margin-top: 5px; }
.fh-item__just { margin-left: 6px; }

.fh-notif__titulo {
  font-size: 0.82rem; font-weight: 700; color: hsl(var(--foreground));
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

.fh-prox__fecha { flex: 1; font-size: 0.82rem; font-weight: 600; color: hsl(var(--foreground)); text-transform: capitalize; }
.fh-prox__hora  { font-size: 0.78rem; color: hsl(var(--muted-foreground)); font-variant-numeric: tabular-nums; }

.fh-mas { font-size: 0.74rem; font-style: italic; text-align: center; color: hsl(var(--muted-foreground)); }

.fh-obs {
  padding: 8px 12px; margin-top: 10px;
  font-size: 0.78rem; font-style: italic;
  color: hsl(var(--muted-foreground));
  background: hsl(var(--muted) / 45%);
  border-radius: 8px;
}

/* ── Calendario ─────────────────────────────────────────────────────────── */
.cal-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 3px; margin-bottom: 3px; }
.cal-grid--header { margin-bottom: 4px; }
.cal-cell {
  position: relative;
  display: flex; align-items: center; justify-content: center;
  height: 30px; border-radius: 6px;
  font-size: 0.78rem; font-weight: 500;
  color: hsl(var(--foreground));
}
.cal-cell--head { font-size: 0.66rem; font-weight: 700; color: hsl(var(--muted-foreground)); text-transform: uppercase; }
.cal-cell--empty { opacity: 0; pointer-events: none; }
.cal-cell--turno {
  font-weight: 700;
  background: hsl(var(--muted));
  border: 1px solid hsl(var(--border));
}
.cal-cell--hoy {
  color: #fff !important;
  background: #18a058 !important;
  border-color: #18a058 !important;
  font-weight: 800;
}
.cal-cell--pasado { opacity: 0.45; }
.cal-cell__dot {
  position: absolute; right: 3px; bottom: 3px;
  width: 5px; height: 5px; border-radius: 50%; background: #f08c00;
}

.cal-leyenda {
  display: flex; gap: 12px; flex-wrap: wrap;
  padding-top: 8px; margin-top: 8px;
  font-size: 0.7rem; color: hsl(var(--muted-foreground));
  border-top: 1px solid hsl(var(--border));
}
.cal-ley { display: flex; gap: 4px; align-items: center; }
.cal-ley__box { width: 12px; height: 12px; border-radius: 3px; flex-shrink: 0; background: hsl(var(--muted)); border: 1px solid hsl(var(--border)); }
.cal-ley__box--hoy { background: #18a058; border-color: #18a058; }
.cal-ley__dot { width: 7px; height: 7px; border-radius: 50%; background: #f08c00; flex-shrink: 0; }
</style>
