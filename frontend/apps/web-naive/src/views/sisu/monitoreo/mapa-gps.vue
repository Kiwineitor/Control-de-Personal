<script lang="ts" setup>
// Mapa GPS en Vivo — antes era la pestaña "GPS en vivo" dentro de Vigilancia en
// Vivo. Se separó a su propia ruta porque las dos pestañas cargaban los mismos
// datos con dos intervalos de 20s independientes, y porque el mapa necesita el
// alto de la pantalla que la lista de turnos le disputaba.
//
// La ficha lateral de persona se comparte con Vigilancia en Vivo.

import type { FichaTarget } from '../vigilancia/data';

import type { EventoAbandonoDto, PersonalEnTurnoDto, RolTurnoDto, SesionMonitoreoDto, UsuarioConectadoDto } from '#/api/sisu/monitoreo';

import { computed, h, onMounted, onUnmounted, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';

import {
  NButton, NCard, NDataTable, NEmpty, NGrid, NGridItem,
  NInput, NModal, NSpace, NSpin, NTag,
} from 'naive-ui';

import { message } from '#/adapter/naive';
import {
  getAlertasActivas, getPersonalEnTurnoAhora, getRolTurnoByJornada,
  getSesionesActivas, getUsuariosConectados, resolverAlerta,
} from '#/api/sisu/monitoreo';
import { useWebSocket } from '#/composables/useWebSocket';

import FichaPersona from '../vigilancia/modules/ficha-persona.vue';
import MapaMonitoreo from './modules/mapa-monitoreo.vue';

// ── Estado principal ───────────────────────────────────────────────────────
const sesiones          = ref<SesionMonitoreoDto[]>([]);
const sesionesEnTurno   = ref<Map<string, PersonalEnTurnoDto>>(new Map());
const alertas           = ref<EventoAbandonoDto[]>([]);
// Solo la primera carga tapa la tabla; los refrescos de 20s y las señales del
// WebSocket no deben devolver la vista al estado de spinner mientras se lee.
const cargandoInicial   = ref(true);
const rolTurnoIdx       = ref<Map<number, RolTurnoDto>>(new Map());
const usuariosConectados = ref<UsuarioConectadoDto[]>([]);

const statsConexion = computed(() => ({
  total:  usuariosConectados.value.length,
  conGps: usuariosConectados.value.filter(u => u.tieneGps).length,
  sinGps: usuariosConectados.value.filter(u => !u.tieneGps).length,
  fuera:  usuariosConectados.value.filter(u => u.estadoZona === 'FUERA').length,
}));

// ── WebSocket: actualización instantánea — sin polling ────────────────────
const { alertasMonitoreo, presenciaSignal } = useWebSocket();
watch(alertasMonitoreo, () => cargarDatos(), { deep: true });
watch(presenciaSignal, () => cargarDatos());

// ── Derived state ──────────────────────────────────────────────────────────
const fueraDeZona = computed(() =>
  alertas.value
    .filter(a => !a.resuelto && (a.estado === 'FUERA_ZONA' || a.estado === 'ABANDONO_CONFIRMADO'))
    .toSorted((a, b) => b.tiempoFueraMinutos - a.tiempoFueraMinutos),
);

const contadores = computed(() => ({
  total:    sesiones.value.length,
  dentro:   sesiones.value.filter(s => s.estadoZona === 'DENTRO').length,
  fuera:    sesiones.value.filter(s => s.estadoZona === 'FUERA').length,
  abandono: fueraDeZona.value.filter(a => a.estado === 'ABANDONO_CONFIRMADO').length,
}));

const alertaPorSesion = computed(() => {
  const map = new Map<number, EventoAbandonoDto>();
  for (const a of alertas.value) {
    if (a.idSesion && !a.resuelto
      && (a.estado === 'FUERA_ZONA' || a.estado === 'ABANDONO_CONFIRMADO')) {
      map.set(a.idSesion, a);
    }
  }
  return map;
});

// ── Carga de datos ─────────────────────────────────────────────────────────
async function cargarDatos() {
  try {
    const [enTurno, sesActivas, a, conectados] = await Promise.all([
      getPersonalEnTurnoAhora(),
      getSesionesActivas(),
      getAlertasActivas(),
      getUsuariosConectados().catch(() => [] as UsuarioConectadoDto[]),
    ]);

    usuariosConectados.value = conectados;
    alertas.value = a;

    // Mapa de personal en turno por clave compuesta tipo+id
    const enTurnoMap = new Map<string, PersonalEnTurnoDto>();
    for (const p of enTurno) {
      enTurnoMap.set(`${p.tipoPersonal}-${p.idPersonal}`, p);
    }
    sesionesEnTurno.value = enTurnoMap;

    // Construir mapa de sesiones GPS activas por tipo+idPersonal.
    // Clave compuesta: idTitular e idInterno son espacios separados (ambos
    // arrancan en 1) y colisionarían en un mapa solo numérico, haciendo que un
    // interno "herede" la sesión GPS de un titular con el mismo id.
    const sesMap = new Map<string, SesionMonitoreoDto>();
    for (const s of sesActivas) {
      if (s.idPersonal != null) sesMap.set(`${s.tipoPersonal}-${s.idPersonal}`, s);
    }

    // Mostrar TODOS los que tienen turno vigente ahora:
    // — si tienen sesión GPS: usar datos reales
    // — si no: sintetizar un DTO con estadoZona=DESCONOCIDO
    const merged: SesionMonitoreoDto[] = enTurno.map((p: PersonalEnTurnoDto) => {
      const sesion = p.idPersonal == null ? undefined : sesMap.get(`${p.tipoPersonal}-${p.idPersonal}`);
      if (sesion) return sesion;
      return {
        idSesion:                  -(p.idPersonal ?? 0),   // negativo = sin sesión GPS
        nombrePersonal:            p.nombrePersonal,
        cargo:                     p.cargo,
        tipoPersonal:              p.tipoPersonal,
        idPersonal:                p.idPersonal,
        idJornada:                 p.idJornada,
        nombreJornada:             p.nombreJornada,
        idLugar:                   p.idLugar,
        nombreLugar:               p.nombreLugar,
        estadoZona:                'DESCONOCIDO',
        timestampInicio:           new Date().toISOString(),
        timestampPrimeraPresencia: null,
        ultimaActualizacion:       null,
        tiempoFueraMinutos:        0,
        ultimaLatitud:             null,
        ultimaLongitud:            null,
        ultimaDistanciaMetros:     null,
        tieneEventoAbierto:        false,
      };
    });

    sesiones.value = merged;
    cargarRolesTurno(merged).catch(() => {});
  } catch (error: any) {
    message.error(error?.message ?? 'Error al cargar el personal en turno');
  } finally {
    cargandoInicial.value = false;
  }
}

// El rol de turno del mes no cambia entre refrescos de 20s; sin este freno se
// disparaba una petición por jornada en cada ciclo y en cada señal del WebSocket.
const TTL_ROLES_MS = 5 * 60 * 1000;
let rolesCargadosEn = 0;

async function cargarRolesTurno(lista: SesionMonitoreoDto[]) {
  if (Date.now() - rolesCargadosEn < TTL_ROLES_MS) return;

  const ids = [...new Set(
    lista.filter(s => s.tipoPersonal === 'TITULAR' && s.idJornada).map(s => s.idJornada!),
  )];
  if (ids.length === 0) return;

  const resultados = await Promise.allSettled(ids.map(id => getRolTurnoByJornada(id)));
  const mes  = new Date().getMonth() + 1;
  const anio = new Date().getFullYear();
  const mapa = new Map<number, RolTurnoDto>();

  for (const r of resultados) {
    if (r.status === 'fulfilled') {
      for (const rt of r.value) {
        if (rt.activo && rt.mes === mes && rt.anio === anio) mapa.set(rt.idTitular, rt);
      }
    }
  }
  rolTurnoIdx.value = mapa;
  rolesCargadosEn   = Date.now();
}

// ── Resolver alerta ────────────────────────────────────────────────────────
const modalResolver  = ref(false);
const alertaResolver = ref<EventoAbandonoDto | null>(null);
const obsResolver    = ref('');
const resolviendoId  = ref<null | number>(null);

function abrirResolver(a: EventoAbandonoDto) {
  alertaResolver.value = a;
  obsResolver.value    = '';
  modalResolver.value  = true;
}

async function confirmarResolver() {
  if (!alertaResolver.value) return;
  resolviendoId.value = alertaResolver.value.idEvento;
  try {
    await resolverAlerta(alertaResolver.value.idEvento, obsResolver.value || undefined);
    message.success('Alerta resuelta');
    modalResolver.value = false;
    await cargarDatos();
  } catch (error: any) {
    message.error(error?.message ?? 'Error al resolver');
  } finally {
    resolviendoId.value = null;
  }
}

// ── Ficha lateral compartida ───────────────────────────────────────────────
const fichaVisible = ref(false);
const fichaTarget  = ref<FichaTarget | null>(null);

function abrirDetalle(row: SesionMonitoreoDto) {
  if (row.idPersonal == null) return;
  fichaTarget.value = {
    idPersona:      row.idPersonal,
    tipo:           row.tipoPersonal,
    nombreCompleto: row.nombrePersonal,
    cargo:          row.cargo,
    // Las filas sintetizadas (idSesion negativo) no tienen sesión GPS real.
    sesion:         row.idSesion > 0 ? row : null,
    idJornada:      row.idJornada,
    nombreJornada:  row.nombreJornada,
    nombreLugar:    row.nombreLugar,
  };
  fichaVisible.value = true;
}

// ── Helpers de horario ─────────────────────────────────────────────────────
const HOY = new Date().toISOString().slice(0, 10);

function parseMinutos(hora: null | string | undefined): null | number {
  if (!hora) return null;
  const parts = hora.slice(0, 5).split(':').map(Number);
  return (parts[0] ?? 0) * 60 + (parts[1] ?? 0);
}

function estaEnVentanaHoraria(iniMin: null | number, finMin: null | number, actual: number): boolean {
  if (iniMin == null || finMin == null) return false;
  if (finMin < iniMin) return actual >= iniMin || actual <= finMin;
  return actual >= iniMin && actual <= finMin;
}

function estaEnHorario(p: SesionMonitoreoDto): boolean {
  const enTurno = sesionesEnTurno.value.get(`${p.tipoPersonal}-${p.idPersonal}`);
  if (!enTurno?.horaInicio || !enTurno?.horaFin) return true;
  const ahoraMins = new Date().getHours() * 60 + new Date().getMinutes();
  return estaEnVentanaHoraria(parseMinutos(enTurno.horaInicio), parseMinutos(enTurno.horaFin), ahoraMins);
}

function chipDia(d: { fecha: string }) {
  const num    = Number.parseInt(d.fecha.slice(8), 10);
  const hoy    = d.fecha === HOY;
  const pasado = d.fecha < HOY;
  return h('span', {
    class: ['dia-chip', hoy ? 'dia-chip--hoy' : (pasado ? 'dia-chip--pasado' : 'dia-chip--futuro')],
    title: d.fecha,
  }, String(num));
}

// ── Columnas de la tabla ───────────────────────────────────────────────────
const columnas = computed(() => [
  {
    title: 'Personal', key: 'nombrePersonal', minWidth: 200, fixed: 'left' as const,
    render: (row: SesionMonitoreoDto) => h('div', { class: 'cell-personal' }, [
      h('span', { class: 'cell-personal__nombre' }, row.nombrePersonal),
      h('div', { class: 'cell-personal__meta' }, [
        h(NTag, { size: 'tiny', round: true, type: row.tipoPersonal === 'INTERNO' ? 'info' : 'default' },
          () => row.tipoPersonal),
        row.cargo ? h('span', { class: 'cell-muted' }, row.cargo) : null,
      ]),
    ]),
  },
  {
    title: 'Jornada', key: 'nombreJornada', width: 155,
    render: (row: SesionMonitoreoDto) => h('span', { class: 'cell-sm' }, row.nombreJornada ?? '—'),
  },
  {
    title: 'Lugar asignado', key: 'nombreLugar', width: 160,
    render: (row: SesionMonitoreoDto) => h('span', { class: 'cell-sm' }, row.nombreLugar ?? '—'),
  },
  {
    title: 'Horario', key: '_horario', width: 140, align: 'center' as const,
    render: (row: SesionMonitoreoDto) => {
      const enTurno = sesionesEnTurno.value.get(`${row.tipoPersonal}-${row.idPersonal}`);
      const ini = enTurno?.horaInicio ?? null;
      const fin = enTurno?.horaFin ?? null;
      if (!ini || !fin) return h('span', { class: 'cell-muted' }, '—');
      const enHorario = estaEnHorario(row);
      return h('div', { class: 'cell-horario' }, [
        h('div', { class: 'cell-horario__rango' }, `${ini.slice(0, 5)} – ${fin.slice(0, 5)}`),
        h(NTag, {
          type: enHorario ? 'success' : 'default',
          size: 'tiny',
          round: true,
          class: enHorario ? '' : 'cell-horario__off',
        }, () => enHorario ? '🟢 En turno' : '⏸ Fuera de horario'),
      ]);
    },
  },
  {
    title: 'GPS', key: 'estadoZona', width: 115, align: 'center' as const,
    render: (row: SesionMonitoreoDto) => {
      if (!estaEnHorario(row)) {
        return h(NTag, { type: 'default', size: 'small', round: true }, () => '⏸ Fuera de horario');
      }
      // idSesion negativo = sin sesión GPS activa (sintetizado desde PersonalEnTurnoDto)
      if (row.idSesion < 0) {
        return h(NTag, { type: 'default', size: 'small', round: true }, () => '📵 Sin GPS');
      }
      const cfg: Record<string, { label: string; type: 'default' | 'success' | 'warning' }> = {
        DENTRO:      { label: '✅ En zona',   type: 'success' },
        FUERA:       { label: '⚠️ Fuera',     type: 'warning' },
        DESCONOCIDO: { label: '❓ Sin datos', type: 'default' },
      };
      const c = cfg[row.estadoZona] ?? { label: row.estadoZona, type: 'default' as const };
      return h(NTag, { type: c.type, size: 'small', round: true }, () => c.label);
    },
  },
  {
    title: 'T. fuera', key: 'tiempoFueraMinutos', width: 110, align: 'center' as const,
    render: (row: SesionMonitoreoDto) => {
      if (row.tiempoFueraMinutos <= 0) return h('span', { class: 'cell-muted' }, '—');
      const abandono = alertaPorSesion.value.get(row.idSesion)?.estado === 'ABANDONO_CONFIRMADO';
      return h('div', { class: 'cell-timer' }, [
        h('span', { class: abandono ? 'timer--red' : 'timer--orange' }, `${row.tiempoFueraMinutos} min`),
        h(NTag, { type: abandono ? 'error' : 'warning', size: 'tiny', round: true },
          () => abandono ? '🚨 Abandono' : '⚠️ Fuera'),
      ]);
    },
  },
  {
    title: 'Distancia', key: 'ultimaDistanciaMetros', width: 100, align: 'center' as const,
    render: (row: SesionMonitoreoDto) => {
      if (row.ultimaDistanciaMetros == null) return h('span', { class: 'cell-muted' }, '—');
      return h('span', {
        class: row.estadoZona === 'FUERA' ? 'dist--fuera' : 'cell-sm',
      }, `${Math.round(Number(row.ultimaDistanciaMetros))} m`);
    },
  },
  {
    title: 'Días turno (mes)', key: '_dias', minWidth: 185,
    render: (row: SesionMonitoreoDto) => {
      if (row.tipoPersonal !== 'TITULAR' || !row.idPersonal)
        return h('span', { class: 'cell-muted' }, 'Solo titulares');
      const rt = rolTurnoIdx.value.get(row.idPersonal);
      if (!rt || rt.dias.length === 0)
        return h('span', { class: 'cell-muted' }, 'Sin rol turno');
      const sorted  = rt.dias.toSorted((a, b) => a.fecha.localeCompare(b.fecha));
      const mostrar = sorted.slice(0, 10);
      const resto   = sorted.length - mostrar.length;
      return h('div', { class: 'dias-grid' }, [
        ...mostrar.map(d => chipDia(d)),
        resto > 0 ? h('span', { class: 'dias-grid__mas' }, `+${resto}`) : null,
      ]);
    },
  },
  {
    title: 'Alerta', key: 'tieneEventoAbierto', width: 90, align: 'center' as const,
    render: (row: SesionMonitoreoDto) =>
      row.tieneEventoAbierto
        ? h(NTag, { type: 'error', size: 'small', round: true }, () => '🔴 Sí')
        : h(NTag, { type: 'success', size: 'small', round: true }, () => '✅ No'),
  },
  {
    title: 'Última señal', key: 'ultimaActualizacion', width: 110,
    render: (row: SesionMonitoreoDto) =>
      row.ultimaActualizacion
        ? h('span', { class: 'cell-xs cell-muted' },
            new Date(row.ultimaActualizacion).toLocaleTimeString('es', { hour: '2-digit', minute: '2-digit' }))
        : h('span', { class: 'cell-xs cell-muted' }, 'Sin señal'),
  },
  // Acciones — botón Ver siempre + Resolver si hay alerta
  {
    title: 'Acciones', key: '_acc', width: 120, align: 'center' as const, fixed: 'right' as const,
    render: (row: SesionMonitoreoDto) => {
      const alerta = alertaPorSesion.value.get(row.idSesion);
      return h('div', { class: 'acc-col' }, [
        h(NButton, {
          size: 'tiny', secondary: true,
          onClick: () => abrirDetalle(row),
        }, () => '👁 Ver ficha'),
        alerta
          ? h(NButton, {
              size: 'tiny', type: 'primary', ghost: true,
              loading: resolviendoId.value === alerta.idEvento,
              onClick: () => abrirResolver(alerta),
            }, () => '✓ Resolver')
          : null,
      ]);
    },
  },
]);

function rowClassName(row: SesionMonitoreoDto) {
  const alerta = alertaPorSesion.value.get(row.idSesion);
  if (alerta?.estado === 'ABANDONO_CONFIRMADO') return 'row--abandono';
  if (row.estadoZona === 'FUERA') return 'row--fuera';
  if (!estaEnHorario(row)) return 'row--fuera-horario';
  return '';
}

// Polling de seguridad: refresca cada 20s aunque no llegue señal WS.
// Cubre el caso donde el admin abre la página DESPUÉS del primer ping GPS.
let pollTimer: null | ReturnType<typeof setInterval> = null;

onMounted(() => {
  cargarDatos();
  pollTimer = setInterval(cargarDatos, 20_000);
});

onUnmounted(() => {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
});
</script>

<template>
  <Page auto-content-height>
    <!-- Cabecera ─────────────────────────────────────────────────────────── -->
    <div class="mg-header">
      <div>
        <h2 class="mg-title">Mapa GPS en Vivo</h2>
        <p class="mg-sub">Ubicación del personal en turno · actualizaciones instantáneas vía WebSocket</p>
      </div>
    </div>

    <!-- ── Panel usuarios conectados ahora ──────────────────────────────── -->
    <div class="conexion-bar mb-4">
      <div class="conexion-bar__chip conexion-bar__chip--blue">
        <span class="conexion-dot conexion-dot--pulse"></span>
        <strong>{{ statsConexion.total }}</strong>
        <span>en línea</span>
      </div>
      <div class="conexion-bar__chip conexion-bar__chip--green">
        <span class="conexion-dot" style="background: #22c55e"></span>
        <strong>{{ statsConexion.conGps }}</strong>
        <span>con GPS</span>
      </div>
      <div v-if="statsConexion.sinGps > 0" class="conexion-bar__chip conexion-bar__chip--gray">
        <span class="conexion-dot" style="background: #94a3b8"></span>
        <strong>{{ statsConexion.sinGps }}</strong>
        <span>sin GPS</span>
      </div>
      <div v-if="statsConexion.fuera > 0" class="conexion-bar__chip conexion-bar__chip--orange">
        <span class="conexion-dot" style="background: #f97316"></span>
        <strong>{{ statsConexion.fuera }}</strong>
        <span>fuera de zona</span>
      </div>

      <!-- Lista compacta de quién está conectado -->
      <div v-if="usuariosConectados.length > 0" class="conexion-bar__lista">
        <span
          v-for="u in usuariosConectados"
          :key="u.username"
          class="conexion-avatar"
          :class="{
            'conexion-avatar--gps': u.tieneGps && u.estadoZona === 'DENTRO',
            'conexion-avatar--fuera': u.tieneGps && u.estadoZona === 'FUERA',
            'conexion-avatar--sin': !u.tieneGps,
          }"
          :title="`${u.nombre} · GPS: ${u.estadoZona}${u.ultimaSenal ? ` · Última señal: ${new Date(u.ultimaSenal).toLocaleTimeString('es', { hour: '2-digit', minute: '2-digit' })}` : ''}`"
        >
          {{ u.nombre.split(' ').slice(0, 2).map((p: string) => p[0]).join('').toUpperCase() }}
        </span>
      </div>
    </div>

    <!-- Mapa de ubicación en vivo ──────────────────────────────────────── -->
    <NCard size="small" class="mb-4 mapa-card">
      <template #header>
        <div class="mapa-header">
          <span>🗺 Ubicación en Vivo</span>
          <span class="mapa-header__meta">Solo personal dentro del horario de turno</span>
        </div>
      </template>
      <div class="overflow-hidden rounded-lg" style="min-height: 320px; border: 1px solid hsl(var(--border))">
        <MapaMonitoreo :activo="true" />
      </div>
    </NCard>

    <!-- Contadores ───────────────────────────────────────────────────────── -->
    <NGrid :cols="4" :x-gap="12" class="mb-4" responsive="screen" :collapsed-cols="2">
      <NGridItem>
        <NCard size="small" class="stat-card stat-card--violet">
          <div class="stat-num" style="color: #6366f1">{{ contadores.total }}</div>
          <div class="stat-lbl">En turno</div>
        </NCard>
      </NGridItem>
      <NGridItem>
        <NCard size="small" class="stat-card stat-card--green">
          <div class="stat-num" style="color: #18a058">{{ contadores.dentro }}</div>
          <div class="stat-lbl">En zona</div>
        </NCard>
      </NGridItem>
      <NGridItem>
        <NCard size="small" class="stat-card stat-card--orange">
          <div class="stat-num" style="color: #f08c00">{{ contadores.fuera }}</div>
          <div class="stat-lbl">Fuera</div>
        </NCard>
      </NGridItem>
      <NGridItem>
        <NCard size="small" class="stat-card stat-card--red">
          <div class="stat-num" style="color: #e03131">{{ contadores.abandono }}</div>
          <div class="stat-lbl">Abandonos</div>
        </NCard>
      </NGridItem>
    </NGrid>

    <!-- Fuera de zona ────────────────────────────────────────────────────── -->
    <template v-if="fueraDeZona.length > 0">
      <div class="fuera-header mb-2">
        <span class="fuera-titulo">⚠️ Fuera de zona</span>
        <NTag type="error" size="tiny" round>{{ fueraDeZona.length }}</NTag>
        <span class="fuera-sub">· se ocultan al regresar</span>
      </div>
      <NGrid :cols="3" :x-gap="12" :y-gap="10" class="mb-4" responsive="screen" :collapsed-cols="1">
        <NGridItem v-for="a in fueraDeZona" :key="a.idEvento">
          <div
            class="alerta-card"
            :class="[a.estado === 'ABANDONO_CONFIRMADO' ? 'alerta-card--abandono' : 'alerta-card--fuera']"
          >
            <div class="alerta-card__body">
              <div
                class="alerta-avatar"
                :class="[a.estado === 'ABANDONO_CONFIRMADO' ? 'alerta-avatar--red' : 'alerta-avatar--orange']"
              >
