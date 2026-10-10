<script lang="ts" setup>
import type { SelectOption } from 'naive-ui';

import type { JornadaDto, JornadaRequest, LugarSelectDto, ModalidadHorario, TipoJornada, TitularSelectDto } from '#/api/sisu/jornada';

import { computed, h, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import {
  NButton, NDatePicker, NDivider, NForm, NFormItem, NGrid, NGridItem,
  NInput, NRadioButton, NRadioGroup, NSelect, NSpin, NSwitch, NTimePicker,
} from 'naive-ui';

import { message } from '#/adapter/naive';
import {
  asignarTitulares, createJornada,
  getLugaresDisponibles,
  getTitularesDisponibles,
  updateJornada,
} from '#/api/sisu/jornada';

const emit = defineEmits<{ success: [] }>();

// ── Catálogo de tipos de jornada ───────────────────────────────────────────

interface TipoConfig {
  value:             TipoJornada;
  icon:              string;
  label:             string;
  sub:               string;
  color:             string;
  forzaContinua:     boolean;  // true = no se puede elegir modalidad PARTIDA
  horaInicioDefault: string;
  horaFinDefault:    string;
}

const TIPOS_CONFIG: TipoConfig[] = [
  {
    value: 'REGULAR',     icon: '📋', label: 'Regular',      sub: 'Diaria (8-9h)',
    color: '#64748b', forzaContinua: true,  horaInicioDefault: '07:00', horaFinDefault: '15:00',
  },
  {
    value: 'GUARDIA_12H', icon: '🛡️', label: 'Guardia 12h',  sub: 'Mañana o noche',
    color: '#0d9488', forzaContinua: true,  horaInicioDefault: '07:00', horaFinDefault: '19:00',
  },
  {
    value: 'GUARDIA_24H', icon: '🔒', label: 'Guardia 24h',  sub: 'Turno completo',
    color: '#0891b2', forzaContinua: true,  horaInicioDefault: '07:00', horaFinDefault: '07:00',
  },
  {
    value: 'NOCTURNA',    icon: '🌙', label: 'Nocturna',      sub: '19:00 → 07:00',
    color: '#4338ca', forzaContinua: true,  horaInicioDefault: '19:00', horaFinDefault: '07:00',
  },
  {
    value: 'ROTACION',    icon: '🔄', label: 'Rotación',      sub: 'MIR / Internos',
    color: '#7c3aed', forzaContinua: true,  horaInicioDefault: '07:00', horaFinDefault: '14:00',
  },
  {
    value: 'LLAMADA',     icon: '📞', label: 'De Llamada',    sub: 'Localización',
    color: '#d97706', forzaContinua: true,  horaInicioDefault: '08:00', horaFinDefault: '22:00',
  },
  {
    value: 'EMERGENCIA',  icon: '🚨', label: 'Emergencia',    sub: 'Respuesta urgente',
    color: '#dc2626', forzaContinua: true,  horaInicioDefault: '00:00', horaFinDefault: '23:59',
  },
  {
    value: 'COBERTURA',      icon: '📌', label: 'Cobertura',      sub: 'Cubre una ausencia',
    color: '#be123c', forzaContinua: true,  horaInicioDefault: '07:00', horaFinDefault: '15:00',
  },
  {
    value: 'ADMINISTRATIVA', icon: '🏢', label: 'Administrativa', sub: 'Horario fijo (8-12/14-18)',
    color: '#0369a1', forzaContinua: false, horaInicioDefault: '08:00', horaFinDefault: '12:00',
  },
];

// ── Estado del formulario ──────────────────────────────────────────────────

const loading = ref(false);
const isEdit  = computed(() => !!form.value.idJornada);

interface FormData {
  idJornada?:       number;
  tipoJornada:      TipoJornada;
  modalidad:        ModalidadHorario;
  periodoDias:      'rango' | 'unico';
  fechaInicio:      null | string;
  fechaFin:         null | string;
  horaInicio:       null | string;
  horaFin:          null | string;
  horaInicioTarde:  null | string;
  horaFinTarde:     null | string;
  nombre:           string;
  descripcion:      string;
  idLugar:          null | number;  // lugar/fuente laboral (geocerca del turno)
  diasDefault:      Set<string>;  // días específicos del turno (CSV ISO)
}

const EMPTY = (): FormData => ({
  idJornada:       undefined,
  tipoJornada:     'REGULAR',
  modalidad:       'CONTINUA',
  periodoDias:     'unico',
  fechaInicio:     null,
  fechaFin:        null,
  horaInicio:      '07:00',
  horaFin:         '15:00',
  horaInicioTarde: '15:00',
  horaFinTarde:    '19:00',
  nombre:          '',
  descripcion:     '',
  idLugar:         null,
  diasDefault:     new Set<string>(),
});

const form = ref<FormData>(EMPTY());

// ── Computed ───────────────────────────────────────────────────────────────

const tipoActual = computed(() =>
  TIPOS_CONFIG.find(t => t.value === form.value.tipoJornada),
);

const soportaModalidad = computed(() => !tipoActual.value?.forzaContinua);

const colorActivo = computed(() => tipoActual.value?.color ?? '#18a058');

// true cuando horaFin < horaInicio → el turno cruza medianoche y termina al día siguiente
const cruzaMedianoche = computed(() =>
  form.value.modalidad === 'CONTINUA'
  && !!form.value.horaInicio && !!form.value.horaFin
  && form.value.horaFin < form.value.horaInicio,
);

// ── Personal del turno (solo en creación) ──────────────────────────────────

const titularesDisp  = ref<TitularSelectDto[]>([]);
const titularesSelec = ref<number[]>([]);
const cargandoPerso  = ref(false);

// ── Horarios individuales por titular ────────────────────────────────────────
const horariosIndividuales = ref<Record<number, { usarGeneral: boolean; inicio: string | null; fin: string | null }>>({});

watch(titularesSelec, (nuevos) => {
  for (const id of nuevos) {
    if (!horariosIndividuales.value[id]) {
      horariosIndividuales.value[id] = { usarGeneral: true, inicio: null, fin: null };
    }
  }
  for (const id of Object.keys(horariosIndividuales.value).map(Number)) {
    if (!nuevos.includes(id)) delete horariosIndividuales.value[id];
  }
}, { deep: true });

const titularesConOverride = computed(() =>
  titularesSelec.value.filter(id => {
    const h = horariosIndividuales.value[id];
    return h && !h.usarGeneral;
  }),
);

function nombreTitular(id: number): string {
  return titularesDisp.value.find(t => t.idTitular === id)?.nombreCompleto ?? '';
}

function getHorarioEntry(id: number) {
  return horariosIndividuales.value[id]!;
}

// ── Lugares (fuente laboral / geocerca del turno) ──────────────────────────
const lugaresDisp = ref<LugarSelectDto[]>([]);

async function cargarLugares() {
  if (lugaresDisp.value.length > 0) return;
  try {
    lugaresDisp.value = await getLugaresDisponibles();
  } catch {
    /* sin lugares: el selector queda vacío */
  }
}

const lugaresOpts = computed<SelectOption[]>(() =>
  lugaresDisp.value.map(l => ({ label: l.nombre, value: l.idLugar })),
);

async function cargarPersonal() {
  if (titularesDisp.value.length > 0) return;
  cargandoPerso.value = true;
  try {
    titularesDisp.value = await getTitularesDisponibles();
  } finally {
    cargandoPerso.value = false;
  }
}

// Extrae abreviatura del cargo para el badge (MÉDICO, ENFERM., etc.)
function abrevCargo(cargo: null | string): string {
  if (!cargo) return 'PERS';
  const c = cargo.toLowerCase();
  if (/m[eé]dic|doctor|dr\./.test(c)) return 'MÉD';
  if (/enfer/.test(c))                return 'ENF';
  if (/t[eé]cnic/.test(c))           return 'TÉC';
  if (/psicol/.test(c))               return 'PSI';
  if (/obstetr/.test(c))              return 'OBS';
  if (/odontol/.test(c))              return 'ODO';
  if (/nutric/.test(c))               return 'NUT';
  return cargo.slice(0, 3).toUpperCase();
}

const CARGO_COLOR: Record<string, string> = {
  MÉD: '#0d9488', ENF: '#7c3aed', TÉC: '#d97706',
  PSI: '#0369a1', OBS: '#be123c', ODO: '#0891b2',
  NUT: '#15803d', PERS: '#64748b',
};

function renderTitularLabel(option: SelectOption) {
  const t = titularesDisp.value.find(x => x.idTitular === option.value);
  const abrev = abrevCargo(t?.cargo ?? null);
  const color = CARGO_COLOR[abrev] ?? '#64748b';
  return h('div', { style: 'display:flex;align-items:center;gap:8px' }, [
    h('span', {
      style: `background:${color}20;color:${color};border:1px solid ${color}55;
              font-size:.64rem;font-weight:700;padding:1px 6px;border-radius:10px;white-space:nowrap`,
    }, abrev),
    h('span', { style: 'font-size:.83rem' }, t?.nombreCompleto ?? String(option.label)),
    t?.cargo ? h('span', { style: 'font-size:.72rem;color:#94a3b8' }, `· ${t.cargo}`) : null,
  ]);
}


const titularesOpts = computed<SelectOption[]>(() =>
  titularesDisp.value.map(t => ({
    label: t.nombreCompleto,
    value: t.idTitular,
  })),
);


// Solo ADMINISTRATIVA puede tener modalidad PARTIDA; cualquier otro tipo → siempre CONTINUA
watch(() => form.value.tipoJornada, (tipo) => {
  if (tipo !== 'ADMINISTRATIVA') {
    form.value.modalidad = 'CONTINUA';
  }
});

// ── Modal lifecycle ────────────────────────────────────────────────────────

const [Modal, modalApi] = useVbenModal({
  onOpenChange(isOpen: boolean) {
    if (!isOpen) return;
    const data = modalApi.getData<JornadaDto>();
    // Siempre resetear selección de personal
    titularesSelec.value = [];
    horariosIndividuales.value = {};
    cargarLugares();   // lugares disponibles para el selector (crear y editar)
    if (data?.idJornada) {
      // Auto-migra el tipo legacy GUARDIA → GUARDIA_24H al editar
      const tipoJornada = (data.tipoJornada === 'GUARDIA'
        ? 'GUARDIA_24H'
        : data.tipoJornada) as TipoJornada;
      const d = data as any;
      form.value = {
        idJornada:       data.idJornada,
        tipoJornada,
        modalidad:       d.modalidad      ?? 'CONTINUA',
        periodoDias:     data.fechaInicio === data.fechaFin ? 'unico' : 'rango',
        fechaInicio:     data.fechaInicio  ?? null,
        fechaFin:        data.fechaFin     ?? null,
        horaInicio:      data.horaInicio   ? data.horaInicio.slice(0, 5)   : '07:00',
        horaFin:         data.horaFin      ? data.horaFin.slice(0, 5)      : '15:00',
        horaInicioTarde: d.horaInicioTarde ? d.horaInicioTarde.slice(0, 5) : '15:00',
        horaFinTarde:    d.horaFinTarde    ? d.horaFinTarde.slice(0, 5)    : '19:00',
        nombre:          data.nombre       ?? '',
        descripcion:     data.descripcion  ?? '',
        idLugar:         data.idLugar       ?? null,
        diasDefault:     data.diasDefault
          ? new Set((data.diasDefault as string).split(',').filter(Boolean))
          : new Set<string>(),
      };
    } else {
      form.value = EMPTY();
      cargarPersonal();  // precarga disponibles para la sección de personal
    }
  },
});

// ── Calendario de días del turno ───────────────────────────────────────────
// computed ya está importado en la línea 3

const diasDelMes = computed(() => {
  const inicio = form.value.fechaInicio;
  const fin    = form.value.fechaFin ?? form.value.fechaInicio;
  if (!inicio || form.value.periodoDias === 'unico') return [];
  // Parsear año y mes directamente del string ISO (sin new Date() para evitar problemas de timezone)
  const [year, mes] = inicio.split('-').map(Number);
  const total = new Date(year, mes, 0).getDate();  // último día del mes
  const result = [];
  for (let d = 1; d <= total; d++) {
    const fecha = `${year}-${String(mes).padStart(2,'0')}-${String(d).padStart(2,'0')}`;
    // Comparar strings ISO directamente (evita offset timezone)
    const enRango = fecha >= inicio && fecha <= fin!;
    const dt = new Date(`${fecha}T12:00:00`);  // mediodía para evitar DST issues
    result.push({
      fecha,
      diaNum: d,
      diaSemana: dt.toLocaleDateString('es', { weekday: 'short' }),
      enRango,
    });
  }
  return result;
});

function toggleDia(fecha: string) {
  const s = new Set(form.value.diasDefault);
  if (s.has(fecha)) s.delete(fecha); else s.add(fecha);
  form.value.diasDefault = s;
}

function seleccionarTodosMes() {
  form.value.diasDefault = new Set(
    diasDelMes.value.filter(d => d.enRango).map(d => d.fecha)
  );
}

// ── Acciones ───────────────────────────────────────────────────────────────

function seleccionarTipo(tipo: TipoJornada) {
  const config = TIPOS_CONFIG.find(t => t.value === tipo);
  form.value.tipoJornada = tipo;
  if (config?.forzaContinua) form.value.modalidad = 'CONTINUA';
  // En modo creación, autorellenar horas predeterminadas del tipo
  if (!isEdit.value) {
    form.value.horaInicio = config?.horaInicioDefault ?? '07:00';
    form.value.horaFin    = config?.horaFinDefault    ?? '15:00';
  }
}

// ── Validación ─────────────────────────────────────────────────────────────

function validar(): null | string {
  if (!form.value.fechaInicio) return 'La fecha de inicio es obligatoria';
  if (form.value.periodoDias === 'rango') {
    if (!form.value.fechaFin) return 'La fecha de fin es obligatoria';
    if (form.value.fechaFin < form.value.fechaInicio)
      return 'La fecha fin debe ser igual o posterior al inicio';
  }
  if (!form.value.horaInicio) return 'La hora de entrada es obligatoria';
  if (!form.value.horaFin)    return 'La hora de salida es obligatoria';
  if (form.value.modalidad === 'PARTIDA') {
    if (!form.value.horaInicioTarde) return 'La hora de entrada del turno tarde es obligatoria';
    if (!form.value.horaFinTarde)    return 'La hora de salida del turno tarde es obligatoria';
  }
  return null;
}

// ── Submit ─────────────────────────────────────────────────────────────────

async function handleSubmit() {
  const err = validar();
  if (err) { message.warning(err); return; }

  loading.value = true;
  try {
    const fechaFin = form.value.periodoDias === 'unico'
      ? form.value.fechaInicio!
      : form.value.fechaFin!;

    const payload: JornadaRequest = {
      nombre:      form.value.nombre      || undefined,
      fechaInicio: form.value.fechaInicio!,
      fechaFin,
      horaInicio:  `${form.value.horaInicio}:00`,
      horaFin:     `${form.value.horaFin}:00`,
      tipoJornada: form.value.tipoJornada,
      modalidad:   form.value.modalidad,
      descripcion: form.value.descripcion || undefined,
      idLugar:     form.value.idLugar ?? undefined,
    };

    if (form.value.modalidad === 'PARTIDA') {
      payload.horaInicioTarde = `${form.value.horaInicioTarde}:00`;
      payload.horaFinTarde    = `${form.value.horaFinTarde}:00`;
    }
    if (form.value.diasDefault.size > 0) {
      payload.diasDefault = [...form.value.diasDefault].sort().join(',');
    }

    if (isEdit.value) {
      await updateJornada(form.value.idJornada!, payload);
      message.success('Turno actualizado');
    } else {
      const creado = await createJornada(payload);
      // Asignar titulares seleccionados
      if (titularesSelec.value.length > 0) {
        try {
          const overrides: Record<string, { horaInicio: string; horaFin: string }> = {};
          for (const id of titularesSelec.value) {
            const h = horariosIndividuales.value[id];
            if (h && !h.usarGeneral && h.inicio && h.fin) {
              overrides[String(id)] = { horaInicio: h.inicio, horaFin: h.fin };
            }
          }
          await asignarTitulares(creado.idJornada, {
            idTitulares: titularesSelec.value,
            horariosIndividuales: Object.keys(overrides).length > 0 ? overrides : undefined,
          });
          message.success(`Turno creado · ${titularesSelec.value.length} titular(es) asignado(s)`);
        } catch {
          message.warning('Turno creado, pero hubo un error al asignar el personal');
        }
      } else {
        message.success('Turno creado');
      }
    }
    modalApi.close();
    emit('success');
  } catch (error: any) {
    message.error(error?.message ?? 'Error al guardar la jornada');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <Modal
    :title="isEdit ? '✏️ Editar Turno' : '📅 Nuevo Turno — Días, Horario y Personal'"
    :footer="false"
    style="width: 880px"
  >
    <NForm label-placement="top" :show-require-mark="false">
<!-- ── 1. Tipo de turno ──────────────────────────────────────── -->
      <p class="sec-label">Tipo de Turno</p>
      <div class="tipo-grid">
        <div
          v-for="t in TIPOS_CONFIG"
          :key="t.value"
          class="tipo-card"
          :class="{ 'tipo-card--active': form.tipoJornada === t.value }"
          :style="form.tipoJornada === t.value
            ? { borderColor: t.color, background: `${t.color}12` }
            : {}"
          @click="seleccionarTipo(t.value)"
        >
          <span class="tipo-card__icon">{{ t.icon }}</span>
          <span class="tipo-card__label">{{ t.label }}</span>
          <span class="tipo-card__sub">{{ t.sub }}</span>
        </div>
      </div>

      <!-- ── 2. Período ────────────────────────────────────────────────── -->
      <p class="sec-label" style="margin-top:22px">Período</p>
      <NRadioGroup v-model:value="form.periodoDias" size="medium">
        <NRadioButton value="unico">📅 Un solo día</NRadioButton>
        <NRadioButton value="rango">🗓️ Varios días</NRadioButton>
      </NRadioGroup>

      <NGrid
        :cols="form.periodoDias === 'rango' ? 2 : 1"
        :x-gap="16"
        style="margin-top:12px"
      >
        <NGridItem>
          <NFormItem :label="form.periodoDias === 'unico' ? 'Fecha *' : 'Fecha inicio *'">
            <NDatePicker
              v-model:formatted-value="form.fechaInicio"
              type="date"
              value-format="yyyy-MM-dd"
              clearable
              style="width:100%"
            />
          </NFormItem>
        </NGridItem>
        <NGridItem v-if="form.periodoDias === 'rango'">
          <NFormItem label="Fecha fin *">
            <NDatePicker
              v-model:formatted-value="form.fechaFin"
              type="date"
              value-format="yyyy-MM-dd"
              clearable
              style="width:100%"
            />
          </NFormItem>
        </NGridItem>
      </NGrid>

      <!-- ── 3. Horario ────────────────────────────────────────────────── -->
      <p class="sec-label" style="margin-top:4px">Horario</p>

      <!-- Siempre: un par Entrada → Salida -->
      <div class="horario-row">
        <div class="horario-item">
          <NFormItem label="Entrada *">
            <NTimePicker
              v-model:formatted-value="form.horaInicio"
              format="HH:mm"
              clearable
              :minutes-step="5"
              style="width:100%"
            />
          </NFormItem>
        </div>
        <div class="horario-arrow">→</div>
        <div class="horario-item">
          <NFormItem label="Salida *">
            <NTimePicker
              v-model:formatted-value="form.horaFin"
              format="HH:mm"
              clearable
              :minutes-step="5"
              style="width:100%"
            />
          </NFormItem>
        </div>
      </div>

      <!-- Indicador cruce de medianoche (turno nocturno) -->
      <div v-if="cruzaMedianoche" class="medianoche-badge">
        <span>🌙 Turno nocturno — termina al día siguiente</span>
        <span class="medianoche-hint">
          Inicia {{ form.horaInicio }} hs · finaliza {{ form.horaFin }} hs del día siguiente
          (los días seleccionados son los días de <strong>inicio</strong>)
        </span>
      </div>

      <!-- Botón para activar horario partido — SOLO para tipo Administrativa -->
      <div v-if="form.tipoJornada === 'ADMINISTRATIVA'" style="margin-top:10px">
        <button
          type="button"
          class="partido-toggle"
          :class="{ 'partido-toggle--activo': form.modalidad === 'PARTIDA' }"
          @click="form.modalidad = form.modalidad === 'PARTIDA' ? 'CONTINUA' : 'PARTIDA'"
        >
          {{ form.modalidad === 'PARTIDA' ? '☑ Horario partido activado' : '☐ Activar horario partido (mañana + tarde)' }}
        </button>
      </div>

      <!-- Bloque tarde: SOLO cuando Administrativa + Partido activado -->
      <div
        v-if="form.tipoJornada === 'ADMINISTRATIVA' && form.modalidad === 'PARTIDA'"
        class="turno-block"
        style="margin-top:10px"
      >
        <p class="turno-label">🌆 Turno Tarde</p>
        <div class="horario-row">
          <div class="horario-item">
            <NFormItem label="Entrada tarde *">
              <NTimePicker
                v-model:formatted-value="form.horaInicioTarde"
                format="HH:mm"
                clearable
                :minutes-step="5"
                style="width:100%"
              />
            </NFormItem>
          </div>
          <div class="horario-arrow">→</div>
          <div class="horario-item">
            <NFormItem label="Salida tarde *">
              <NTimePicker
                v-model:formatted-value="form.horaFinTarde"
                format="HH:mm"
                clearable
                :minutes-step="5"
                style="width:100%"
              />
            </NFormItem>
          </div>
        </div>
      </div>

      <!-- ── 3b. Lugar / Fuente laboral (geocerca del turno) ─────────────── -->
      <p class="sec-label" style="margin-top:22px">Lugar / Fuente laboral</p>
      <NFormItem>
        <NSelect
          v-model:value="form.idLugar"
          :options="lugaresOpts"
          filterable
          clearable
          placeholder="Selecciona el lugar de trabajo (define la zona del control GPS)"
        />
      </NFormItem>
      <p class="personal-tip" style="color:#64748b; margin-top:-4px">
        📍 Define la geocerca del turno. <strong>Sin lugar el sistema no puede verificar
        si el personal está dentro de su zona</strong> (el GPS quedará siempre "en zona").
      </p>

      <!-- ── 4. Días específicos del turno (solo en rango) ───────────────── -->
      <template v-if="form.periodoDias === 'rango' && diasDelMes.length > 0">
        <NDivider style="margin: 18px 0 12px">
          <span class="div-label">DÍAS DEL TURNO EN EL MES</span>
        </NDivider>
        <p style="font-size:12px;color:#888;margin-bottom:8px">
          Selecciona los días específicos en que trabaja este equipo (opcional).
          Si no se selecciona ninguno, se asume que trabajan todos los días del rango.
        </p>
        <div class="dias-grid">
          <div
            v-for="dia in diasDelMes"
            :key="dia.fecha"
            class="dia-chip"
            :class="{
              'dia-chip--activo': form.diasDefault.has(dia.fecha),
              'dia-chip--fuera': !dia.enRango,
            }"
            @click="dia.enRango && toggleDia(dia.fecha)"
          >
            <span class="dia-chip__num">{{ dia.diaNum }}</span>
            <span class="dia-chip__semana">{{ dia.diaSemana }}</span>
          </div>
        </div>
        <div style="display:flex;gap:8px;margin-top:8px;align-items:center">
          <NButton size="tiny" @click="seleccionarTodosMes">Todos</NButton>
          <NButton size="tiny" @click="form.diasDefault = new Set()">Limpiar</NButton>
          <span style="font-size:12px;color:#888">
            {{ form.diasDefault.size }} día(s) seleccionado(s)
          </span>
        </div>
      </template>

      <!-- ── 5. Personal del Turno (solo al crear) ─────────────────────── -->
      <template v-if="!isEdit">
        <NDivider style="margin: 18px 0 12px">
          <span class="div-label">PERSONAL DEL TURNO</span>
        </NDivider>

        <NSpin :show="cargandoPerso" size="small">
          <!-- Titulares: médicos, enfermeros, etc. -->
          <NFormItem label="👨‍⚕️ Médicos / Enfermeros / Titulares">
            <NSelect
              v-model:value="titularesSelec"
              :options="titularesOpts"
              :render-label="renderTitularLabel"
              multiple
              filterable
              clearable
              placeholder="Buscar y seleccionar personal médico..."
              :max-tag-count="4"
            />
          </NFormItem>
          <p v-if="titularesSelec.length > 0" class="personal-tip">
            ✅ {{ titularesSelec.length }} titular(es) —
            trabajarán los {{ form.diasDefault.size > 0 ? form.diasDefault.size : 'todos los' }} días marcados arriba
          </p>
          <p v-if="titularesDisp.length === 0 && !cargandoPerso" class="personal-tip" style="color:#f97316">
            ⚠ No hay personal disponible. Verifica la conexión con el servidor.
          </p>
          <p class="personal-tip" style="color:#64748b">
            🎓 Los internos se asignan por separado desde el detalle del turno, donde puedes definir los días exactos por cada uno.
          </p>
        </NSpin>

        <!-- ══ HORARIOS INDIVIDUALES POR TITULAR ════════════════════════════ -->
        <template v-if="titularesSelec.length > 0">
          <NDivider style="margin: 18px 0 12px">
            <span class="div-label">⏰ HORARIOS POR PERSONA</span>
          </NDivider>

          <div
            class="rounded-xl p-4 space-y-3"
            style="border: 1px solid hsl(var(--border)); background: hsl(var(--card))"
          >
            <div class="flex items-center justify-between mb-1">
              <p style="font-size:12px;color:#64748b;margin:0">
                Horario general del turno: <strong>{{ form.horaInicio }} – {{ form.horaFin }}</strong>.
                Cambia a <strong>"Específico"</strong> para asignar un horario distinto a alguien.
              </p>
              <span
                v-if="titularesConOverride.length > 0"
                style="font-size:.72rem;font-weight:600;color:#0f766e;background:#0f766e18;padding:2px 10px;border-radius:12px"
              >
                {{ titularesConOverride.length }} con horario propio
              </span>
            </div>

            <div class="space-y-2">
              <template v-for="id in titularesSelec" :key="'sch-' + id">
                <div
                  v-if="horariosIndividuales[id]"
                  class="rounded-lg px-3 py-2.5"
                  style="background: hsl(var(--muted)); border: 1px solid hsl(var(--border))"
                >
                  <div class="flex items-center justify-between mb-2">
                    <div class="flex items-center gap-2">
                      <span
                        class="inline-block w-7 h-7 rounded-full text-white text-xs font-bold flex items-center justify-center flex-shrink-0"
                        style="background:#e63946"
                      >
                        {{ nombreTitular(id).charAt(0).toUpperCase() }}
                      </span>
                      <span class="text-xs font-semibold" style="color: hsl(var(--foreground))">
                        {{ nombreTitular(id) }}
                      </span>
                    </div>
                    <NSwitch
                      v-model:value="getHorarioEntry(id).usarGeneral"
                      size="small"
                    >
                      <template #checked>
                        <span style="font-size:.68rem">General</span>
                      </template>
                      <template #unchecked>
                        <span style="font-size:.68rem">Específico</span>
                      </template>
                    </NSwitch>
                  </div>

                  <div
                    v-if="!getHorarioEntry(id).usarGeneral"
                    class="flex gap-3 items-center"
                  >
                    <div class="flex-1">
                      <div class="text-xs mb-1" style="color: hsl(var(--muted-foreground))">Entrada</div>
                      <NTimePicker
                        v-model:formatted-value="getHorarioEntry(id).inicio"
                        format="HH:mm:ss"
                        value-format="HH:mm:ss"
                        :hour-step="1"
                        :minute-step="15"
                        :second-step="0"
                        style="width: 100%"
                        size="small"
                        placeholder="Hora entrada"
                      />
                    </div>
                    <div style="color:hsl(var(--muted-foreground));font-size:1rem;padding-top:18px">→</div>
                    <div class="flex-1">
                      <div class="text-xs mb-1" style="color: hsl(var(--muted-foreground))">Salida</div>
                      <NTimePicker
                        v-model:formatted-value="getHorarioEntry(id).fin"
                        format="HH:mm:ss"
                        value-format="HH:mm:ss"
                        :hour-step="1"
                        :minute-step="15"
                        :second-step="0"
                        style="width: 100%"
                        size="small"
                        placeholder="Hora salida"
                      />
                    </div>
                  </div>

                  <div
                    v-else
                    class="text-xs"
                    style="color: hsl(var(--muted-foreground))"
                  >
                    Usará el horario general: {{ form.horaInicio }} – {{ form.horaFin }}
                  </div>
                </div>
              </template>
            </div>
          </div>
        </template>
      </template>

      <!-- ── 6. Detalles opcionales ─────────────────────────────────────── -->
      <NDivider style="margin: 18px 0 12px">
        <span class="div-label">DETALLES OPCIONALES</span>
      </NDivider>

      <NGrid :cols="1" :x-gap="16">
        <NGridItem>
          <NFormItem label="Nombre descriptivo">
            <NInput
              v-model:value="form.nombre"
              placeholder="Ej: &quot;Guardia UCI · Sem. 22&quot; o &quot;Rotación Cirugía Mayo&quot;"
              maxlength="200"
              clearable
            />
          </NFormItem>
        </NGridItem>
        <NGridItem>
          <NFormItem label="Descripción / Instrucciones">
            <NInput
              v-model:value="form.descripcion"
              type="textarea"
              :autosize="{ minRows: 2, maxRows: 4 }"
              placeholder="Área de cobertura, personal mínimo requerido, instrucciones especiales…"
              maxlength="500"
            />
          </NFormItem>
        </NGridItem>
      </NGrid>

      <!-- ── Acciones ───────────────────────────────────────────────────── -->
      <div class="flex justify-end gap-2 mt-4">
        <NButton @click="modalApi.close()">Cancelar</NButton>
        <NButton
          type="primary"
          :loading="loading"
          :style="{ background: colorActivo, borderColor: colorActivo }"
          @click="handleSubmit"
        >
          {{ isEdit ? 'Actualizar Turno' : 'Crear Turno' }}
        </NButton>
      </div>
</NForm>
  </Modal>
</template>

<style scoped>
/* ── Labels de sección ──────────────────────────────────────────────────── */
.sec-label {
  font-size: 0.70rem;
  font-weight: 700;
  color: hsl(var(--muted-foreground));
  letter-spacing: 0.9px;
  text-transform: uppercase;
  margin: 0 0 10px;
}
.div-label {
  font-size: 0.66rem;
  color: hsl(var(--muted-foreground));
  font-weight: 600;
  letter-spacing: 0.5px;
}

/* ── Grid de tipos ──────────────────────────────────────────────────────── */
.tipo-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 7px;
}
.tipo-card {
  border: 2px solid hsl(var(--border));
  border-radius: 11px;
  padding: 11px 6px 9px;
  text-align: center;
  cursor: pointer;
  transition: border-color 0.14s, background 0.14s, transform 0.1s;
  user-select: none;
  background: transparent;
}
.tipo-card:hover {
  border-color: hsl(var(--muted-foreground));
  background: hsl(var(--accent));
  transform: translateY(-1px);
}
.tipo-card--active {
  box-shadow: 0 3px 12px rgba(0, 0, 0, 0.2);
}
.tipo-card__icon  { font-size: 1.3rem; display: block; }
.tipo-card__label { font-size: 0.73rem; font-weight: 700; display: block; margin-top: 5px; color: hsl(var(--foreground)); }
.tipo-card__sub   { font-size: 0.62rem; color: hsl(var(--muted-foreground)); display: block; margin-top: 2px; line-height: 1.3; }

/* ── Botón activar horario partido (solo Administrativa) ────────────────── */
.partido-toggle {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  background: hsl(var(--muted));
  border: 1.5px solid hsl(var(--border));
  border-radius: 8px;
  padding: 6px 14px;
  font-size: 0.74rem;
  font-weight: 600;
  color: hsl(var(--muted-foreground));
  cursor: pointer;
  transition: border-color .14s, background .14s, color .14s;
}
.partido-toggle:hover {
  border-color: #0369a1;
  color: #0369a1;
  background: #eff6ff;
}
.partido-toggle--activo {
  background: #eff6ff;
  border-color: #0369a1;
  color: #0369a1;
}

/* ── Horario ────────────────────────────────────────────────────────────── */
.horario-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.horario-item   { flex: 1; }
.horario-arrow  {
  color: hsl(var(--muted-foreground));
  font-size: 1.1rem;
  padding-top: 30px;
  flex-shrink: 0;
}

/* ── Turno blocks (horario partido) ─────────────────────────────────────── */
.turno-block {
  background: hsl(var(--muted));
  border: 1px solid hsl(var(--border));
  border-radius: 10px;
  padding: 12px 14px 4px;
}
.turno-label {
  font-size: 0.72rem;
  font-weight: 700;
  color: hsl(var(--muted-foreground));
  margin: 0 0 8px;
}

/* ── Calendario de días del turno ─────────────────────────────────────────── */
.dias-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 5px;
}
.dia-chip {
  display: flex;
  flex-direction: column;
  align-items: center;
  border-radius: 8px;
  padding: 5px 2px;
  cursor: pointer;
  border: 1.5px solid hsl(var(--border));
  background: transparent;
  transition: all .15s;
  user-select: none;
}
.dia-chip:hover:not(.dia-chip--fuera) {
  border-color: #18a058;
  background: #f0fff4;
}
.dia-chip--activo {
  background: #18a058;
  border-color: #18a058;
  color: white;
}
.dia-chip--fuera {
  opacity: .3;
  cursor: not-allowed;
}
.dia-chip__num    { font-size: 13px; font-weight: 700; line-height: 1; }
.dia-chip__semana { font-size: 8px;  opacity: .75; text-transform: uppercase; }

/* ── Indicador cruce de medianoche ─────────────────────────────────────────── */
/* ── Personal tip ──────────────────────────────────────────────────────────── */
.personal-tip {
  font-size: 0.71rem;
  color: hsl(var(--muted-foreground));
  background: hsl(var(--muted));
  border-radius: 6px;
  padding: 5px 10px;
  margin: -8px 0 12px;
  line-height: 1.4;
}

/* ── Indicador cruce de medianoche ─────────────────────────────────────────── */
.medianoche-badge {
  display: flex;
  flex-direction: column;
  gap: 4px;
  background: #1e1b4b;
  border: 1px solid #4338ca;
  color: #a5b4fc;
  border-radius: 10px;
  padding: 10px 14px;
  font-size: 0.76rem;
  font-weight: 600;
  margin-top: 10px;
}
.medianoche-hint {
  font-size: 0.68rem;
  color: #818cf8;
  font-weight: 400;
  line-height: 1.4;
}
.medianoche-hint strong { color: #c7d2fe; }
</style>
