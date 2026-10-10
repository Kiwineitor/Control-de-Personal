<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { useI18n } from '@vben/locales';
import { useUserStore } from '@vben/stores';

import { NButton, NSpin } from 'naive-ui';

import { message } from '#/adapter/naive';
import {
  type DiaTurnoCalendarioDto,
  getMiJornada,
  type MiJornadaResponse,
} from '#/api/sisu/app';
import { useGeolocalizacion } from '#/composables/useGeolocalizacion';
import { usePlatformPermisos } from '#/composables/usePlatformPermisos';
import { registrarHorarioJornada, useTurnoHoy } from '#/composables/useTurnoHoy';
import { useWebSocket } from '#/composables/useWebSocket';
import BitacoraModal from '#/views/sisu/movil/bitacora/modules/bitacora-modal.vue';

const router    = useRouter();
const userStore = useUserStore();
const { t, locale } = useI18n();

const jornada         = ref<MiJornadaResponse | null>(null);
const cargandoInicial = ref(true);
const bitacoraModalShow = ref(false);
const offline = ref(!navigator.onLine);

const ALMACEN_JORNADA = 'sisu:home:jornada';

onMounted(() => {
  window.addEventListener('offline', () => { offline.value = true; });
  window.addEventListener('online', () => { offline.value = false; });
});

const { notificaciones, gpsStatus } = useWebSocket();
const { coordenadas, iniciarWatch, senalPerdida, watchActivo, gpsActivo } = useGeolocalizacion();
const {
  estadoUbicacion, estadoNotificacion,
  pedirUbicacion, pedirNotificaciones,
} = usePlatformPermisos();
const { proximoTurno, tieneTurnoHoy, enHorarioTurno, faseTurnoHoy, horaFinTurnoHoy,
        horaInicioTurnoHoy, versionTurno, cargado: turnoCargado, cargarTurnoHoy } = useTurnoHoy();

// El turno se recargó (por WebSocket, por push, o al volver a primer plano) →
// recargar también la jornada del día. Son endpoints distintos: sin esto la
// tarjeta seguía mostrando el horario y el lugar anteriores aunque el estado del
// turno ya se hubiera actualizado.
watch(versionTurno, () => { void cargar(true); });

// Refrescar jornada/turno al llegar notificación relevante
watch(
  () => notificaciones.value[0],
  (newest) => {
    if (!newest) return;
    if (
      newest.tipo === 'JORNADA_ASIGNADA' ||
      // Edición de horarios de una jornada ya asignada: hay que refrescar o el
      // usuario sigue viendo el horario viejo.
      newest.tipo === 'JORNADA_ACTUALIZADA' ||
      newest.tipo === 'ACTIVIDAD_ASIGNADA' ||
      newest.tipo === 'HORARIO_ASIGNADO' ||
      newest.tipo === 'RECORDATORIO_TURNO' ||
      newest.tipo === 'FIN_TURNO'
    ) {
      cargar(true);
      cargarTurnoHoy(true);
    }
  },
);

const primerNombre = computed(() => {
  const r = userStore.userInfo?.realName ?? userStore.userInfo?.username ?? 'Usuario';
  return r.split(' ')[0];
});

const saludo = computed(() => {
  const h = new Date().getHours();
  if (h >= 5  && h < 12) return t('inicio.buenos_dias');
  if (h >= 12 && h < 19) return t('inicio.buenas_tardes');
  return t('inicio.buenas_noches');
});

const fechaLocale = computed(() => locale.value === 'pt-BR' ? 'pt-BR' : 'es-PE');

const fechaHoy = computed(() =>
  new Date().toLocaleDateString(fechaLocale.value, {
    weekday: 'long', day: 'numeric', month: 'long',
  }),
);

// ── Modo de la pantalla ─────────────────────────────────────────────────────
// 'turno-hoy'      → hoy es día de turno O no se pudo confirmar (fail-safe activo).
// 'sin-turno-hoy'  → día libre CONFIRMADO (turno cargado y hoy no le toca).
// 'sin-jornada'    → no tiene ninguna asignación vigente.
//
// A prueba de fallos: sólo mostramos "sin-turno-hoy" (ubicación en pausa) cuando
// CONFIRMAMOS que hoy es día libre. Si el backend no respondió (turnoCargado=false),
// no afirmamos "no tienes turno": tratamos el estado como activo.
const esDiaLibreConfirmado = computed(() => turnoCargado.value && !tieneTurnoHoy.value);

const modo = computed<'sin-jornada' | 'sin-turno-hoy' | 'turno-hoy'>(() => {
  if (esDiaLibreConfirmado.value) {
    return (jornada.value || proximoTurno.value) ? 'sin-turno-hoy' : 'sin-jornada';
  }
  // Turno hoy confirmado, o estado desconocido (fail-safe): modo activo.
  if (jornada.value || tieneTurnoHoy.value) return 'turno-hoy';
  return 'sin-jornada';
});

/**
 * ¿Corresponde mostrar la tarjeta de GPS?
 *
 * Sólo mientras el turno está en curso. Antes se mostraba durante todo el día de
 * turno, así que al terminar la jornada el GPS quedaba parado —correctamente— pero
 * la tarjeta seguía ahí anunciando "Sin conexión con el sistema" y "Conectando…".
 * Eso se leía como una falla de red cuando en realidad el turno ya había acabado.
 *
 * Si el turno aún no se cargó (sin respuesta del backend) se mantiene visible:
 * es el mismo criterio conservador que usa `esDiaLibreConfirmado`.
 */
const mostrarTarjetaGps = computed(() => enHorarioTurno.value);

/**
 * Etiqueta de la tarjeta de jornada.
 *
 * Se decide por `enHorarioTurno`, no por la fase: así "EN TURNO HOY" es imposible
 * de mostrar fuera del horario aunque la fase no se haya podido calcular. Cuando no
 * sabemos en qué punto del día estamos, una etiqueta neutra es preferible a afirmar
 * algo falso.
 */
const etiquetaEstadoTurno = computed(() => {
  if (enHorarioTurno.value) return t('inicio.en_turno_hoy');
  if (faseTurnoHoy.value === 'antes')   return t('inicio.turno_no_iniciado');
  if (faseTurnoHoy.value === 'despues') return t('inicio.turno_finalizado');
  return t('inicio.turno_generico');
});