👤
</div>
              <div class="alerta-info">
                <div class="alerta-nombre">{{ a.nombrePersonal }}</div>
                <div class="alerta-sub">{{ a.cargo ?? a.tipoPersonal }} · {{ a.nombreLugar ?? 'Sin lugar' }}</div>
                <NSpace size="small" class="mt-1" wrap>
                  <NTag :type="a.estado === 'ABANDONO_CONFIRMADO' ? 'error' : 'warning'" size="tiny" round>
                    {{ a.estado === 'ABANDONO_CONFIRMADO' ? '🚨 Abandono confirmado' : '⚠️ Fuera de zona' }}
                  </NTag>
                </NSpace>
              </div>
              <div
                class="alerta-timer"
                :class="[a.estado === 'ABANDONO_CONFIRMADO' ? 'alerta-timer--red' : 'alerta-timer--orange']"
              >
                <span class="alerta-timer__num">{{ a.tiempoFueraMinutos }}</span>
                <span class="alerta-timer__lbl">min fuera</span>
              </div>
            </div>
            <div class="alerta-card__footer">
              <NButton size="tiny" type="primary" :loading="resolviendoId === a.idEvento" @click="abrirResolver(a)">
                ✓ Resolver
              </NButton>
            </div>
          </div>
        </NGridItem>
      </NGrid>
    </template>

    <!-- Tabla personal en turno ─────────────────────────────────────────── -->
    <NCard size="small">
      <template #header>
        <div class="tabla-header">
          <span>📋 Personal en Turno</span>
          <span class="tabla-header__meta">
            {{ sesiones.length }} en turno ·
            {{ sesiones.filter(s => s.idSesion > 0).length }} con GPS activo · live WebSocket
          </span>
        </div>
      </template>
      <NSpin :show="cargandoInicial">
        <NEmpty v-if="sesiones.length === 0 && !cargandoInicial" description="No hay personal en turno" class="py-10" />
        <NDataTable
          v-else
          :data="sesiones"
          :columns="columnas"
          size="small"
          :bordered="false"
          :striped="true"
          :row-class-name="rowClassName"
          :scroll-x="1280"
          :max-height="520"
        />
      </NSpin>
    </NCard>

    <!-- Ficha lateral compartida ─────────────────────────────────────────── -->
    <FichaPersona v-model:show="fichaVisible" :target="fichaTarget" />

    <!-- Modal resolver alerta ────────────────────────────────────────────── -->
    <NModal v-model:show="modalResolver" preset="card" title="Resolver alerta" style="width: 420px">
      <p style="margin-bottom: 12px; font-size: 14px">
        ¿Marcar como resuelta la alerta de <strong>{{ alertaResolver?.nombrePersonal }}</strong>?
      </p>
      <NInput v-model:value="obsResolver" type="textarea" :rows="3" placeholder="Observaciones (opcional)..." />
      <template #footer>
        <NSpace justify="end">
          <NButton @click="modalResolver = false">Cancelar</NButton>
          <NButton type="primary" :loading="!!resolviendoId" @click="confirmarResolver">Confirmar</NButton>
        </NSpace>
      </template>
    </NModal>
  </Page>
