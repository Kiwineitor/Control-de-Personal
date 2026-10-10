<script setup lang="ts">
import type { DiaTurnoCalendarioDto } from '#/api/sisu/app';

import { computed, onMounted, ref, watch } from 'vue';

import { useI18n } from '@vben/locales';

import {
  NDrawer, NDrawerContent,
  NEmpty, NSpin, NTag,
} from 'naive-ui';

import { getMisTurnos } from '#/api/sisu/app';
import { useTurnoHoy } from '#/composables/useTurnoHoy';
import { useWebSocket } from '#/composables/useWebSocket';

const { t, locale } = useI18n();
const { turnosRefreshSignal } = useWebSocket();

const { turnoHoy, proximoTurno, tieneTurnoHoy, cargarTurnoHoy } = useTurnoHoy();

const hoyDate = new Date();
const mesActual = ref(hoyDate.getMonth() + 1);
const anioActual = ref(hoyDate.getFullYear());

const fechaLocale = computed(() => locale.value === 'pt-BR' ? 'pt-BR' : 'es-PE');

interface DiaCalendario {
  date: number;
  fecha: string;
  turno?: DiaTurnoCalendarioDto;
  esHoy: boolean;
  esMesActual: boolean;
}

const weekdays = computed(() => {
  const base = new Date(2024, 0, 1);
  return Array.from({ length: 7 }, (_, i) => {
    const d = new Date(base);
    d.setDate(base.getDate() + i);
    return d.toLocaleDateString(fechaLocale.value, { weekday: 'short' }).slice(0, 3).toUpperCase();
  });
});