/**
 * Texto del estado del turno cuando NO está en curso (antes o después).
 * Se renderiza dentro de la tarjeta de jornada, no como tarjeta aparte.
 */
const avisoFaseTurno = computed<null | { detalle: string; icono: string; titulo: string }>(() => {
  if (faseTurnoHoy.value === 'antes') {
    return {
      icono: '🕒',
      titulo: t('inicio.turno_por_empezar'),
      detalle: horaInicioTurnoHoy.value
        ? t('inicio.turno_por_empezar_desc', { hora: hFmt(horaInicioTurnoHoy.value) })
        : t('inicio.turno_por_empezar_desc_sinhora'),
    };
  }
  if (faseTurnoHoy.value === 'despues') {
    return {
      icono: '✅',
      titulo: t('inicio.turno_terminado'),
      detalle: horaFinTurnoHoy.value
        ? t('inicio.turno_terminado_desc', { hora: hFmt(horaFinTurnoHoy.value) })
        : t('inicio.turno_terminado_desc_sinhora'),
    };
  }
  return null;
});

const porcentaje = computed(() => {
  if (!jornada.value || jornada.value.totalActividades === 0) return 0;
  const hechas = jornada.value.actividadesCompletadas + jornada.value.actividadesValidadas;
  return Math.round((hechas / jornada.value.totalActividades) * 100);
});

// ── Estado real del GPS para la tarjeta del Home ────────────────────────────
// Antes la tarjeta mostraba siempre "Sincronizando con el sistema" en verde,
// sin distinguir GPS apagado / sin señal / fuera de zona. Esta máquina de estados
// refleja lo que de verdad pasa, en orden de prioridad:
//   denegado   → permiso de ubicación bloqueado
//   sinsenal   → no estamos llegando al backend (GPS/Internet caído)
//   fuera      → backend confirma que estás FUERA del área asignada
//   dentro     → backend confirma que estás en tu lugar (conectado con el sistema)
//   conectando → GPS activo pero aún sin la primera confirmación del servidor
type ToneGps = 'err' | 'idle' | 'ok' | 'warn';
interface EstadoGps {
  badge:   string;
  detalle: string;
  tone:    ToneGps;
  titulo:  string;
}
const estadoGps = computed<EstadoGps>(() => {
  if (estadoUbicacion.value === 'denied') {
    return {
      tone: 'err', badge: t('inicio.gps_apagado_badge'),
      titulo: t('inicio.gps_apagado_titulo'),
      detalle: t('inicio.gps_apagado_detalle'),
    };
  }
  if (gpsActivo.value === false) {
    return {
      tone: 'warn', badge: t('inicio.gps_sinsenal_badge'),
      titulo: 'GPS desactivado',
      detalle: 'Activá el GPS en tu celular — la ubicación por red no es confiable',
    };
  }
  if (senalPerdida.value) {
    return {
      tone: 'warn', badge: t('inicio.gps_sinsenal_badge'),
      titulo: t('inicio.gps_sinsenal_titulo'),
      detalle: t('inicio.gps_sinsenal_detalle'),
    };
  }
  const gs = gpsStatus.value;
  if (gs?.estadoZona === 'FUERA') {
    const dist = Math.round(gs.distanciaMetros ?? 0);
    const msg = gs.mensajePersonal ?? `Estás a ${dist} m de tu área — regresá a tu puesto`;
    return {
      tone: 'err', badge: t('inicio.gps_fuera_badge'),
      titulo: t('inicio.gps_fuera_titulo'),
      detalle: t('inicio.gps_fuera_detalle', { msg }),
    };
  }
  if (gs?.estadoZona === 'DENTRO') {
    const prec = coordenadas.value ? Math.round(coordenadas.value.precision ?? 0) : 0;
    return {
      tone: 'ok', badge: t('inicio.gps_ok_badge'),
      titulo: gs.nombreLugar ? t('inicio.gps_ok_titulo', { lugar: gs.nombreLugar }) : t('inicio.gps_ok_titulo_default'),
      detalle: prec > 0 ? t('inicio.gps_ok_detalle_prec', { prec }) : t('inicio.gps_ok_detalle'),
    };
  }
  return {
    tone: 'idle', badge: t('inicio.gps_conectando_badge'),
    titulo: t('inicio.gps_conectando_titulo'),
    detalle: watchActivo.value ? t('inicio.gps_conectando_detalle_watch') : t('inicio.gps_conectando_detalle_idle'),
  };
});

const hFmt = (h: null | string) => (h ? h.slice(0, 5) : '--:--');

/** Describe cuándo es el próximo turno: "mañana" o "lunes 17 de junio". */
function fmtProximoDia(diaTurno: DiaTurnoCalendarioDto): string {
  const hoy = new Date(); hoy.setHours(0, 0, 0, 0);
  const dia = new Date(`${diaTurno.fecha}T00:00:00`);
  const diff = Math.round((dia.getTime() - hoy.getTime()) / 86_400_000);
  if (diff === 1) return t('inicio.proximoTurno_manana');
  return new Date(`${diaTurno.fecha}T12:00:00`).toLocaleDateString(fechaLocale.value, {
    weekday: 'long', day: 'numeric', month: 'long',
  });
}

async function cargar(silencioso = false) {
  try {
    jornada.value = (await getMiJornada()) ?? null;
    if (jornada.value) {
      sessionStorage.setItem(ALMACEN_JORNADA, JSON.stringify(jornada.value));
    }
  } catch {
    const cache = sessionStorage.getItem(ALMACEN_JORNADA);
    if (cache) {
      jornada.value = JSON.parse(cache) as MiJornadaResponse;
    } else if (!silencioso) {
      message.error('No se pudo cargar la jornada');
    }
  }
  // El horario de la jornada sirve de respaldo cuando el calendario no responde o
  // no trae el día de hoy. Sin esto, la app se quedaba en "estado desconocido" y
  // asumía turno activo a cualquier hora.
  registrarHorarioJornada(jornada.value);
}

// ── Permisos ──────────────────────────────────────────────────────────────
const activandoPermisos = ref(false);

async function activarPermisos() {
  activandoPermisos.value = true;
  try {
    const ubicOk = await pedirUbicacion();
    // Arrancar el GPS en el mismo stack del gesto del usuario. El sender canónico
    // (HTTP en nativo → sobrevive al background) lo aplica iniciarWatch por defecto;
    // NO pasamos un callback STOMP propio (eso rompía el segundo plano).
    // Sólo dentro del horario: conceder el permiso no debe iniciar el registro si el
    // turno ya terminó o aún no empieza.
    if (ubicOk && !esDiaLibreConfirmado.value && enHorarioTurno.value) {
      await iniciarWatch();
    }
    await pedirNotificaciones();
  } finally {
    activandoPermisos.value = false;
  }
}