</template>

<style scoped>
/* ── Layout general ─────────────────────────────────────────────────────── */
.mg-header {
  display: flex; align-items: flex-start; justify-content: space-between;
  gap: 12px; flex-wrap: wrap; margin-bottom: 16px;
}
.mg-title { margin: 0; font-size: 1.1rem; font-weight: 700; color: var(--n-text-color); }
.mg-sub   { margin: 4px 0 0; font-size: 0.8rem; color: var(--n-text-color-3); }

/* ── Contadores ─────────────────────────────────────────────────────────── */
.stat-card { text-align: center; }
.stat-card--violet { border-top: 3px solid #6366f1; }
.stat-card--green  { border-top: 3px solid #18a058; }
.stat-card--orange { border-top: 3px solid #f08c00; }
.stat-card--red    { border-top: 3px solid #e03131; }
.stat-num { font-size: 1.9rem; font-weight: 900; line-height: 1.1; }
.stat-lbl { margin-top: 4px; font-size: 0.72rem; color: var(--n-text-color-3); }

/* ── Fuera de zona ──────────────────────────────────────────────────────── */
.fuera-header { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.fuera-titulo { font-size: 0.88rem; font-weight: 700; color: #c92a2a; }
.fuera-sub    { font-size: 0.75rem; color: var(--n-text-color-3); }

.alerta-card {
  padding: 12px; background: var(--n-color);
  border: 1.5px solid; border-radius: 12px;
}
.alerta-card--abandono {
  border-color: #e03131;
  background-color: color-mix(in srgb, #e03131 8%, var(--n-color));
}
.alerta-card--fuera {
  border-color: #f08c00;
  background-color: color-mix(in srgb, #f08c00 8%, var(--n-color));
}
.alerta-card__body   { display: flex; gap: 10px; align-items: flex-start; }
.alerta-card__footer { display: flex; justify-content: flex-end; margin-top: 10px; }

.alerta-avatar {
  display: flex; align-items: center; justify-content: center;
  width: 38px; height: 38px; border-radius: 50%; flex-shrink: 0; font-size: 17px;
}
.alerta-avatar--orange { background: color-mix(in srgb, #f08c00 20%, transparent); }
.alerta-avatar--red    { background: color-mix(in srgb, #e03131 20%, transparent); }

.alerta-info   { flex: 1; min-width: 0; }
.alerta-nombre { font-size: 0.9rem; font-weight: 700; color: var(--n-text-color); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.alerta-sub    { margin-top: 2px; font-size: 0.75rem; color: var(--n-text-color-3); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.alerta-timer {
  display: flex; flex-direction: column; align-items: center;
  min-width: 52px; padding: 6px 8px; border-radius: 8px; flex-shrink: 0;
}
.alerta-timer--orange { background: color-mix(in srgb, #f08c00 15%, transparent); }
.alerta-timer--red    { background: color-mix(in srgb, #e03131 15%, transparent); }
.alerta-timer__num { font-size: 1.4rem; font-weight: 900; line-height: 1; }
.alerta-timer--orange .alerta-timer__num { color: #f08c00; }
.alerta-timer--red    .alerta-timer__num { color: #e03131; }
.alerta-timer__lbl { font-size: 10px; color: var(--n-text-color-3); }

/* ── Tabla ──────────────────────────────────────────────────────────────── */
.tabla-header { display: flex; align-items: center; justify-content: space-between; }
.tabla-header__meta { font-size: 0.75rem; font-weight: 400; color: var(--n-text-color-3); }

:deep(.cell-personal) { display: flex; flex-direction: column; gap: 3px; }
:deep(.cell-personal__nombre) { font-size: 0.87rem; font-weight: 600; color: var(--n-text-color); }
:deep(.cell-personal__meta)   { display: flex; gap: 5px; align-items: center; flex-wrap: wrap; }
:deep(.cell-sm)    { font-size: 0.82rem; color: var(--n-text-color); }
:deep(.cell-xs)    { font-size: 0.75rem; }
:deep(.cell-muted) { font-size: 0.8rem; color: var(--n-text-color-3); }
:deep(.cell-timer) { display: flex; flex-direction: column; align-items: center; gap: 3px; }
:deep(.timer--red)    { font-size: 1rem; font-weight: 800; color: #e03131; }
:deep(.timer--orange) { font-size: 1rem; font-weight: 800; color: #f08c00; }
:deep(.dist--fuera)   { font-size: 0.82rem; font-weight: 700; color: #f08c00; }

:deep(.acc-col) { display: flex; flex-direction: column; gap: 4px; align-items: center; }

:deep(.dias-grid) { display: flex; gap: 3px; flex-wrap: wrap; align-items: center; }
:deep(.dia-chip) {
  display: inline-flex; align-items: center; justify-content: center;
  width: 22px; height: 22px; border-radius: 4px;
  font-size: 10px; font-weight: 700; cursor: default; color: var(--n-text-color);
}
:deep(.dia-chip--hoy)    { color: #fff !important; background: #18a058; box-shadow: 0 1px 4px rgb(24 160 88 / 35%); }
:deep(.dia-chip--futuro) { background: color-mix(in srgb, #6366f1 18%, transparent); border: 1px solid color-mix(in srgb, #6366f1 30%, transparent); }
:deep(.dia-chip--pasado) { background: color-mix(in srgb, currentColor 12%, transparent); opacity: 0.35; }
:deep(.dias-grid__mas)   { font-size: 10px; font-style: italic; color: var(--n-text-color-3); }

:deep(.row--fuera td)    { background: color-mix(in srgb, #f08c00 7%, var(--n-color)) !important; }
:deep(.row--abandono td) { background: color-mix(in srgb, #e03131 7%, var(--n-color)) !important; }
:deep(.row--fuera-horario td) {
  background: color-mix(in srgb, #94a3b8 6%, var(--n-color)) !important;
  opacity: 0.65;
}

/* ── Celda horario ──────────────────────────────────────────────────────── */
:deep(.cell-horario) { display: flex; flex-direction: column; align-items: center; gap: 3px; }
:deep(.cell-horario__rango) { font-size: 0.82rem; font-weight: 600; color: var(--n-text-color); }
:deep(.cell-horario__off) { opacity: 0.6; }

/* ── Mapa ───────────────────────────────────────────────────────────────── */
.mapa-header { display: flex; align-items: center; justify-content: space-between; }
.mapa-header__meta { font-size: 0.75rem; font-weight: 400; color: var(--n-text-color-3); }

/* ── Panel usuarios conectados ──────────────────────────────────────────── */
.conexion-bar {
  display: flex; gap: 8px; align-items: center; flex-wrap: wrap;
  padding: 8px 14px;
  background: var(--n-color);
  border: 1px solid var(--n-border-color);
  border-radius: 10px;
}

.conexion-bar__chip {
  display: inline-flex; gap: 5px; align-items: center;
  padding: 3px 10px; border-radius: 20px;
  font-size: 0.75rem; border: 1px solid transparent;
}
.conexion-bar__chip strong { font-size: 0.88rem; }
.conexion-bar__chip--blue   { color: #1d4ed8; background: #eff6ff; border-color: #bfdbfe; }
.conexion-bar__chip--green  { color: #15803d; background: #f0fdf4; border-color: #bbf7d0; }
.conexion-bar__chip--gray   { color: #64748b; background: #f8fafc; border-color: #e2e8f0; }
.conexion-bar__chip--orange { color: #c2410c; background: #fff7ed; border-color: #fed7aa; }

.conexion-dot { width: 7px; height: 7px; border-radius: 50%; flex-shrink: 0; }
.conexion-dot--pulse { background: #3b82f6; animation: mg-pulse 2s ease-in-out infinite; }

@keyframes mg-pulse {
  0%, 100% { opacity: 1; }
  50%      { opacity: 0.5; }
}

.conexion-bar__lista { display: flex; gap: 4px; flex-wrap: wrap; margin-left: auto; }

.conexion-avatar {
  display: flex; align-items: center; justify-content: center;
  width: 28px; height: 28px; border-radius: 50%;
  font-size: 9px; font-weight: 800; cursor: default;
  border: 2px solid transparent;
  transition: transform 0.15s;
}
.conexion-avatar:hover  { transform: scale(1.15); }
.conexion-avatar--gps   { color: #15803d; background: #dcfce7; border-color: #86efac; }
.conexion-avatar--fuera { color: #c2410c; background: #fff7ed; border-color: #fdba74; }
.conexion-avatar--sin   { color: #64748b; background: #f1f5f9; border-color: #cbd5e1; }
</style>