const diasDelMes = computed(() => {
  const year = anioActual.value;
  const month = mesActual.value;
  const daysInMonth = new Date(year, month, 0).getDate();
  const startWeekday = new Date(year, month - 1, 1).getDay();
  const startOffset = startWeekday === 0 ? 6 : startWeekday - 1;
  const hoyStr = `${String(hoyDate.getFullYear()).padStart(4, '0')}-${String(hoyDate.getMonth() + 1).padStart(2, '0')}-${String(hoyDate.getDate()).padStart(2, '0')}`;
  const dias: DiaCalendario[] = [];

  for (let i = 0; i < startOffset; i++) {
    dias.push({ date: 0, fecha: '', esHoy: false, esMesActual: false });
  }

  for (let d = 1; d <= daysInMonth; d++) {
    const fecha = `${year}-${String(month).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
    dias.push({
      date: d,
      fecha,
      turno: mapaTurnos.value[fecha],
      esHoy: fecha === hoyStr,
      esMesActual: true,
    });
  }

  const rem = 7 - (dias.length % 7);
  if (rem < 7) {
    for (let i = 0; i < rem; i++) {
      dias.push({ date: 0, fecha: '', esHoy: false, esMesActual: false });
    }
  }

  return dias;
});

const turnos = ref<DiaTurnoCalendarioDto[]>([]);
const cargando = ref(false);
const drawerVisible = ref(false);
const turnoSeleccionado = ref<DiaTurnoCalendarioDto | null>(null);

const mapaTurnos = computed(() => {
  const m: Record<string, DiaTurnoCalendarioDto> = {};
  for (const turno of turnos.value) m[turno.fecha] = turno;
  return m;
});

const mesNombre = computed(() =>
  new Date(anioActual.value, mesActual.value - 1, 1)
    .toLocaleDateString(fechaLocale.value, { month: 'long', year: 'numeric' }),
);

const turnosFuturos = computed(() => turnos.value.filter(x => !x.esPasado));
const turnosPasados = computed(() => turnos.value.filter(x => x.esPasado && !x.esHoy));
const totalMes = computed(() => turnos.value.length);
const proximos = computed(() => turnosFuturos.value.length);
const pasados = computed(() => turnosPasados.value.length);

function claseTurnoItem(turno: DiaTurnoCalendarioDto) {
  return turno.esHoy ? 'turno-item--hoy' : '';
}

function etiquetaTipo(tipo: string) {
  const map: Record<string, string> = {
    REGULAR: 'Regular',
    GUARDIA_12H: 'Guardia 12h',
    GUARDIA_24H: 'Guardia 24h',
    NOCTURNA: 'Nocturna',
    ROTACION: 'Rotación',
    LLAMADA: 'Localización',
    EMERGENCIA: 'Emergencia',
    COBERTURA: 'Cobertura',
    ADMINISTRATIVA: 'Administrativa',
    GUARDIA: 'Guardia',
  };
  return map[tipo] ?? tipo;
}

function fmtFechaTitulo(fecha: string, diaSemana?: string | null) {
  const d = new Date(`${fecha}T12:00:00`);
  const nom = diaSemana ? `${diaSemana.charAt(0).toUpperCase()}${diaSemana.slice(1)}` : '';
  const dia = d.toLocaleDateString(fechaLocale.value, { day: '2-digit', month: 'long', year: 'numeric' });
  return nom ? `${nom}, ${dia}` : dia;
}

function fmtProximoDia(d: DiaTurnoCalendarioDto) {
  const hoy = new Date(); hoy.setHours(0, 0, 0, 0);
  const dia = new Date(`${d.fecha}T00:00:00`);
  const diff = Math.round((dia.getTime() - hoy.getTime()) / 86_400_000);
  if (diff === 1) return t('inicio.proximoTurno_manana');
  return new Date(`${d.fecha}T12:00:00`).toLocaleDateString(fechaLocale.value, {
    weekday: 'long', day: 'numeric', month: 'long',
  });
}

function hFmt(h?: string | null) { return h ? h.slice(0, 5) : '--:--'; }

async function cargar() {
  cargando.value = true;
  try { turnos.value = await getMisTurnos(mesActual.value, anioActual.value); }
  catch { /* silent */ }
  finally { cargando.value = false; }
}

function mesSiguiente() {
  if (mesActual.value === 12) { mesActual.value = 1; anioActual.value++; }
  else mesActual.value++;
  cargar();
}

function mesAnterior() {
  if (mesActual.value === 1) { mesActual.value = 12; anioActual.value--; }
  else mesActual.value--;
  cargar();
}

function irAHoy() {
  mesActual.value = hoyDate.getMonth() + 1;
  anioActual.value = hoyDate.getFullYear();
  cargar();
}

function abrirDetalle(turno: DiaTurnoCalendarioDto) {
  turnoSeleccionado.value = turno;
  drawerVisible.value = true;
}

onMounted(() => {
  cargar();
  cargarTurnoHoy();
});

watch(turnosRefreshSignal, () => { cargar(); cargarTurnoHoy(true); });
</script>

<template>
  <div class="turnos-page">
    
    <header class="mes-header">
      <div class="mes-header__row">
        <button class="mes-nav__btn" aria-label="Mes anterior" @click="mesAnterior">
          <svg viewBox="0 0 24 24" fill="none" stroke="#1e40af" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="15 18 9 12 15 6" />
          </svg>
        </button>

        <div class="mes-nav__info">
          <h2 class="mes-nav__nombre">{{ mesNombre }}</h2>
          <span class="mes-nav__sub">
            {{ totalMes === 0 ? t('turnos.sin_turnos_mes') : `${totalMes} ${totalMes === 1 ? t('turnos.asignado') : t('turnos.asignados')}` }}
          </span>
        </div>

        <button class="mes-nav__btn" aria-label="Mes siguiente" @click="mesSiguiente">
          <svg viewBox="0 0 24 24" fill="none" stroke="#1e40af" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="9 18 15 12 9 6" />
          </svg>
        </button>

        <button class="hoy-btn" @click="irAHoy">
          <svg viewBox="0 0 24 24" fill="none" stroke="#1e40af" stroke-width="2.5" style="width:16px;height:16px">
            <rect x="3" y="4" width="18" height="18" rx="2" />
            <line x1="3" y1="10" x2="21" y2="10" />
            <line x1="8" y1="2" x2="8" y2="6" />
            <line x1="16" y1="2" x2="16" y2="6" />
          </svg>
        </button>
      </div>

      <div class="stats-row">
        <div class="stat-item">
          <span class="stat-item__num stat-item__num--total">{{ totalMes }}</span>
          <span class="stat-item__label">{{ t('turnos.total', 'Total') }}</span>
        </div>
        <div class="stat-divider"></div>
        <div class="stat-item">
          <span class="stat-item__num stat-item__num--blue">{{ proximos }}</span>
          <span class="stat-item__label">{{ t('turnos.proximos', 'Próximos') }}</span>
        </div>
        <div class="stat-divider"></div>
        <div class="stat-item">
          <span class="stat-item__num stat-item__num--muted">{{ pasados }}</span>
          <span class="stat-item__label">{{ t('turnos.realizados', 'Realizados') }}</span>
        </div>
      </div>
    </header>

    <div v-if="tieneTurnoHoy && turnoHoy" class="turno-hoy-card" @click="abrirDetalle(turnoHoy)">
      <div class="turno-hoy-card__top">
        <span class="live-badge">
          <span class="live-badge__dot"></span>
          {{ t('turnos.hoy_estas_en_turno', 'HOY ESTÁS EN TURNO') }}
        </span>
      </div>
      <h3 class="turno-hoy-card__nombre">{{ turnoHoy.nombreTurno }}</h3>
      <div class="turno-hoy-card__meta">
        <span class="meta-chip">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" width="14" height="14">
            <circle cx="12" cy="12" r="10" />
            <polyline points="12 6 12 12 16 14" />
          </svg>
          {{ hFmt(turnoHoy.horaInicio) }} – {{ hFmt(turnoHoy.horaFin) }}
        </span>
        <span v-if="turnoHoy.nombreLugar" class="meta-chip">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" width="14" height="14">
            <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z" />
            <circle cx="12" cy="10" r="3" />
          </svg>
          {{ turnoHoy.nombreLugar }}
        </span>
      </div>
    </div>

    <div v-else class="descanso-card">
      <span class="descanso-card__badge">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" width="16" height="16">
          <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
        </svg>
        {{ t('turnos.hoy_no_tienes', 'Libre hoy') }}
      </span>
      <div v-if="proximoTurno" class="proximo-turno">
        <span class="proximo-turno__label">{{ t('turnos.proximo_turno', 'Próximo turno:') }}</span>
        <span class="proximo-turno__dia">{{ fmtProximoDia(proximoTurno) }}</span>
        <div class="proximo-turno__meta">
          <span class="meta-chip meta-chip--dark">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" width="14" height="14">
              <circle cx="12" cy="12" r="10" />
              <polyline points="12 6 12 12 16 14" />
            </svg>
            {{ hFmt(proximoTurno.horaInicio) }} – {{ hFmt(proximoTurno.horaFin) }}
          </span>
        </div>
      </div>
      <p v-else class="descanso-card__text">
        {{ t('turnos.sin_programados', 'No tienes turnos próximos programados.') }}
      </p>
    </div>

    <div class="leyenda">
      <span class="leyenda-item"><span class="leyenda-dot leyenda-dot--hoy"></span>{{ t('turnos.hoy_btn') }}</span>
      <span class="leyenda-item"><span class="leyenda-dot leyenda-dot--fut"></span>{{ t('turnos.proximo') }}</span>
      <span class="leyenda-item"><span class="leyenda-dot leyenda-dot--pas"></span>{{ t('turnos.realizado') }}</span>
    </div>

    <div class="cal-card">
      <NSpin :show="cargando">
        <div class="cal-grid">
          <span v-for="d in weekdays" :key="d" class="cal-g__hd">{{ d }}</span>
          <div
            v-for="(dia, idx) in diasDelMes"
            :key="idx"
            class="cal-g__cell"
            :class="{
              'cal-g__cell--empty': !dia.esMesActual,
              'cal-g__cell--today': dia.esHoy && !dia.turno,
              'cal-g__cell--hoy': dia.turno?.esHoy,
              'cal-g__cell--fut': dia.turno && !dia.turno.esHoy && !dia.turno.esPasado,
              'cal-g__cell--pas': dia.turno?.esPasado,
            }"
            @click="dia.turno && abrirDetalle(dia.turno)"
          >
            <span v-if="dia.esMesActual" class="cal-g__num">{{ dia.date }}</span>
          </div>
        </div>

        <NEmpty
          v-if="!cargando && turnos.length === 0"
          :description="t('turnos.no_turnos_mes', 'No hay turnos asignados en este mes')"
          style="padding: 40px 0"
          class="empty-text"
        />
      </NSpin>
    </div>

    <template v-if="proximos > 0">
      <div class="seccion-header">
        <h3 class="seccion-titulo">{{ t('turnos.proximos', 'Próximos Turnos') }}</h3>
        <span class="seccion-count">{{ proximos }}</span>
      </div>

      <div class="lista-turnos">
        <div
          v-for="turno in turnosFuturos"
          :key="turno.fecha"
          class="turno-item"
          :class="claseTurnoItem(turno)"
          @click="abrirDetalle(turno)"
        >
          <div class="turno-item__fecha" :class="{ 'turno-item__fecha--hoy': turno.esHoy }">
            <span class="turno-item__dia">{{ new Date(`${turno.fecha}T12:00:00`).getDate() }}</span>
            <span class="turno-item__ddn">{{ (turno.diaSemana || '').slice(0, 3).toUpperCase() }}</span>
          </div>

          <div class="turno-item__info">
            <div class="turno-item__nombre">{{ turno.nombreTurno }}</div>
            <div class="turno-item__horario">
              {{ hFmt(turno.horaInicio) }} – {{ hFmt(turno.horaFin) }}
            </div>
            <div v-if="turno.nombreLugar" class="turno-item__lugar">{{ turno.nombreLugar }}</div>
          </div>

          <NTag :type="turno.esHoy ? 'info' : 'default'" size="small" round :bordered="false" class="custom-tag">
            {{ turno.esHoy ? t('turnos.hoy_btn', 'Hoy') : etiquetaTipo(turno.tipoTurno) }}
          </NTag>
        </div>
      </div>
    </template>

    <template v-if="pasados > 0">
      <div class="seccion-header seccion-header--pasados">
        <h3 class="seccion-titulo">{{ t('turnos.realizados', 'Turnos Realizados') }}</h3>
        <span class="seccion-count seccion-count--muted">{{ pasados }}</span>
      </div>

      <div class="lista-turnos">
        <div
          v-for="turno in turnosPasados"
          :key="turno.fecha"
          class="turno-item turno-item--pasado"
          @click="abrirDetalle(turno)"
        >
          <div class="turno-item__fecha turno-item__fecha--pas">
            <span class="turno-item__dia">{{ new Date(`${turno.fecha}T12:00:00`).getDate() }}</span>
            <span class="turno-item__ddn">{{ (turno.diaSemana || '').slice(0, 3).toUpperCase() }}</span>
          </div>

          <div class="turno-item__info">
            <div class="turno-item__nombre turno-item__nombre--pas">{{ turno.nombreTurno }}</div>
            <div class="turno-item__horario">
              {{ hFmt(turno.horaInicio) }} – {{ hFmt(turno.horaFin) }}
            </div>
          </div>
          <NTag type="default" size="small" round :bordered="false" class="custom-tag">{{ t('turnos.realizado_tag') }}</NTag>
        </div>
      </div>
    </template>

    <NDrawer v-model:show="drawerVisible" :width="420" placement="right">
      <NDrawerContent
        v-if="turnoSeleccionado"
        closable
        :title="fmtFechaTitulo(turnoSeleccionado.fecha, turnoSeleccionado.diaSemana)"
      >
        <div class="detalle">
          <div class="d-badge-row">
            <NTag
              :type="turnoSeleccionado.esHoy ? 'info' : turnoSeleccionado.esPasado ? 'default' : 'success'"
              size="medium" round :bordered="false"
            >
              {{ turnoSeleccionado.esHoy ? t('turnos.tu_turno_hoy') : turnoSeleccionado.esPasado ? t('turnos.realizado_tag') : t('turnos.proximo_turno_tag') }}
            </NTag>
          </div>

          <div class="d-cards">
            <div class="d-card">
              <span class="d-card__bar d-card__bar--blue"></span>
              <div class="d-card__body">
                <span class="d-card__label">{{ t('turnos.total', 'Turno') }}</span>
                <span class="d-card__value">{{ turnoSeleccionado.nombreTurno }}</span>
              </div>
            </div>
            <div class="d-card">
              <span class="d-card__bar d-card__bar--indigo"></span>
              <div class="d-card__body">
                <span class="d-card__label">Tipo</span>
                <span class="d-card__value">{{ etiquetaTipo(turnoSeleccionado.tipoTurno) }}</span>
              </div>
            </div>
            <div v-if="turnoSeleccionado.nombreLugar" class="d-card">
              <span class="d-card__bar d-card__bar--teal"></span>
              <div class="d-card__body">
                <span class="d-card__label">{{ t('turnos.lugar', 'Ubicación') }}</span>
                <span class="d-card__value">{{ turnoSeleccionado.nombreLugar }}</span>
              </div>
            </div>
            <div v-if="turnoSeleccionado.modalidad === 'PARTIDA'" class="d-card">
              <span class="d-card__bar d-card__bar--amber"></span>
              <div class="d-card__body">
                <span class="d-card__label">{{ t('turnos.modalidad', 'Modalidad') }}</span>
                <span class="d-card__value">{{ t('turnos.horario_partido', 'Partido') }}</span>
              </div>
            </div>
          </div>

          <div class="d-h">
            <svg class="d-h__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16">
              <circle cx="12" cy="12" r="10"/>
              <polyline points="12 6 12 12 16 14"/>
            </svg>
            <span class="d-h__title">Horario</span>

            <template v-if="turnoSeleccionado.modalidad !== 'PARTIDA'">
              <div class="d-h__row">
                <div class="d-h__blq">
                  <span class="d-h__lbl">{{ t('turnos.entrada', 'Entrada') }}</span>
                  <span class="d-h__num">{{ hFmt(turnoSeleccionado.horaInicio) }}</span>
                </div>
                <span class="d-h__arrow">→</span>
                <div class="d-h__blq">
                  <span class="d-h__lbl">{{ t('turnos.salida', 'Salida') }}</span>
                  <span class="d-h__num">{{ hFmt(turnoSeleccionado.horaFin) }}</span>
                </div>
              </div>
            </template>
            <template v-else>
              <div class="d-h__splits">
                <div class="d-h__split">
                  <span class="d-h__lbl">{{ t('turnos.manana', 'Mañana') }}</span>
                  <span class="d-h__range">{{ hFmt(turnoSeleccionado.horaInicio) }} – {{ hFmt(turnoSeleccionado.horaFin) }}</span>
                </div>
                <div class="d-h__divider"></div>
                <div class="d-h__split">
                  <span class="d-h__lbl">{{ t('turnos.tarde', 'Tarde') }}</span>
                  <span class="d-h__range">{{ hFmt(turnoSeleccionado.horaInicioTarde) }} – {{ hFmt(turnoSeleccionado.horaFinTarde) }}</span>
                </div>
              </div>
            </template>
          </div>
        </div>
      </NDrawerContent>
    </NDrawer>
  </div>
</template>

<style scoped>
/* Contenedor general */
.turnos-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding-bottom: 30px;
  background-color: transparent;
}

/* ═══════════════ ENCABEZADO Y NAVEGACIÓN ═══════════════ */
.mes-header {
  background: #ffffff;
  border-radius: 16px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 4px 12px rgba(30, 64, 175, 0.05);
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.mes-header__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.mes-nav__btn {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}
.mes-nav__btn:active { background: #e2e8f0; transform: scale(0.95); }
.mes-nav__btn svg { width: 22px; height: 22px; }

.hoy-btn {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}
.hoy-btn:active { background: #dbeafe; transform: scale(0.95); }

.mes-nav__info { flex: 1; text-align: center; }

.mes-nav__nombre {
  font-size: 20px;
  font-weight: 800;
  color: #1e293b;
  margin: 0;
  text-transform: capitalize;
}

.mes-nav__sub {
  font-size: 13px;
  color: #64748b;
  margin-top: 4px;
  display: block;
  font-weight: 500;
}

/* ═══════════════ STATS ═══════════════ */
.stats-row {
  display: flex;
  align-items: center;
  background: #f8fafc;
  border-radius: 12px;
  padding: 14px 10px;
}

.stat-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.stat-divider { width: 1px; height: 36px; background: #e2e8f0; }

.stat-item__num { font-size: 24px; font-weight: 800; line-height: 1; }
.stat-item__num--total { color: #1e293b; } 
.stat-item__num--blue { color: #2563eb; } 
.stat-item__num--muted { color: #94a3b8; } 
.stat-item__label { font-size: 13px; color: #64748b; font-weight: 600; }

/* ═══════════════ BANNERS ═══════════════ */
.turno-hoy-card {
  border-radius: 18px;
  padding: 22px;
  background: linear-gradient(135deg, #2563eb 0%, #1e40af 100%);
  color: #ffffff;
  box-shadow: 0 8px 24px rgba(37, 99, 235, 0.25);
  cursor: pointer;
  position: relative;
  overflow: hidden;
  transition: transform 0.2s;
}
.turno-hoy-card:active { transform: scale(0.98); }

.turno-hoy-card::before {
  content: ''; position: absolute; top: -40px; right: -40px;
  width: 150px; height: 150px; border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
}

.live-badge {
  display: inline-flex; align-items: center; gap: 8px;
  font-size: 12px; font-weight: 700; letter-spacing: 1px;
  color: rgba(255, 255, 255, 0.9); margin-bottom: 12px;
}

.live-badge__dot {
  width: 8px; height: 8px; background: #60a5fa;
  border-radius: 50%; animation: blink 2s infinite;
}
@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }

.turno-hoy-card__nombre { font-size: 20px; font-weight: 700; margin: 0 0 12px; line-height: 1.3; }
.turno-hoy-card__meta { display: flex; flex-wrap: wrap; gap: 8px; }

.meta-chip {
  display: inline-flex; align-items: center; gap: 6px;
  font-size: 13px; font-weight: 600;
  background: rgba(255, 255, 255, 0.15);
  padding: 6px 12px; border-radius: 20px;
}

.descanso-card {
  border-radius: 18px; padding: 22px;
  background: #ffffff; border: 1px solid #e2e8f0;
  box-shadow: 0 4px 12px rgba(30, 64, 175, 0.05);
}
.descanso-card__badge {
  display: inline-flex; align-items: center; gap: 6px;
  font-size: 14px; font-weight: 600;
  background: #f1f5f9; color: #475569;
  padding: 6px 14px; border-radius: 20px; margin-bottom: 16px;
}
.proximo-turno { background: #f8fafc; border-radius: 14px; padding: 16px; border: 1px solid #e2e8f0;}
.proximo-turno__label { font-size: 12px; color: #64748b; text-transform: uppercase; margin-bottom: 6px; display: block; font-weight: 600;}
.proximo-turno__dia { font-size: 18px; font-weight: 800; color: #1e293b; margin-bottom: 12px; display: block;}

.meta-chip--dark {
  background: #e2e8f0; color: #334155; border: none;
}

/* ═══════════════ LEYENDA Y CALENDARIO ═══════════════ */
.leyenda { 
  display: flex; justify-content: center; gap: 24px; 
  font-size: 12px; color: #64748b; font-weight: 600; 
}
.leyenda-item { display: flex; align-items: center; gap: 6px; }
.leyenda-dot { width: 8px; height: 8px; border-radius: 50%; }
.leyenda-dot--hoy { background: #2563eb; }
.leyenda-dot--fut { background: #93c5fd; }
.leyenda-dot--pas { background: #cbd5e1; }

.cal-card {
  border-radius: 16px;
  border: 1px solid #e2e8f0;
  background: #ffffff;
  box-shadow: 0 4px 16px rgba(30, 64, 175, 0.06);
  overflow: hidden;
}

.cal-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
}

.cal-g__hd {
  text-align: center;
  padding: 7px 0;
  font-size: 11px;
  font-weight: 700;
  color: #475569;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.cal-g__cell {
  position: relative;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-bottom: 1px solid #f1f5f9;
  border-right: 1px solid #f1f5f9;
  cursor: default;
  transition: background 0.12s ease;
}

.cal-g__cell:nth-child(7n) {
  border-right: none;
}

.cal-g__cell--empty {
  background: #fafafa;
}

.cal-g__num {
  width: 26px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  color: #111827;
  border-radius: 50%;
  line-height: 1;
  transition: all 0.15s ease;
}

.cal-g__cell--today .cal-g__num {
  background: #2563eb;
  color: #fff;
  font-weight: 700;
  box-shadow: 0 2px 6px rgba(37, 99, 235, 0.3);
}

.cal-g__cell--hoy {
  background: #eff6ff;
  cursor: pointer;
}
.cal-g__cell--hoy .cal-g__num {
  background: #2563eb;
  color: #fff;
  font-weight: 700;
  box-shadow: 0 2px 6px rgba(37, 99, 235, 0.3);
}

.cal-g__cell--fut {
  background: #f5f9ff;
  cursor: pointer;
}
.cal-g__cell--fut:hover { background: #dbeafe; }
.cal-g__cell--fut .cal-g__num {
  color: #1e40af;
  font-weight: 700;
}
.cal-g__cell--fut::after {
  content: '';
  position: absolute;
  bottom: 3px;
  left: 50%;
  transform: translateX(-50%);
  width: 14px;
  height: 3px;
  background: #93c5fd;
  border-radius: 2px;
}

.cal-g__cell--pas {
  background: #fafbfc;
  cursor: pointer;
}
.cal-g__cell--pas:hover { background: #f1f5f9; }
.cal-g__cell--pas .cal-g__num {
  color: #94a3b8;
  font-weight: 600;
}
.cal-g__cell--pas::after {
  content: '';
  position: absolute;
  bottom: 3px;
  left: 50%;
  transform: translateX(-50%);
  width: 14px;
  height: 3px;
  background: #d1d5db;
  border-radius: 2px;
}

.cal-g__cell--hoy::after {
  content: '';
  position: absolute;
  bottom: 3px;
  left: 50%;
  transform: translateX(-50%);
  width: 14px;
  height: 3px;
  background: #2563eb;
  border-radius: 2px;
}

:deep(.n-empty__description) {
  color: #64748b !important;
  font-weight: 600 !important;
}

/* ═══════════════ LISTAS ═══════════════ */
.seccion-header { display: flex; align-items: center; gap: 10px; padding: 0 4px; }
.seccion-header--pasados { margin-top: 10px; }
.seccion-titulo { font-size: 18px; font-weight: 800; color: #1e293b; margin: 0; flex: 1; }
.seccion-count { font-size: 14px; font-weight: 800; padding: 4px 14px; border-radius: 20px; background: #eff6ff; color: #2563eb; }
.seccion-count--muted { background: #f1f5f9; color: #64748b; }

.lista-turnos { display: flex; flex-direction: column; gap: 12px; }

.turno-item {
  display: flex; align-items: center; gap: 14px;
  border-radius: 16px; border: 1px solid #e2e8f0;
  background: #ffffff; padding: 16px;
  box-shadow: 0 2px 8px rgba(30, 64, 175, 0.04);
}
.turno-item--hoy { border-color: #93c5fd; background: #f8fafc; border-width: 2px;}
.turno-item--pasado { opacity: 0.8; }

.turno-item__fecha {
  display: flex; flex-direction: column; align-items: center;
  min-width: 54px; padding: 10px 6px; border-radius: 12px;
  background: #f1f5f9; color: #1e293b;
}
.turno-item__fecha--hoy { background: #2563eb; color: #fff; }
.turno-item__fecha--pas { color: #64748b; }

.turno-item__dia { font-size: 24px; font-weight: 900; line-height: 1; }
.turno-item__ddn { font-size: 11px; font-weight: 800; opacity: 0.9; margin-top: 4px; }

.turno-item__info { flex: 1; min-width: 0; }
.turno-item__nombre { font-size: 16px; font-weight: 800; color: #1e293b; margin-bottom: 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;}
.turno-item__nombre--pas { color: #475569; }
.turno-item__horario { font-size: 14px; color: #64748b; font-weight: 600; }
.turno-item__lugar { font-size: 13px; color: #94a3b8; font-weight: 600; margin-top: 4px; }

.custom-tag { font-weight: 700; font-size: 12px;}

/* ═══════════════ DETALLE (DRAWER) ═══════════════ */
:deep(.n-drawer) { background-color: #ffffff !important; }
:deep(.n-drawer-content) { background-color: #ffffff !important; }
:deep(.n-drawer-body-content-wrapper) {
  color: #1e293b !important;
}
:deep(.n-drawer-header) { 
  background: linear-gradient(135deg, #1e40af 0%, #2563eb 100%) !important;
  border-bottom: none !important;
  padding: 20px 24px !important;
}
:deep(.n-drawer-header__main) { 
  color: #ffffff !important; 
  font-size: 15px !important; 
  font-weight: 800 !important;
  letter-spacing: 0.3px;
}
:deep(.n-drawer-body) { 
  background-color: #ffffff !important; 
  padding: 20px !important;
  color: #1e293b !important;
}
:deep(.n-base-close) { color: rgba(255,255,255,0.8) !important; }
:deep(.n-base-close:hover) { color: #ffffff !important; }

.detalle {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.d-badge-row {
  display: flex;
  justify-content: center;
  margin-bottom: 4px;
}

.d-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.d-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  display: flex;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}

.d-card__bar {
  width: 4px;
  flex-shrink: 0;
}

.d-card__bar--blue { background: #2563eb; }
.d-card__bar--indigo { background: #6366f1; }
.d-card__bar--teal { background: #0d9488; }
.d-card__bar--amber { background: #d97706; }

.d-card__body {
  flex: 1;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.d-card__label {
  font-size: 10px;
  font-weight: 700;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.d-card__value {
  font-size: 15px;
  font-weight: 800;
  color: #1e293b;
  line-height: 1.3;
  word-break: break-word;
}

.d-h {
  background: linear-gradient(135deg, #f8fafc 0%, #eff6ff 100%);
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  padding: 18px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.d-h__icon {
  color: #2563eb;
  width: 20px;
  height: 20px;
}

.d-h__title {
  font-size: 13px;
  font-weight: 800;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 1px;
}

.d-h__row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
  width: 100%;
}

.d-h__blq {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  flex: 1;
}

.d-h__lbl {
  font-size: 11px;
  font-weight: 700;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.d-h__num {
  font-size: 28px;
  font-weight: 900;
  color: #1e40af;
  line-height: 1.2;
}

.d-h__arrow {
  font-size: 20px;
  color: #cbd5e1;
  font-weight: 300;
  flex-shrink: 0;
}

.d-h__splits {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
}

.d-h__split {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.d-h__range {
  font-size: 18px;
  font-weight: 900;
  color: #1e40af;
  line-height: 1.2;
}

.d-h__divider {
  height: 1px;
  background: #e2e8f0;
}
</style>