/** Día libre: solo pedimos notificaciones (para que llegue el aviso de turno). */
async function activarNotificaciones() {
  activandoPermisos.value = true;
  try {
    await pedirNotificaciones();
  } finally {
    activandoPermisos.value = false;
  }
}

/**
 * Panel que pide ubicación y notificaciones.
 *
 * Sólo con el turno EN CURSO. Antes aparecía durante todo el día de turno y encima
 * iba primero en la cadena del template, así que se comía a la tarjeta de fase: por
 * eso, con el turno ya terminado, la app seguía insistiendo en activar el GPS. Pedir
 * ubicación fuera del horario da a entender que se rastrea todo el tiempo, justo lo
 * contrario de lo que promete el aviso de privacidad.
 *
 * Las notificaciones fuera de turno las cubre el banner global del layout, que no
 * depende de esta pantalla.
 */
const mostrarPanelPermisos = computed(() =>
  enHorarioTurno.value
  && (estadoUbicacion.value !== 'granted' || estadoNotificacion.value !== 'granted'),
);

// ── Diagnóstico de fase de turno ──────────────────────────────────────────
// Se envía a la Consola del sistema por el log shipper. Existe porque el estado
// mostrado no coincidía con el horario real y desde fuera no se puede saber qué
// datos ve el dispositivo. Quitar cuando el cálculo esté confirmado en campo.
watch(
  [faseTurnoHoy, turnoCargado, enHorarioTurno, jornada],
  () => {
    console.info('[turno-diag] ' + JSON.stringify({
      fase:          faseTurnoHoy.value,
      turnoCargado:  turnoCargado.value,
      tieneTurnoHoy: tieneTurnoHoy.value,
      enHorario:     enHorarioTurno.value,
      horaInicioEfectiva: horaInicioTurnoHoy.value,
      horaFinEfectiva:    horaFinTurnoHoy.value,
      jornadaHoras:  jornada.value
        ? `${jornada.value.horaInicio} - ${jornada.value.horaFin}`
        : null,
      horaDispositivo: new Date().toTimeString().slice(0, 8),
    }));
  },
  { immediate: true },
);

onMounted(async () => {
  await Promise.allSettled([cargar(), cargarTurnoHoy()]);
  cargandoInicial.value = false;
});
</script>

<template>
  <div class="home">
<!-- ── Greeting ──────────────────────────────────────────── -->
    <header class="greeting">
      <div class="greeting__text">
        <h1 class="greeting__title">
          {{ saludo }},
          <span class="greeting__name">{{ primerNombre }}</span>
        </h1>
        <p class="greeting__date">{{ fechaHoy }}</p>
      </div>
      <div class="greeting__icon" aria-hidden="true">
        <svg v-if="new Date().getHours() < 12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="32" height="32">
          <circle cx="12" cy="12" r="5" />
          <line x1="12" y1="1" x2="12" y2="3" />
          <line x1="12" y1="21" x2="12" y2="23" />
          <line x1="4.22" y1="4.22" x2="5.64" y2="5.64" />
          <line x1="18.36" y1="18.36" x2="19.78" y2="19.78" />
          <line x1="1" y1="12" x2="3" y2="12" />
          <line x1="21" y1="12" x2="23" y2="12" />
          <line x1="4.22" y1="19.78" x2="5.64" y2="18.36" />
          <line x1="18.36" y1="5.64" x2="19.78" y2="4.22" />
        </svg>
        <svg v-else-if="new Date().getHours() < 19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="32" height="32">
          <circle cx="12" cy="12" r="5" />
          <line x1="12" y1="1" x2="12" y2="3" />
          <line x1="12" y1="21" x2="12" y2="23" />
          <line x1="4.22" y1="4.22" x2="5.64" y2="5.64" />
          <line x1="18.36" y1="18.36" x2="19.78" y2="19.78" />
          <line x1="1" y1="12" x2="3" y2="12" />
          <line x1="21" y1="12" x2="23" y2="12" />
          <line x1="4.22" y1="19.78" x2="5.64" y2="18.36" />
          <line x1="18.36" y1="5.64" x2="19.78" y2="4.22" />
          <path d="M16 12a4 4 0 0 0-4-4" opacity="0.5" />
        </svg>
        <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="32" height="32">
          <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
        </svg>
      </div>
    </header>

    <!-- ── Loading ───────────────────────────────────────────── -->
    <div v-if="cargandoInicial" class="loading-wrap">
      <NSpin size="large" />
    </div>

    <template v-else>
<!-- ═══════════════ HOY ES DÍA DE TURNO ═══════════════ -->
      <template v-if="modo === 'turno-hoy'">
<!-- Ubicación: panel de permisos (si falta) o GPS activo -->
        <div v-if="mostrarPanelPermisos" class="permisos-panel">
          <div class="permisos-panel__header">
            <span class="permisos-panel__icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22">
                <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z" />
                <circle cx="12" cy="10" r="3" />
              </svg>
            </span>
            <div>
          <p class="permisos-panel__title">{{ t('inicio.turno_hoy_titulo') }}</p>
          <p class="permisos-panel__sub">
            {{ t('inicio.turno_hoy_sub') }}
          </p>
            </div>
          </div>

          <!-- Ítem: Ubicación -->
          <div class="permiso-item" :class="{ 'permiso-item--ok': estadoUbicacion === 'granted' }">
            <span class="permiso-item__icono">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
                <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z" />
                <circle cx="12" cy="10" r="3" />
              </svg>
            </span>
            <div class="permiso-item__texto">
              <span class="permiso-item__nombre">{{ t('inicio.ubicacion_label') }}</span>
              <span class="permiso-item__desc">
                <template v-if="estadoUbicacion === 'granted'">{{ t('inicio.concedido') }}</template>
                <template v-else-if="estadoUbicacion === 'denied'">
                  {{ t('inicio.ubicacion_denegada') }}
                </template>
                <template v-else>{{ t('inicio.ubicacion_necesaria') }}</template>
              </span>
            </div>
            <span v-if="estadoUbicacion === 'granted'" class="permiso-check">✓</span>
            <span v-else-if="estadoUbicacion === 'denied'" class="permiso-badge permiso-badge--denied">
              {{ t('inicio.denegado') }}
            </span>
            <span v-else class="permiso-badge permiso-badge--pending">{{ t('inicio.pendiente') }}</span>
          </div>

          <!-- Ítem: Notificaciones -->
          <div class="permiso-item" :class="{ 'permiso-item--ok': estadoNotificacion === 'granted' }">
            <span class="permiso-item__icono">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
                <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
                <path d="M13.73 21a2 2 0 0 1-3.46 0" />
              </svg>
            </span>
            <div class="permiso-item__texto">
              <span class="permiso-item__nombre">{{ t('inicio.notificaciones_label') }}</span>
              <span class="permiso-item__desc">
                <template v-if="estadoNotificacion === 'granted'">{{ t('inicio.concedido') }}</template>
                <template v-else-if="estadoNotificacion === 'denied'">
                  {{ t('inicio.ubicacion_denegada') }}
                </template>
                <template v-else>{{ t('inicio.ubicacion_necesaria') }}</template>
              </span>
            </div>
            <span v-if="estadoNotificacion === 'granted'" class="permiso-check">✓</span>
            <span v-else-if="estadoNotificacion === 'denied'" class="permiso-badge permiso-badge--denied">
              {{ t('inicio.denegado') }}
            </span>
            <span v-else class="permiso-badge permiso-badge--pending">{{ t('inicio.pendiente') }}</span>
          </div>

          <!-- Botón único de acción -->
          <button
            v-if="estadoUbicacion !== 'denied' || estadoNotificacion !== 'denied'"
            class="permisos-btn"
            :disabled="activandoPermisos"
            @click="activarPermisos"
          >
            <span v-if="activandoPermisos" class="permisos-spinner"></span>
            <span v-else>{{ t('inicio.btn_permitir') }}</span>
          </button>
        </div>

        <!-- Fuera del horario no se muestra tarjeta de estado aquí: el detalle vive
             dentro de la propia tarjeta de jornada, más abajo. -->

        <!-- GPS activo: chip de estado real (apagado / sin señal / fuera / en zona / conectando) -->
        <div v-else-if="mostrarTarjetaGps" class="gps-card" :class="`gps-card--${estadoGps.tone}`">
          <div class="gps-card__left">
            <span class="gps-card__icon">
              <svg
viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                   width="18" height="18"
>
                <circle cx="12" cy="12" r="10" />
                <circle cx="12" cy="12" r="3" />
                <line x1="12" y1="2" x2="12" y2="6" />
                <line x1="12" y1="18" x2="12" y2="22" />
                <line x1="2" y1="12" x2="6" y2="12" />
                <line x1="18" y1="12" x2="22" y2="12" />
              </svg>
            </span>
            <div class="gps-card__info">
              <p class="gps-card__titulo">{{ estadoGps.titulo }}</p>
              <p class="gps-card__estado">{{ estadoGps.detalle }}</p>
            </div>
          </div>
          <div class="gps-badge" :class="`gps-badge--${estadoGps.tone}`">
            <span class="gps-badge__dot"></span>
            {{ estadoGps.badge }}
          </div>
        </div>

        <!-- Tarjeta de jornada (progreso + actividades).
             Fuera del horario pasa a gris y absorbe el detalle de la fase: así el
             usuario ve un solo bloque con nombre, horario, lugar y estado, en vez
             de una tarjeta suelta encima repitiendo contexto. -->
        <template v-if="jornada">
          <div class="jornada-card" :class="{ 'jornada-card--inactiva': !enHorarioTurno }">
            <div class="jornada-card__top">
              <!-- El punto "en vivo" sólo cuando el turno está EN CURSO. Antes era
                   fijo, así que la tarjeta decía "EN TURNO HOY" con el indicador
                   latiendo incluso horas después de terminada la jornada. -->
              <span class="live-badge" :class="{ 'live-badge--inactivo': !enHorarioTurno }">
                <span v-if="enHorarioTurno" class="live-badge__dot"></span>
                {{ etiquetaEstadoTurno }}
              </span>
              <span class="tipo-badge">{{ jornada.tipoAsignacion }}</span>
            </div>

            <h2 class="jornada-card__nombre">{{ jornada.nombre }}</h2>

            <div class="jornada-card__meta">
              <span class="meta-chip">
                <span class="meta-chip__icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" width="12" height="12">
                    <circle cx="12" cy="12" r="10" />
                    <polyline points="12 6 12 12 16 14" />
                  </svg>
                </span>
                {{ hFmt(jornada.horaInicio) }} – {{ hFmt(jornada.horaFin) }}
              </span>
              <span v-if="jornada.nombreLugar" class="meta-chip">
                <span class="meta-chip__icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" width="12" height="12">
                    <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z" />
                    <circle cx="12" cy="10" r="3" />
                  </svg>
                </span>
                {{ jornada.nombreLugar }}
              </span>
            </div>

            <!-- Estado de la fase, dentro de la misma tarjeta -->
            <div v-if="avisoFaseTurno" class="fase-inline">
              <p class="fase-inline__titulo">
                {{ avisoFaseTurno.icono }} {{ avisoFaseTurno.titulo }}
              </p>
              <p class="fase-inline__detalle">{{ avisoFaseTurno.detalle }}</p>

              <!-- Terminado el turno, lo siguiente que necesita saber es cuándo vuelve. -->
              <template v-if="faseTurnoHoy === 'despues'">
                <div v-if="proximoTurno" class="fase-inline__proximo">
                  <span class="fase-inline__label">{{ t('inicio.proximo_turno') }}</span>
                  <span class="fase-inline__dia">{{ fmtProximoDia(proximoTurno) }}</span>
                  <div class="jornada-card__meta">
                    <span class="meta-chip">
                      <span class="meta-chip__icon">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" width="12" height="12">
                          <circle cx="12" cy="12" r="10" />
                          <polyline points="12 6 12 12 16 14" />
                        </svg>
                      </span>
                      {{ hFmt(proximoTurno.horaInicio) }} – {{ hFmt(proximoTurno.horaFin) }}
                    </span>
                    <span v-if="proximoTurno.nombreLugar" class="meta-chip">
                      <span class="meta-chip__icon">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" width="12" height="12">
                          <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z" />
                          <circle cx="12" cy="10" r="3" />
                        </svg>
                      </span>
                      {{ proximoTurno.nombreLugar }}
                    </span>
                  </div>
                </div>
                <p v-else class="fase-inline__detalle">
                  {{ t('inicio.sin_proximos_turnos') }}
                </p>
              </template>
            </div>

            <div class="progress-block">
              <div class="progress-block__row">
                <span class="progress-block__label">{{ t('inicio.progreso_turno') }}</span>
                <strong class="progress-block__pct">{{ porcentaje }}%</strong>
              </div>
              <div class="progress-block__track" role="progressbar" :aria-valuenow="porcentaje">
                <div class="progress-block__fill" :style="{ width: `${porcentaje}%` }"></div>
              </div>
              <p class="progress-block__sub">
                {{ t('inicio.actividades_completadas', {
                  hechas: jornada.actividadesCompletadas + jornada.actividadesValidadas,
                  total: jornada.totalActividades
                }) }}
              </p>
            </div>
          </div>

          <!-- Stats -->
          <div class="stats-row">
            <div class="stat-item">
              <span class="stat-item__dot stat-item__dot--warn"></span>
              <span class="stat-item__num stat-item__num--warn">{{ jornada.actividadesPendientes }}</span>
              <span class="stat-item__label">{{ t('inicio.pendientes') }}</span>
            </div>
            <div class="stat-divider"></div>
            <div class="stat-item">
              <span class="stat-item__dot stat-item__dot--ok"></span>
              <span class="stat-item__num stat-item__num--ok">{{ jornada.actividadesCompletadas }}</span>
              <span class="stat-item__label">{{ t('inicio.completadas') }}</span>
            </div>
            <div class="stat-divider"></div>
            <div class="stat-item">
              <span class="stat-item__dot stat-item__dot--blue"></span>
              <span class="stat-item__num stat-item__num--blue">{{ jornada.actividadesValidadas }}</span>
              <span class="stat-item__label">{{ t('inicio.validadas') }}</span>
            </div>
          </div>

          <!-- CTA -->
          <NButton
            type="primary"
            size="large"
            block
            class="cta-btn"
            :disabled="!enHorarioTurno"
            @click="router.push('/app/actividades')"
          >
            {{ enHorarioTurno ? t('inicio.ver_actividades') : 'Disponible durante tu turno' }}
          </NButton>

          <!-- Offline: datos cacheados -->
          <div v-if="offline" class="offline-notice">
            <span class="offline-notice__dot"></span>
            Datos cargados previamente — sin conexión al servidor
          </div>
        </template>
      </template>

      <!-- ═══════════════ HOY NO ES DÍA DE TURNO ═══════════════ -->
      <template v-else-if="modo === 'sin-turno-hoy'">
<!-- Estado: día libre + próximo turno -->
        <div class="descanso-card">
          <span class="descanso-card__badge">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14" style="margin-right: 4px; vertical-align: -2px;">
              <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
            </svg>
            {{ t('inicio.dia_libre') }}
          </span>
          <p class="descanso-card__text">
            {{ t('inicio.dia_libre_desc') }}
          </p>

          <div v-if="proximoTurno" class="proximo-turno">
            <span class="proximo-turno__label">{{ t('inicio.proximo_turno') }}</span>
            <span class="proximo-turno__dia">{{ fmtProximoDia(proximoTurno) }}</span>
            <div class="proximo-turno__meta">
              <span class="meta-chip meta-chip--dark">
                <span class="meta-chip__icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" width="12" height="12">
                    <circle cx="12" cy="12" r="10" />
                    <polyline points="12 6 12 12 16 14" />
                  </svg>
                </span>
                {{ hFmt(proximoTurno.horaInicio) }} – {{ hFmt(proximoTurno.horaFin) }}
              </span>
              <span v-if="proximoTurno.nombreLugar" class="meta-chip meta-chip--dark">
                <span class="meta-chip__icon">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" width="12" height="12">
                    <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z" />
                    <circle cx="12" cy="10" r="3" />
                  </svg>
                </span>
                {{ proximoTurno.nombreLugar }}
              </span>
            </div>
          </div>
          <p v-else class="descanso-card__text descanso-card__text--muted">
            {{ t('inicio.sin_proximos_turnos') }}
          </p>
        </div>

        <!-- Ubicación en pausa + notificaciones -->
        <div class="pausa-card">
          <div class="pausa-item">
            <span class="pausa-item__icono">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
                <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z" />
                <circle cx="12" cy="10" r="3" />
              </svg>
            </span>
            <div class="pausa-item__texto">
              <span class="pausa-item__nombre">{{ t('inicio.ubicacion_pausa') }}</span>
              <span class="pausa-item__desc">{{ t('inicio.ubicacion_pausa_desc') }}</span>
            </div>
            <span class="pausa-badge">{{ t('inicio.en_pausa') }}</span>
          </div>

          <div class="pausa-item">
            <span class="pausa-item__icono">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
                <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
                <path d="M13.73 21a2 2 0 0 1-3.46 0" />
              </svg>
            </span>
            <div class="pausa-item__texto">
              <span class="pausa-item__nombre">{{ t('inicio.notificaciones_label') }}</span>
              <span class="pausa-item__desc">
                <template v-if="estadoNotificacion === 'granted'">
                  {{ t('inicio.notif_activas_desc') }}
                </template>
                <template v-else-if="estadoNotificacion === 'denied'">
                  {{ t('inicio.notif_bloqueadas_desc') }}
                </template>
                <template v-else>
                  {{ t('inicio.notif_pendiente_desc') }}
                </template>
              </span>
            </div>
            <span v-if="estadoNotificacion === 'granted'" class="pausa-badge pausa-badge--ok">
              {{ t('inicio.notif_activas') }}
            </span>
          </div>

          <button
            v-if="estadoNotificacion === 'prompt' || estadoNotificacion === 'unknown'"
            class="permisos-btn permisos-btn--notif"
            :disabled="activandoPermisos"
            @click="activarNotificaciones"
          >
            <span v-if="activandoPermisos" class="permisos-spinner"></span>
            <span v-else>{{ t('inicio.activar_notificaciones') }}</span>
          </button>
        </div>
      </template>

      <!-- ═══════════════ SIN JORNADA ═══════════════ -->
      <template v-else>
        <div class="empty-state">
          <div class="empty-state__icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="40" height="40">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
              <polyline points="14 2 14 8 20 8" />
              <line x1="16" y1="13" x2="8" y2="13" />
              <line x1="16" y1="17" x2="8" y2="17" />
              <polyline points="10 9 9 9 8 9" />
            </svg>
          </div>
          <p class="empty-state__title">{{ t('inicio.sin_jornada') }}</p>
          <p class="empty-state__body">
            {{ t('inicio.sin_jornada_desc') }}
          </p>
          <NButton secondary size="small" @click="() => { cargar(); cargarTurnoHoy(true); }">
            {{ t('inicio.actualizar') }}
          </NButton>
        </div>
      </template>

      <!-- ═══════════════ BOTÓN FLOTANTE BITÁCORA ═══════════════ -->
      <button
        v-if="modo === 'turno-hoy'"
        class="home__fab-bitacora"
        :disabled="!enHorarioTurno"
        :title="enHorarioTurno ? '' : 'Disponible durante tu turno'"
        @click="enHorarioTurno ? (bitacoraModalShow = true) : null"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" width="22" height="22">
          <path d="M12 20h9" />
          <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z" />
        </svg>
        <span>Bitácora</span>
      </button>

      <BitacoraModal
        :show="bitacoraModalShow"
        @update:show="bitacoraModalShow = $event"
      />
</template>
  </div>
</template>

<style scoped>
/* ── Layout base ──────────────────────────────────────────── */
.home {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding-bottom: 20px;
}

.loading-wrap {
  display: flex;
  justify-content: center;
  padding: 80px 0;
}

/* ── Greeting ─────────────────────────────────────────────── */
.greeting {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px;
  background: hsl(var(--card));
  border-radius: 14px;
  border: 1px solid hsl(var(--border));
  border-left: 3px solid #2563eb;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
}

/* Tinta navy en vez del negro del tema: mantiene el contraste pero pertenece a la
   misma familia que el resto del portal, en lugar de parecer un gris heredado. */
.greeting__title {
  font-size: 20px;
  font-weight: 600;
  color: rgb(247, 247, 247);
  margin: 0 0 4px;
  line-height: 1.3;
}

.greeting__name {
  color: #2563eb;
  font-weight: 700;
}

.greeting__date {
  font-size: 14px;
  color: hsl(var(--muted-foreground));
  margin: 0;
  text-transform: capitalize;
}

.greeting__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: rgba(37, 99, 235, 0.08);
  color: #2563eb;
  flex-shrink: 0;
}

/* ── GPS Card ──────────────────────────────────────────────── */
.gps-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 12px;
  border: 1px solid transparent;
  transition: background 0.25s, border-color 0.25s;
}

/* ── Estado de fase, dentro de la tarjeta de jornada ─────────────────────
   Hereda el fondo de .jornada-card (oscuro), así que el texto va en blancos
   translúcidos como el resto de esa tarjeta. */
.fase-inline {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid rgba(255, 255, 255, 0.18);
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.fase-inline__titulo {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.95);
}
.fase-inline__detalle {
  margin: 0;
  font-size: 12.5px;
  line-height: 1.45;
  color: rgba(255, 255, 255, 0.72);
}
.fase-inline__proximo {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.fase-inline__label {
  font-size: 10.5px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.6);
}
.fase-inline__dia {
  font-size: 15px;
  font-weight: 700;
  color: #fff;
  text-transform: capitalize;
}

/* Turno fuera de curso: la tarjeta pasa del azul vivo a un gris apagado. El
   contenido no cambia —nombre, horario, lugar y progreso siguen ahí—, sólo baja
   de jerarquía.

   Los tonos están elegidos por CONTRASTE, no sólo por apagados: el texto de esta
   tarjeta es blanco, y sobre #4b5563 da 7:1 (AAA). Un gris más claro se vería más
   "apagado" pero dejaría los blancos por debajo del mínimo legible. */
.jornada-card--inactiva {
  background: linear-gradient(140deg, #4b5563 0%, #374151 100%);
  box-shadow: 0 4px 16px rgba(55, 65, 81, 0.20);
}

/* Los chips de horario y lugar necesitan más cuerpo sobre el gris que sobre el
   azul, o se diluyen contra el fondo. */
.jornada-card--inactiva .meta-chip {
  background: rgba(255, 255, 255, 0.16);
  color: rgba(255, 255, 255, 0.95);
}

/* Tonos de estado: ok=en zona, warn=sin señal, err=fuera/apagado, idle=conectando */
.gps-card--ok   { background: rgba(37, 99, 235, 0.08); border-color: rgba(37, 99, 235, 0.35); }
.gps-card--warn { background: rgba(234, 179, 8, 0.10); border-color: rgba(234, 179, 8, 0.40); }
.gps-card--err  { background: rgba(220, 38, 38, 0.08); border-color: rgba(220, 38, 38, 0.40); }
.gps-card--idle { background: hsl(var(--muted)); border-color: hsl(var(--border)); }

.gps-card__left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
}

.gps-card__icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.gps-card--ok   .gps-card__icon { background: rgba(37, 99, 235, 0.15); color: #1e40af; }
.gps-card--warn .gps-card__icon { background: rgba(234, 179, 8, 0.18); color: #92400e; }
.gps-card--err  .gps-card__icon { background: rgba(220, 38, 38, 0.15); color: #991b1b; }
.gps-card--idle .gps-card__icon { background: hsl(var(--border)); color: hsl(var(--muted-foreground)); }

.gps-card--ok   .gps-card__estado { color: #1e40af; }
.gps-card--warn .gps-card__estado { color: #92400e; }
.gps-card--err  .gps-card__estado { color: #991b1b; }
.gps-card--idle .gps-card__estado { color: #475569; }

.gps-card__info { min-width: 0; }

.gps-card__titulo {
  font-size: 15px;
  font-weight: 700;
  color: #0f2547;
  margin: 0 0 3px;
}

.gps-card--warn .gps-card__titulo { color: #78350f; }
.gps-card--err  .gps-card__titulo { color: #7f1d1d; }
.gps-card--idle .gps-card__titulo { color: #334155; }

.gps-card__estado {
  font-size: 13px;
  margin: 0;
  line-height: 1.4;
  white-space: normal;
  overflow-wrap: break-word;
}

/* Badge de estado */
.gps-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  font-weight: 600;
  padding: 5px 12px;
  border-radius: 20px;
  flex-shrink: 0;
}

.gps-badge--ok   { background: rgba(37, 99, 235, 0.15); color: #1e40af; border: 1px solid rgba(37, 99, 235, 0.35); }
.gps-badge--warn { background: rgba(234, 179, 8, 0.18); color: #92400e; border: 1px solid rgba(234, 179, 8, 0.40); }
.gps-badge--err  { background: rgba(220, 38, 38, 0.15); color: #991b1b; border: 1px solid rgba(220, 38, 38, 0.40); }
.gps-badge--idle { background: hsl(var(--border)); color: #475569; border: 1px solid hsl(var(--border)); }

.gps-badge--ok   .gps-badge__dot { background: #1e40af; }
.gps-badge--warn .gps-badge__dot { background: #92400e; }
.gps-badge--err  .gps-badge__dot { background: #991b1b; }
.gps-badge--idle .gps-badge__dot { background: #475569; }

.gps-badge__dot {
  width: 6px;
  height: 6px;
  background: hsl(var(--success));
  border-radius: 50%;
  animation: gpsPulse 2s ease-in-out infinite;
}

@keyframes gpsPulse {
  0%, 100% { opacity: 1; transform: scale(1);    }
  50%       { opacity: 0.5; transform: scale(0.75); }
}

/* Botón activar */
.gps-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 6px 16px;
  border-radius: 20px;
  border: none;
  background: hsl(var(--success));
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  flex-shrink: 0;
  min-width: 68px;
  -webkit-tap-highlight-color: transparent;
  transition: opacity 0.15s;
}

.gps-btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.gps-spinner {
  width: 12px;
  height: 12px;
  border: 2px solid rgba(255,255,255,0.4);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

/* ── Empty state ──────────────────────────────────────────── */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 48px 24px;
  border-radius: 14px;
  background: hsl(var(--card));
  border: 1px solid hsl(var(--border));
  gap: 10px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
}

.empty-state__icon  { display: flex; color: #2563eb; opacity: 0.7; }
.empty-state__title { font-size: 18px; font-weight: 700; color: hsl(var(--card-foreground)); margin: 0; }
.empty-state__body  { font-size: 14px; color: hsl(var(--muted-foreground)); margin: 0; line-height: 1.55; }

/* ── Jornada card ─────────────────────────────────────────── */
.jornada-card {
  border-radius: 16px;
  padding: 22px;
  background: linear-gradient(140deg, #1e3a8a 0%, #1d4ed8 100%);
  color: #fff;
  box-shadow: 0 4px 16px rgba(30, 58, 138, 0.25);
  position: relative;
  overflow: hidden;
}

.jornada-card::before {
  content: '';
  position: absolute;
  top: -30px;
  right: -30px;
  width: 120px;
  height: 120px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.05);
  pointer-events: none;
}

.jornada-card::after {
  content: '';
  position: absolute;
  bottom: -20px;
  left: 40%;
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.04);
  pointer-events: none;
}

.jornada-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.live-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 1px;
  color: rgba(255, 255, 255, 0.8);
}

/* Turno fuera de curso: sin punto latiendo y en tono apagado. */
.live-badge--inactivo { color: rgba(255, 255, 255, 0.5); }

.live-badge__dot {
  width: 8px;
  height: 8px;
  background: #60a5fa;
  border-radius: 50%;
  animation: blink 2s ease-in-out infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50%       { opacity: 0.3; }
}

.tipo-badge {
  font-size: 12px;
  font-weight: 600;
  background: rgba(255, 255, 255, 0.15);
  border: 1px solid rgba(255, 255, 255, 0.18);
  padding: 3px 12px;
  border-radius: 20px;
  color: rgba(255, 255, 255, 0.92);
}

.jornada-card__nombre {
  font-size: 20px;
  font-weight: 700;
  margin: 0 0 12px;
  line-height: 1.3;
  color: #fff;
}

.jornada-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.meta-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.15);
  padding: 4px 11px;
  border-radius: 20px;
  color: rgba(255, 255, 255, 0.9);
}

.meta-chip__icon { font-size: 12px; }

.progress-block {
  background: rgba(0, 0, 0, 0.18);
  border-radius: 10px;
  padding: 14px 16px;
}

.progress-block__row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.85);
}

.progress-block__pct {
  font-size: 16px;
  font-weight: 700;
  color: #fff;
}

.progress-block__track {
  height: 6px;
  background: rgba(255, 255, 255, 0.18);
  border-radius: 3px;
  overflow: hidden;
  margin-bottom: 7px;
}

.progress-block__fill {
  height: 100%;
  background: #60a5fa;
  border-radius: 3px;
  transition: width 0.6s ease;
  min-width: 2px;
}

.progress-block__sub {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.55);
  margin: 0;
}

/* ── Stats row ────────────────────────────────────────────── */
.stats-row {
  display: flex;
  align-items: center;
  background: hsl(var(--card));
  border: 1px solid hsl(var(--border));
  border-radius: 14px;
  padding: 18px 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
}

.stat-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.stat-divider {
  width: 1px;
  height: 40px;
  background: hsl(var(--border));
  flex-shrink: 0;
}

.stat-item__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  margin-bottom: 2px;
}
.stat-item__dot--warn { background: #0ea5e9; }
.stat-item__dot--ok   { background: #2563eb; }
.stat-item__dot--blue { background: #1e3a8a; }

.stat-item__num {
  font-size: 24px;
  font-weight: 800;
  line-height: 1;
}
/* Rampa de progreso, de claro a oscuro: pendiente → completada → validada.
   Antes eran rojo, azul e índigo —tres tonos sin relación entre sí—; el degradado
   dentro de la misma familia comunica el avance en vez de sólo diferenciar. */
.stat-item__num--warn { color: #0ea5e9; }   /* pendientes  */
.stat-item__num--ok   { color: #2563eb; }   /* completadas */
.stat-item__num--blue { color: #1e3a8a; }   /* validadas   */

.stat-item__label {
  font-size: 13px;
  color: hsl(var(--muted-foreground));
  font-weight: 600;
}

/* ── CTA ──────────────────────────────────────────────────── */
.cta-btn {
  height: 52px !important;
  border-radius: 12px !important;
  font-size: 16px !important;
  font-weight: 700 !important;
  --n-color: #2563eb !important;
  --n-color-hover: #1d4ed8 !important;
  --n-color-pressed: #1e40af !important;
}

/* ── Panel de permisos ────────────────────────────────────── */
.permisos-panel {
  border-radius: 14px;
  border: 1.5px solid rgba(37, 99, 235, 0.35);
  background: rgba(37, 99, 235, 0.06);
  overflow: hidden;
}

.permisos-panel__header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 16px 12px;
  border-bottom: 1px solid rgba(37, 99, 235, 0.20);
}

.permisos-panel__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: rgba(37, 99, 235, 0.10);
  color: #2563eb;
  flex-shrink: 0;
}
.permisos-panel__title {
  font-size: 15px; font-weight: 700; color: #2563eb; margin: 0 0 3px;
}
.permisos-panel__sub {
  font-size: 13px; color: #3b82f6; margin: 0; opacity: 0.8;
}

.permiso-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid rgba(37, 99, 235, 0.12);
  transition: background 0.2s;
}

.permiso-item--ok { background: rgba(37, 99, 235, 0.08); border-bottom-color: rgba(37, 99, 235, 0.30); }

.permiso-item__icono {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: rgba(37, 99, 235, 0.08);
  color: #2563eb;
  flex-shrink: 0;
}

.permiso-item__texto {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.permiso-item__nombre {
  font-size: 14px; font-weight: 600; color: mediumblue; 
}

.permiso-item__desc {
  font-size: 12px; color: hsl(var(--muted-foreground)); line-height: 1.35;
}

.permiso-check {
  font-size: 16px; color: hsl(var(--success)); font-weight: 700; flex-shrink: 0;
}

.permiso-badge {
  font-size: 12px; font-weight: 700; padding: 3px 10px;
  border-radius: 10px; flex-shrink: 0;
}
.permiso-badge--denied  { background: rgba(220, 38, 38, 0.15); color: #dc2626; }
.permiso-badge--pending { background: rgba(234, 179, 8, 0.20); color: #ca8a04; }

.permisos-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  padding: 16px;
  border: none;
  background: #2563eb;
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
  transition: opacity 0.15s;
}
.permisos-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.permisos-btn:active:not(:disabled) { opacity: 0.85; }

.permisos-spinner {
  width: 18px; height: 18px;
  border: 2px solid rgba(255,255,255,0.4);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spinPerm 0.7s linear infinite;
}
@keyframes spinPerm { to { transform: rotate(360deg); } }

.permisos-btn--notif {
  background: #6366f1;
}

/* ── Día libre: tarjeta de descanso + próximo turno ───────── */
.descanso-card {
  border-radius: 16px;
  padding: 22px;
  background: linear-gradient(140deg, #1e3a8a 0%, #3730a3 100%);
  color: #fff;
  box-shadow: 0 4px 16px rgba(30, 58, 138, 0.2);
  position: relative;
  overflow: hidden;
}
.descanso-card::before {
  content: '';
  position: absolute;
  top: -30px;
  right: -30px;
  width: 120px;
  height: 120px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.04);
  pointer-events: none;
}

.descanso-card__badge {
  display: inline-flex;
  align-items: center;
  font-size: 14px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.9);
  background: rgba(255, 255, 255, 0.10);
  border: 1px solid rgba(255, 255, 255, 0.15);
  padding: 5px 14px;
  border-radius: 20px;
  margin-bottom: 12px;
}

.descanso-card__text {
  font-size: 15px;
  color: rgba(255, 255, 255, 0.7);
  margin: 0 0 16px;
  line-height: 1.5;
}
.descanso-card__text--muted {
  color: rgba(255, 255, 255, 0.45);
  margin-bottom: 0;
}

.proximo-turno {
  background: rgba(0, 0, 0, 0.25);
  border-radius: 12px;
  padding: 14px 16px;
}
.proximo-turno__label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 1px;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.5);
  margin-bottom: 4px;
}
.proximo-turno__dia {
  display: block;
  font-size: 19px;
  font-weight: 700;
  color: #fff;
  text-transform: capitalize;
  margin-bottom: 12px;
}
.proximo-turno__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.meta-chip--dark {
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.14);
  color: rgba(255, 255, 255, 0.88);
}

/* ── Día libre: estado de ubicación/notificaciones en pausa ── */
.pausa-card {
  border-radius: 14px;
  border: 1px solid hsl(var(--border));
  background: hsl(var(--card));
  overflow: hidden;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
}

.pausa-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid hsl(var(--border));
}
.pausa-item:last-of-type { border-bottom: none; }

.pausa-item__icono {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: rgba(100, 116, 139, 0.10);
  color: #64748b;
  flex-shrink: 0;
}

.pausa-item__texto {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.pausa-item__nombre {
  font-size: 14px;
  font-weight: 600;
  color: hsl(var(--card-foreground));
}
.pausa-item__desc {
  font-size: 12px;
  color: hsl(var(--muted-foreground));
  line-height: 1.4;
}

.pausa-badge {
  font-size: 12px;
  font-weight: 700;
  padding: 4px 11px;
  border-radius: 12px;
  flex-shrink: 0;
  background: hsl(var(--muted));
  color: hsl(var(--muted-foreground));
}
.pausa-badge--ok {
  background: rgba(37, 99, 235, 0.15);
  color: #2563eb;
}

/* ── FAB Bitácora ───────────────────────────────────────── */
.home__fab-bitacora {
  position: fixed;
  bottom: 90px;
  right: 20px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 12px 18px;
  border-radius: 28px;
  /* Celeste profundo: destaca sobre el fondo claro y contra las tarjetas azules,
     sin salirse de la familia. El naranja anterior era el único tono cálido de la
     pantalla y rompía la unidad. */
  background: #0369a1;
  color: #fff;
  border: none;
  font-size: 13px;
  font-weight: 700;
  box-shadow: 0 4px 16px rgba(3, 105, 161, 0.35);
  cursor: pointer;
  z-index: 10;
  transition: transform .15s;
}
.home__fab-bitacora:active {
  transform: scale(.93);
}
.home__fab-bitacora:disabled {
  opacity: 0.45;
  cursor: not-allowed;
  transform: none;
}

/* ── Offline notice ────────────────────────────────────── */
.offline-notice {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px;
  margin-block: 4px;
  font-size: 12px;
  font-weight: 600;
  color: #92400e;
  background: #fffbeb;
  border-radius: 8px;
}
.offline-notice__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #f59e0b;
  animation: offlinePulse 1.8s ease-in-out infinite;
}
@keyframes offlinePulse {
  0%, 100% { opacity: 1; }
  50%       { opacity: 0.3; }
}
</style>
