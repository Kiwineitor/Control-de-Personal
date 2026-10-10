<script lang="ts" setup>
import type { DataTableColumns, SelectOption } from 'naive-ui';

import type { ActividadJornadaDto, ActividadJornadaRequest, ActividadRequest, EstadoActividad as EstadoAct, TipoActividad } from '#/api/sisu/actividades';
import type { BitacoraEntrada } from '#/api/sisu/bitacora';
import type {
  EstadoAsignacion, EstadoJornada, InternoJornadaDto,
  JornadaDto, TipoJornada, TitularJornadaDto,
} from '#/api/sisu/jornada';

import { computed, h, onMounted, ref } from 'vue';

import { Page, useVbenModal } from '@vben/common-ui';

import {
  NButton, NCard, NCheckbox, NDataTable, NDivider, NDrawer, NDrawerContent, NEmpty,
  NGrid, NGridItem, NInput, NModal, NSelect,
  NSpace, NSpin, NTabPane, NTabs, NTag, NTimePicker,
} from 'naive-ui';

import { dialog, message } from '#/adapter/naive';
import { useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  asignarActividad, cambiarEstadoActividad,
  createActividad, getActividadesJornada, getCatalogoActivo, removerActividadJornada,
} from '#/api/sisu/actividades';
import { bitacoraJornadaAdmin } from '#/api/sisu/bitacora';
import {
  actualizarHorarioTitular, cambiarEstadoInterno,
  cambiarEstadoTitular, cancelarJornada,
  cerrarJornada, getInternosJornada, getJornadas,
  getJornadasActivas,
  getTitularesJornada, removerInterno, removerTitular,
} from '#/api/sisu/jornada';

import { ESTADO_ACTIVIDAD_META, TIPO_ACTIVIDAD_META } from '../actividades/data';
import { type ActionParams, ESTADO_JORNADA_META, TIPO_META, useJornadaColumns } from './data';
import CalendarioMensual   from './modules/calendario-mensual.vue';
import FormAsignarInterno  from './modules/form-asignar-interno.vue';
import FormAsignarTitular  from './modules/form-asignar-titular.vue';
import FormHorarioInterno  from './modules/form-horario-interno.vue';
import FormJornada         from './modules/form-jornada.vue';
import FormRolTurno        from './modules/form-rol-turno.vue';

// ════════════════════════════════════════════════════════════════════════════
//  MODAL — NUEVA / EDITAR JORNADA (se usa desde el grid, no desde el drawer)
// ════════════════════════════════════════════════════════════════════════════

const [FormJornadaModal, formJornadaApi] = useVbenModal({
  connectedComponent: FormJornada, destroyOnClose: true,
});

// ════════════════════════════════════════════════════════════════════════════
//  STATS
// ════════════════════════════════════════════════════════════════════════════

// ════════════════════════════════════════════════════════════════════════════
//  VISTA: Lista | Calendario
// ════════════════════════════════════════════════════════════════════════════

type Vista = 'calendario' | 'lista';
const vistaActual = ref<Vista>('lista');

const statsActivas = ref(0);
const statsEnCurso = ref(0);

async function cargarStats() {
  try {
    const lista       = await getJornadasActivas();
    statsActivas.value = lista.length;
    statsEnCurso.value = lista.filter(j => j.estaEnCurso).length;
  } catch { /* silencioso */ }
}

// ════════════════════════════════════════════════════════════════════════════
//  FILTROS + GRID
// ════════════════════════════════════════════════════════════════════════════

const ESTADO_OPTIONS: SelectOption[] = [
  { label: 'Todos',         value: ''          },
  { label: '✅ Activas',   value: 'ACTIVA'    },
  { label: '🔒 Cerradas',  value: 'CERRADA'   },
  { label: '✕ Canceladas', value: 'CANCELADA' },
];

const TIPO_OPTIONS: SelectOption[] = [
  { label: 'Todos los tipos',    value: ''            },
  { label: '📋 Regular',        value: 'REGULAR'     },
  { label: '🛡️ Guardia 12h',   value: 'GUARDIA_12H' },
  { label: '🔒 Guardia 24h',    value: 'GUARDIA_24H' },
  { label: '🌙 Nocturna',       value: 'NOCTURNA'    },
  { label: '🔄 Rotación',       value: 'ROTACION'    },
  { label: '📞 De Llamada',     value: 'LLAMADA'     },
  { label: '🚨 Emergencia',     value: 'EMERGENCIA'  },
  { label: '📌 Cobertura',      value: 'COBERTURA'      },
  { label: '🏢 Administrativa', value: 'ADMINISTRATIVA' },
  { label: '🔒 Guardia (ant.)', value: 'GUARDIA'        },
];

const filtros = ref({
  q:        '',
  estado:   '' as string,
  tipo:     '' as string,
  fechaIni: null as null | string,
  fechaFin: null as null | string,
});

function limpiarFiltros() {
  filtros.value = { q: '', estado: '', tipo: '', fechaIni: null, fechaFin: null };
  gridApi.query();
}

function onAction({ code, row }: ActionParams) {
  switch (code) {
    case 'cancelar': { confirmarCancelar(row);    break;
    }
    case 'cerrar': {  confirmarCerrar(row);       break;
    }
    case 'edit': {    formJornadaApi.setData(row).open(); break;
    }
    case 'view': {    abrirDrawer(row);           break;
    }
  }
}

const [Grid, gridApi] = useVbenVxeGrid<JornadaDto>({
  gridOptions: {
    columns:     useJornadaColumns(onAction),
    maxHeight:   520,
    pagerConfig: { enabled: true, pageSize: 20 },
    proxyConfig: {
      autoLoad: true,
      ajax: {
        query: async ({ page }: { page: { currentPage: number; pageSize: number } }) =>
          getJornadas({
            page:     page.currentPage - 1,
            size:     page.pageSize,
            q:        filtros.value.q        || undefined,
            estado:   (filtros.value.estado  || undefined) as EstadoJornada | undefined,
            tipo:     (filtros.value.tipo    || undefined) as TipoJornada   | undefined,
            fechaIni: filtros.value.fechaIni ?? undefined,
            fechaFin: filtros.value.fechaFin ?? undefined,
          }),
      },
    },
    toolbarConfig: { refresh: true, zoom: true, custom: true },
  },
});

function confirmarCerrar(row: JornadaDto) {
  dialog.warning({
    title:        'Cerrar Jornada',
    content:      `¿Cerrar "${etiquetaJornada(row)}"? Quedará marcada como finalizada.`,
    positiveText: 'Sí, cerrar',
    negativeText: 'Cancelar',
    onPositiveClick: async () => {
      await cerrarJornada(row.idJornada);
      message.success('Jornada cerrada correctamente');
      gridApi.query();
      cargarStats();
      if (jornadaSeleccionada.value?.idJornada === row.idJornada)
        drawerVisible.value = false;
    },
  });
}

function confirmarCancelar(row: JornadaDto) {
  dialog.warning({
    title:        'Cancelar Jornada',
    content:      `¿Cancelar "${etiquetaJornada(row)}"? Esta acción no se puede deshacer.`,
    positiveText: 'Sí, cancelar',
    negativeText: 'No',
    onPositiveClick: async () => {
      await cancelarJornada(row.idJornada);
      message.success('Jornada cancelada');
      gridApi.query();
      cargarStats();
      if (jornadaSeleccionada.value?.idJornada === row.idJornada)
        drawerVisible.value = false;
    },
  });
}

// ════════════════════════════════════════════════════════════════════════════
//  DRAWER — DETALLE JORNADA
// ════════════════════════════════════════════════════════════════════════════

const drawerVisible       = ref(false);
const jornadaSeleccionada = ref<JornadaDto | null>(null);
const tabDrawer           = ref<'actividades' | 'bitacora' | 'internos' | 'titulares'>('titulares');
const loadingDrawer       = ref(false);
const titularesJornada    = ref<TitularJornadaDto[]>([]);
const internosJornada     = ref<InternoJornadaDto[]>([]);
const actividadesJornada  = ref<ActividadJornadaDto[]>([]);
const bitacoraJornada     = ref<BitacoraEntrada[]>([]);
const bitacoraLoading     = ref(false);

// ── Catálogo de actividades (se carga una sola vez) ────────────────────────
const catalogoOpts    = ref<SelectOption[]>([]);
const catalogoLoading = ref(false);

async function cargarCatalogo() {
  if (catalogoOpts.value.length > 0) return;
  catalogoLoading.value = true;
  try {
    const lista = await getCatalogoActivo();
    catalogoOpts.value = lista.map(a => ({
      label: `${a.nombre}  ·  ${TIPO_ACTIVIDAD_META[a.tipoActividad]?.label ?? a.tipoActividad}`,
      value: a.idActividad,
    }));
  } finally {
    catalogoLoading.value = false;
  }
}

// ── Quick-assign actividades ───────────────────────────────────────────────
const quickAssign = ref({
  personaKey:  null as null | string,
  idActividad: null as null | number,
  loading:     false,
});

const personaOpts = computed<SelectOption[]>(() => [
  ...titularesJornada.value.map(t => ({
    label: `👨‍⚕️ ${t.nombreCompleto}`,
    value: `T:${t.idTitularJornada}`,
  })),
  ...internosJornada.value.map(i => ({
    label: `🎓 ${i.nombreCompleto}`,
    value: `I:${i.idInternoJornada}`,
  })),
]);

async function asignarActividadQuick() {
  const { personaKey, idActividad } = quickAssign.value;
  if (!personaKey || !idActividad || !jornadaSeleccionada.value) return;
  const [tipo, idStr] = personaKey.split(':');
  const idPersona = Number(idStr);
  quickAssign.value.loading = true;
  try {
    const req: ActividadJornadaRequest = {
      idActividad,
      idTitularJornada: tipo === 'T' ? idPersona : undefined,
      idInternoJornada: tipo === 'I' ? idPersona : undefined,
    };
    await asignarActividad(req);
    message.success('Actividad asignada');
    quickAssign.value.idActividad = null;
    await recargarDetalle(jornadaSeleccionada.value.idJornada);
  } catch (error: any) {
    message.error(error?.message ?? 'Error al asignar la actividad');
  } finally {
    quickAssign.value.loading = false;
  }
}

function irAAsignarActividad(personaKey: string) {
  quickAssign.value.personaKey = personaKey;
  tabDrawer.value = 'actividades';
}

// ── Crear nueva actividad desde el drawer ─────────────────────────────────

const TIPO_ACT_OPTIONS: SelectOption[] = [
  { label: 'Clínica',        value: 'CLINICA'        },
  { label: 'Administrativa', value: 'ADMINISTRATIVA' },
  { label: 'Académica',      value: 'ACADEMICA'      },
  { label: 'Supervisión',    value: 'SUPERVISION'    },
  { label: 'Emergencia',     value: 'EMERGENCIA'     },
  { label: 'Otra',           value: 'OTRA'           },
];

const mostrarFormNueva = ref(false);
const loadingNuevaAct  = ref(false);
const formNuevaAct     = ref({ nombre: '', tipoActividad: 'CLINICA' as TipoActividad, descripcion: '' });

function toggleFormNueva() {
  mostrarFormNueva.value = !mostrarFormNueva.value;
  if (mostrarFormNueva.value) formNuevaAct.value = { nombre: '', tipoActividad: 'CLINICA', descripcion: '' };
}

async function crearNuevaActividad() {
  if (!formNuevaAct.value.nombre.trim()) { message.warning('El nombre es obligatorio'); return; }
  loadingNuevaAct.value = true;
  try {
    const payload: ActividadRequest = {
      nombre:        formNuevaAct.value.nombre.trim(),
      tipoActividad: formNuevaAct.value.tipoActividad,
      descripcion:   formNuevaAct.value.descripcion || undefined,
      activo:        true,
    };
    const nueva = await createActividad(payload);
    const opt: SelectOption = {
      label: `${nueva.nombre}  ·  ${TIPO_ACTIVIDAD_META[nueva.tipoActividad]?.label ?? nueva.tipoActividad}`,
      value: nueva.idActividad,
    };
    catalogoOpts.value = [opt, ...catalogoOpts.value];
    quickAssign.value.idActividad = nueva.idActividad;
    mostrarFormNueva.value = false;
    message.success(`Actividad "${nueva.nombre}" creada y seleccionada`);
  } catch (error: any) {
    message.error(error?.message ?? 'Error al crear la actividad');
  } finally {
    loadingNuevaAct.value = false;
  }
}

// ── Abrir / Recargar drawer ────────────────────────────────────────────────

async function abrirDrawer(row: JornadaDto) {
  jornadaSeleccionada.value = row;
  drawerVisible.value       = true;
  tabDrawer.value           = 'titulares';
  bitacoraCargada           = false;
  quickAssign.value         = { personaKey: null, idActividad: null, loading: false };
  await recargarDetalle(row.idJornada);
  cargarCatalogo();
}

async function recargarDetalle(id: number) {
  loadingDrawer.value = true;
  try {
    const [t, i, a] = await Promise.all([
      getTitularesJornada(id),
      getInternosJornada(id),
      getActividadesJornada(id),
    ]);
    titularesJornada.value   = t;
    internosJornada.value    = i;
    actividadesJornada.value = a;
  } finally {
    loadingDrawer.value = false;
  }
}

// ── Modales asignar (controlados con v-model dentro del drawer) ────────────

const asignarTitularVisible  = ref(false);
const asignarInternoVisible  = ref(false);
const rolTurnoVisible        = ref(false);
const horarioInternoVisible  = ref(false);

// ── Bitácora: carga perezosa al abrir pestaña ─────────────────────────────

let bitacoraCargada = false;

async function cargarBitacoraJornada() {
  if (!jornadaSeleccionada.value || bitacoraCargada) return;
  bitacoraLoading.value = true;
  try {
    bitacoraJornada.value = await bitacoraJornadaAdmin(jornadaSeleccionada.value.idJornada);
    bitacoraCargada = true;
  } catch {
    bitacoraJornada.value = [];
  } finally {
    bitacoraLoading.value = false;
  }
}

function onTabChange(name: string) {
  if (name === 'bitacora') cargarBitacoraJornada();
}

function abrirAsignarTitular() {
  if (!jornadaSeleccionada.value) return;
  asignarTitularVisible.value = true;
}

function abrirAsignarInterno() {
  if (!jornadaSeleccionada.value) return;
  asignarInternoVisible.value = true;
}

async function onAsignarTitularSuccess() {
  asignarTitularVisible.value = false;
  if (!jornadaSeleccionada.value) return;
  await recargarDetalle(jornadaSeleccionada.value.idJornada);
  gridApi.query();
}

async function onAsignarInternoSuccess() {
  asignarInternoVisible.value = false;
  if (!jornadaSeleccionada.value) return;
  await recargarDetalle(jornadaSeleccionada.value.idJornada);
  gridApi.query();
}

// ── Editar horario de un titular ya asignado ─────────────────────────────
// Antes el horario solo se podía fijar al asignar: corregirlo obligaba a quitar
// al titular y volver a agregarlo, arrastrando sus actividades y su bitácora.

const editarHorario = ref({
  visible:     false,
  guardando:   false,
  row:         null as null | TitularJornadaDto,
  inicio:      null as null | string,
  fin:         null as null | string,
  inicioTarde: null as null | string,
  finTarde:    null as null | string,
  haceManana:  true,
  haceTarde:   true,
});

const horarioSinFranja = computed(() =>
  !!editarHorario.value.row?.jornadaPartida
  && !editarHorario.value.haceManana
  && !editarHorario.value.haceTarde,
);

function abrirEditarHorario(row: TitularJornadaDto) {
  editarHorario.value = {
    visible:     true,
    guardando:   false,
    row,
    inicio:      row.horaInicioEfectiva ?? null,
    fin:         row.horaFinEfectiva ?? null,
    inicioTarde: row.horaInicioTardeEfectiva,
    finTarde:    row.horaFinTardeEfectiva,
    haceManana:  row.trabajaManana,
    haceTarde:   row.trabajaTarde,
  };
}

async function guardarHorario() {
  const e = editarHorario.value;
  if (!e.row || !jornadaSeleccionada.value) return;
  if (horarioSinFranja.value) {
    message.warning('Debe cubrir al menos una franja');
    return;
  }
  e.guardando = true;
  try {
    await actualizarHorarioTitular(
      jornadaSeleccionada.value.idJornada,
      e.row.idTitularJornada,
      {
        horaInicio:      e.inicio ?? '',
        horaFin:         e.fin ?? '',
        horaInicioTarde: e.inicioTarde,
        horaFinTarde:    e.finTarde,
        trabajaManana:   e.haceManana,
        trabajaTarde:    e.haceTarde,
      },
    );
    message.success('Horario actualizado');
    e.visible = false;
    await recargarDetalle(jornadaSeleccionada.value.idJornada);
  } catch (error: any) {
    message.error(error?.message ?? 'Error al actualizar el horario');
  } finally {
    e.guardando = false;
  }
}

// ── Remover personal ────────────────────────────────────────────────────

function quitarTitular(tj: TitularJornadaDto) {
  dialog.warning({
    title:        'Quitar Titular',
    content:      `¿Quitar a ${tj.nombreCompleto} de esta jornada?`,
    positiveText: 'Quitar',
    negativeText: 'Cancelar',
    onPositiveClick: async () => {
      await removerTitular(jornadaSeleccionada.value!.idJornada, tj.idTitularJornada);
      message.success('Titular removido');
      await recargarDetalle(jornadaSeleccionada.value!.idJornada);
      gridApi.query();
    },
  });
}

function quitarInterno(ij: InternoJornadaDto) {
  dialog.warning({
    title:        'Quitar Interno',
    content:      `¿Quitar a ${ij.nombreCompleto} de esta jornada?`,
    positiveText: 'Quitar',
    negativeText: 'Cancelar',
    onPositiveClick: async () => {
      await removerInterno(jornadaSeleccionada.value!.idJornada, ij.idInternoJornada);
      message.success('Interno removido');
      await recargarDetalle(jornadaSeleccionada.value!.idJornada);
      gridApi.query();
    },
  });
}

function quitarActividad(aj: ActividadJornadaDto) {
  dialog.warning({
    title:        'Quitar Actividad',
    content:      `¿Quitar "${aj.nombreActividad}" de ${aj.nombrePersona}?`,
    positiveText: 'Quitar',
    negativeText: 'Cancelar',
    onPositiveClick: async () => {
      await removerActividadJornada(aj.idActividadJornada);
      message.success('Actividad removida');
      await recargarDetalle(jornadaSeleccionada.value!.idJornada);
    },
  });
}

// ── Modal cambiar estado de asignación ────────────────────────────────────

const ESTADO_ASIG_OPTIONS: SelectOption[] = [
  { label: '📋 Programado', value: 'PROGRAMADO' },
  { label: '✅ Confirmado', value: 'CONFIRMADO' },
  { label: '▶ En curso',   value: 'EN_CURSO'   },
  { label: '✔ Completado', value: 'COMPLETADO' },
  { label: '✕ Cancelado',  value: 'CANCELADO'  },
];

const ESTADO_ACTIVIDAD_OPTIONS: SelectOption[] = [
  { label: '📋 Pendiente',  value: 'PENDIENTE'   },
  { label: '▶ En progreso', value: 'EN_PROGRESO' },
  { label: '✔ Completada',  value: 'COMPLETADA'  },
  { label: '✕ Cancelada',   value: 'CANCELADA'   },
];

const ESTADO_ASIG_META: Record<string, { label: string; type: any }> = {
  PROGRAMADO: { label: 'Programado', type: 'default' },
  CONFIRMADO: { label: 'Confirmado', type: 'info'    },
  EN_CURSO:   { label: 'En curso',   type: 'success' },
  COMPLETADO: { label: 'Completado', type: 'primary' },
  CANCELADO:  { label: 'Cancelado',  type: 'error'   },
};

const estadoModal = ref({
  visible:       false,
  tipo: 'titular' as 'actividad' | 'interno' | 'titular',
  idRow:         0,
  nuevoEstado:   '' as '' | EstadoAsignacion,
  observaciones: '',
  loading:       false,
});

function abrirCambioEstado(
  tipo: 'actividad' | 'interno' | 'titular',
  idRow: number,
  estadoActual: EstadoAsignacion,
) {
  estadoModal.value = {
    visible: true, tipo, idRow,
    nuevoEstado:   estadoActual,
    observaciones: '',
    loading:       false,
  };
}

async function confirmarCambioEstado() {
  const { tipo, idRow, nuevoEstado, observaciones } = estadoModal.value;
  if (!jornadaSeleccionada.value || !nuevoEstado) return;
  estadoModal.value.loading = true;
  try {
    if (tipo === 'titular') {
      await cambiarEstadoTitular(jornadaSeleccionada.value.idJornada, idRow, {
        estado: nuevoEstado as EstadoAsignacion,
        observaciones: observaciones || undefined,
      });
    } else if (tipo === 'actividad') {
      await cambiarEstadoActividad(idRow, {
        estado: nuevoEstado as EstadoAct,
        observaciones: observaciones || undefined,
      });
    } else {
      await cambiarEstadoInterno(jornadaSeleccionada.value.idJornada, idRow, {
        estado: nuevoEstado as EstadoAsignacion,
        observaciones: observaciones || undefined,
      });
    }
    message.success('Estado actualizado');
    estadoModal.value.visible = false;
    await recargarDetalle(jornadaSeleccionada.value.idJornada);
  } catch {
    message.error('Error al cambiar el estado');
  } finally {
    estadoModal.value.loading = false;
  }
}

// ── Columnas NDataTable del drawer ────────────────────────────────────────

const columnasTitulares = computed<DataTableColumns<TitularJornadaDto>>(() => [
  {
    title: 'Titular',
    key: 'nombreCompleto',
    render: (row) => h('div', [
      h('div', { style: { fontWeight: 600, fontSize: '0.83rem' } }, row.nombreCompleto),
      h('div', { style: { fontSize: '0.74rem', color: 'hsl(var(--muted-foreground))' } }, row.cargo ?? '—'),
    ]),
  },
  {
    // En jornada partida hay dos ventanas: mostrarlas ambas, y marcar las franjas
    // que el titular NO cubre — antes solo se veía la de la mañana.
    title: 'Horario', key: 'horaInicioEfectiva', width: 150,
    render(row) {
      const corto = (h1: null | string, h2: null | string) =>
        `${(h1 ?? '').slice(0, 5)}–${(h2 ?? '').slice(0, 5)}`;
      const overrideM = row.horaInicioOverride !== null;

      if (!row.jornadaPartida) {
        return h('span', { style: overrideM ? 'color: #0f766e; font-weight: 600' : '' },
          corto(row.horaInicioEfectiva, row.horaFinEfectiva));
      }

      const linea = (activa: boolean, etiqueta: string, texto: string, override: boolean) =>
        h('div', {
          style: activa
            ? `font-size:.76rem;${override ? 'color:#0f766e;font-weight:600' : ''}`
            : 'font-size:.76rem;opacity:.45;text-decoration:line-through',
        }, `${etiqueta} ${activa ? texto : '—'}`);

      return h('div', [
        linea(row.trabajaManana, '☀', corto(row.horaInicioEfectiva, row.horaFinEfectiva), overrideM),
        linea(row.trabajaTarde, '🌆',
          corto(row.horaInicioTardeEfectiva, row.horaFinTardeEfectiva),
          row.horaInicioTardeOverride !== null),
      ]);
    },
  },
  {
    title: 'Estado',
    key: 'estado',
    width: 110,
    render: (row) => {
      const m = ESTADO_ASIG_META[row.estado];
      return h(NTag, { type: m?.type ?? 'default', size: 'small', round: true }, () => m?.label ?? row.estado);
    },
  },
  {
    title: 'Acciones',
    key: '_acc',
    width: 190,
    render: (row) =>
      h(NSpace, { size: 4 }, () => [
        h(NButton, {
          size: 'tiny', type: 'success', ghost: true,
          title: 'Asignar actividad a este titular',
          onClick: () => irAAsignarActividad(`T:${row.idTitularJornada}`),
        }, () => '📋 Act.'),
        h(NButton, {
          size: 'tiny', type: 'info', ghost: true,
          title: 'Modificar el horario de este titular',
          onClick: () => abrirEditarHorario(row),
        }, () => '🕐'),
        h(NButton, {
          size: 'tiny', ghost: true,
          onClick: () => abrirCambioEstado('titular', row.idTitularJornada, row.estado),
        }, () => '↻'),
        h(NButton, {
          size: 'tiny', type: 'error', ghost: true,
          onClick: () => quitarTitular(row),
        }, () => '✕'),
      ]),
  },
]);

const columnasInternos = computed<DataTableColumns<InternoJornadaDto>>(() => [
  {
    title: 'Interno',
    key: 'nombreCompleto',
    render: (row) => h('div', [
      h('div', { style: { fontWeight: 600, fontSize: '0.83rem' } }, row.nombreCompleto),
      h('div', { style: { fontSize: '0.74rem', color: 'hsl(var(--muted-foreground))' } }, row.carrera ?? '—'),
      row.nombreResponsable
        ? h('div', { style: { fontSize: '0.72rem', color: '#7c3aed', marginTop: '1px' } },
            `👤 ${row.nombreResponsable}`)
        : null,
    ]),
  },
  {
    title: 'Rot.',
    key: 'estaEnRotacion',
    width: 70,
    render: (row) =>
      h(NTag, {
        type: row.estaEnRotacion ? 'info' : 'warning',
        size: 'tiny', round: true,
      }, () => row.estaEnRotacion ? '↻' : 'Fuera'),
  },
  {
    title: 'Horario', key: 'horaInicioEfectiva', width: 120,
    render(row) {
      const hasOverride = row.horaInicioOverride !== null;
      return h('span', { style: hasOverride ? 'color: #0f766e; font-weight: 600' : '' },
        `${(row.horaInicioEfectiva||'').slice(0,5)}–${(row.horaFinEfectiva||'').slice(0,5)}`);
    },
  },
  {
    title: 'Estado',
    key: 'estado',
    width: 110,
    render: (row) => {
      const m = ESTADO_ASIG_META[row.estado];
      return h(NTag, { type: m?.type ?? 'default', size: 'small', round: true }, () => m?.label ?? row.estado);
    },
  },
  {
    title: 'Acciones',
    key: '_acc',
    width: 155,
    render: (row) =>
      h(NSpace, { size: 4 }, () => [
        h(NButton, {
          size: 'tiny', type: 'success', ghost: true,
          title: 'Asignar actividad a este interno',
          onClick: () => irAAsignarActividad(`I:${row.idInternoJornada}`),
        }, () => '📋 Act.'),
        h(NButton, {
          size: 'tiny', ghost: true,
          onClick: () => abrirCambioEstado('interno', row.idInternoJornada, row.estado),
        }, () => '↻'),
        h(NButton, {
          size: 'tiny', type: 'error', ghost: true,
          onClick: () => quitarInterno(row),
        }, () => '✕'),
      ]),
  },
]);

const columnasActividades = computed<DataTableColumns<ActividadJornadaDto>>(() => [
  {
    title: 'Actividad', key: 'nombreActividad',
    render: (row) => h('div', [
      h('div', { style: { fontWeight: 600, fontSize: '0.83rem' } }, row.nombreActividad),
      h('div', {
        style: {
          display: 'inline-block', marginTop: '2px',
          padding: '0 7px', borderRadius: '8px', fontSize: '0.7rem', fontWeight: 600,
          background: TIPO_ACTIVIDAD_META[row.tipoActividad]?.bg   ?? '#f1f5f9',
          color:      TIPO_ACTIVIDAD_META[row.tipoActividad]?.color ?? '#475569',
        },
      }, TIPO_ACTIVIDAD_META[row.tipoActividad]?.label ?? row.tipoActividad),
    ]),
  },
  {
    title: 'Persona', key: 'nombrePersona', width: 150,
    render: (row) => h('div', [
      h('div', { style: { fontSize: '0.82rem', fontWeight: 500 } }, row.nombrePersona),
      h(NTag, {
        type: row.asignadoA === 'INTERNO' ? 'info' : 'error',
        size: 'tiny', round: true, style: { marginTop: '2px' },
      }, () => row.asignadoA),
    ]),
  },
  {
    title: 'Estado', key: 'estadoActividad', width: 110,
    render: (row) => {
      const m = ESTADO_ACTIVIDAD_META[row.estadoActividad];
      return h(NTag, { type: m?.type ?? 'default', size: 'small', round: true }, () => m?.label ?? row.estadoActividad);
    },
  },
  {
    title: 'Val.', key: 'validada', width: 48, align: 'center' as const,
    render: (row) => row.validada
      ? h('span', { title: `Validado por ${row.nombreValidador}`, style: { color: '#22c55e', fontSize: '1rem' } }, '✓')
      : h('span', { style: { color: '#cbd5e1' } }, '—'),
  },
  {
    title: 'Acc.', key: '_acc', width: 100,
    render: (row) => h(NSpace, { size: 4 }, () => [
      h(NButton, {
        size: 'tiny', ghost: true,
        onClick: () => abrirCambioEstado('actividad', row.idActividadJornada, row.estadoActividad as any),
      }, () => '↻'),
      h(NButton, {
        size: 'tiny', type: 'error', ghost: true,
        onClick: () => quitarActividad(row),
      }, () => '✕'),
    ]),
  },
]);

// ── Helpers ───────────────────────────────────────────────────────────────

function etiquetaJornada(j: JornadaDto) {
  return j.nombre || `${TIPO_META[j.tipoJornada]?.label ?? j.tipoJornada} · ${j.fechaInicio}`;
}

function fmtHora(horaStr: null | string | undefined) {
  return horaStr ? horaStr.slice(0, 5) : '—';
}

onMounted(cargarStats);
</script>

<template>
  <Page auto-content-height>
<!-- Modal: nueva / editar jornada -->
    <FormJornadaModal @success="() => { gridApi.query(); cargarStats(); }" />

    <!-- ── Cabecera ──────────────────────────────────────────────────── -->
    <div class="mb-4 flex items-center justify-between flex-wrap gap-3">
      <div>
        <h2 class="text-lg font-bold mb-0" style="color: hsl(var(--foreground))">Gestión de Jornadas</h2>
        <p class="text-sm mt-0.5" style="color: hsl(var(--muted-foreground))">
          Programación, asignación y seguimiento de jornadas del personal SISU
        </p>
      </div>
      <div class="flex gap-3 flex-wrap items-center">
        <div class="stat-badge">
          <span class="stat-badge__icon">📅</span>
          <div>
            <div class="stat-badge__label">Activas</div>
            <div class="stat-badge__value stat-badge__value--green">{{ statsActivas }}</div>
          </div>
        </div>
        <div class="stat-badge">
          <span class="stat-badge__icon">▶</span>
          <div>
            <div class="stat-badge__label">En curso ahora</div>
            <div class="stat-badge__value stat-badge__value--orange">{{ statsEnCurso }}</div>
          </div>
        </div>

        <!-- Toggle Vista -->
        <div class="vista-toggle">
          <button
            class="vista-toggle__btn"
            :class="{ 'vista-toggle__btn--active': vistaActual === 'lista' }"
            @click="vistaActual = 'lista'"
          >
            ☰ Lista
          </button>
          <button
            class="vista-toggle__btn"
            :class="{ 'vista-toggle__btn--active': vistaActual === 'calendario' }"
            @click="vistaActual = 'calendario'"
          >
            📅 Calendario
          </button>
        </div>
      </div>
    </div>

    <!-- ── Vista: Lista ──────────────────────────────────────────────── -->
    <template v-if="vistaActual === 'lista'">
<!-- ── Filtros ─────────────────────────────────────────────────── -->
    <NCard size="small" class="mb-3">
      <div class="flex flex-wrap items-center gap-2">
        <NInput
          v-model:value="filtros.q"
          placeholder="Buscar nombre de jornada…"
          clearable
          style="width:220px"
          @keyup.enter="gridApi.query()"
        />
        <NSelect
          v-model:value="filtros.tipo"
          :options="TIPO_OPTIONS"
          placeholder="Tipo"
          style="width:155px"
        />
        <NSelect
          v-model:value="filtros.estado"
          :options="ESTADO_OPTIONS"
          placeholder="Estado"
          style="width:140px"
        />
        <NInput
          v-model:value="filtros.fechaIni"
          placeholder="Desde (yyyy-MM-dd)"
          clearable
          style="width:170px"
          maxlength="10"
        />
        <NInput
          v-model:value="filtros.fechaFin"
          placeholder="Hasta (yyyy-MM-dd)"
          clearable
          style="width:170px"
          maxlength="10"
        />
        <NButton type="primary" @click="gridApi.query()">Filtrar</NButton>
        <NButton @click="limpiarFiltros">Limpiar</NButton>
        <div class="ml-auto">
          <NButton
            type="primary"
            :style="{ background: '#0f766e', borderColor: '#0f766e' }"
            @click="formJornadaApi.setData(null).open()"
          >
            + Nuevo Turno
          </NButton>
        </div>
      </div>
    </NCard>

    <!-- ── Grid ────────────────────────────────────────────────────── -->
    <Grid table-title="Jornadas" />
</template><!-- /v-if lista -->

    <!-- ── Vista: Calendario ─────────────────────────────────────────── -->
    <NCard v-else size="small" class="mb-3">
      <CalendarioMensual @open-detalle="abrirDrawer" />
    </NCard>

    <!-- ════════════════════════════════════════════════════════════════
         DRAWER — DETALLE JORNADA
         ════════════════════════════════════════════════════════════════ -->
    <NDrawer v-model:show="drawerVisible" :width="660" placement="right">
      <NDrawerContent :native-scrollbar="false" closable>
<!-- Header del drawer -->
        <template #header>
          <div v-if="jornadaSeleccionada" class="flex items-start gap-3">
            <div
              class="flex-shrink-0 w-10 h-10 rounded-xl flex items-center justify-center text-white text-lg font-bold"
              :style="{ background: TIPO_META[jornadaSeleccionada.tipoJornada]?.color ?? '#334155' }"
            >
              {{ jornadaSeleccionada.tipoJornada === 'GUARDIA' ? '🔒'
               : jornadaSeleccionada.tipoJornada === 'EMERGENCIA' ? '🚨'
               : jornadaSeleccionada.tipoJornada === 'LLAMADA' ? '📞' : '📋' }}
            </div>
            <div>
              <div class="font-bold text-base leading-tight">
                {{ etiquetaJornada(jornadaSeleccionada) }}
              </div>
              <div class="flex gap-2 mt-1 flex-wrap">
                <NTag
                  :type="ESTADO_JORNADA_META[jornadaSeleccionada.estado]?.type ?? 'default'"
                  size="small" round
                >
                  {{ ESTADO_JORNADA_META[jornadaSeleccionada.estado]?.label }}
                </NTag>
                <NTag v-if="jornadaSeleccionada.estaEnCurso" type="success" size="small" round>
                  ● En curso
                </NTag>
                <NTag type="default" size="small" round>
                  {{ TIPO_META[jornadaSeleccionada.tipoJornada]?.label }}
                </NTag>
              </div>
            </div>
          </div>
        </template>

        <!-- Cuerpo del drawer -->
        <NSpin :show="loadingDrawer">
<!-- Info compacta -->
          <NGrid v-if="jornadaSeleccionada" :cols="3" :x-gap="10" class="mb-4">
            <NGridItem>
              <div class="info-card">
                <div class="info-card__label">Período</div>
                <div class="info-card__value">{{ jornadaSeleccionada.fechaInicio }}</div>
                <div class="info-card__sub">→ {{ jornadaSeleccionada.fechaFin }}</div>
              </div>
            </NGridItem>
            <NGridItem>
              <div class="info-card">
                <div class="info-card__label">Horario</div>
                <div class="info-card__value">
                  {{ fmtHora(jornadaSeleccionada.horaInicio) }} – {{ fmtHora(jornadaSeleccionada.horaFin) }}
                </div>
                <div class="info-card__sub">{{ jornadaSeleccionada.duracionHoras }}h duración</div>
              </div>
            </NGridItem>
            <NGridItem>
              <div class="info-card">
                <div class="info-card__label">Personal</div>
                <div class="info-card__value">
                  <span style="color:#e63946">{{ jornadaSeleccionada.totalTitulares }}T</span>
                  <span style="color: hsl(var(--muted-foreground)); margin: 0 4px">·</span>
                  <span style="color:#1d4ed8">{{ jornadaSeleccionada.totalInternos }}I</span>
                </div>
                <div class="info-card__sub">asignados</div>
              </div>
            </NGridItem>
          </NGrid>

          <!-- Descripción -->
          <div
            v-if="jornadaSeleccionada?.descripcion"
            class="mb-4 px-3 py-2 rounded-lg text-sm"
            style="background: hsl(var(--muted)); border-left: 3px solid hsl(var(--border)); color: hsl(var(--muted-foreground))"
          >
            {{ jornadaSeleccionada.descripcion }}
          </div>

          <!-- Acciones rápidas de jornada -->
          <div v-if="jornadaSeleccionada?.estado === 'ACTIVA'" class="flex gap-2 mb-4 flex-wrap">
            <NButton size="small" ghost @click="formJornadaApi.setData(jornadaSeleccionada).open()">
              ✏ Editar
            </NButton>
            <NButton size="small" type="warning" ghost @click="confirmarCerrar(jornadaSeleccionada!)">
              🔒 Cerrar Turno
            </NButton>
            <NButton size="small" type="error" ghost @click="confirmarCancelar(jornadaSeleccionada!)">
              ✕ Cancelar Turno
            </NButton>
          </div>

          <NDivider style="margin: 8px 0 4px" />

          <!-- ── Tabs ─────────────────────────────────────────────── -->
          <NTabs v-model:value="tabDrawer" type="segment" animated @update:value="onTabChange">
<!-- TAB TITULARES -->
            <NTabPane name="titulares" :tab="`👨‍⚕️ Titulares (${titularesJornada.length})`">
              <div class="flex items-center justify-between mb-3">
                <span class="text-sm font-semibold" style="color: hsl(var(--muted-foreground))">
                  Personal titular asignado
                </span>
                <NSpace>
                  <NButton
                    v-if="jornadaSeleccionada?.estado === 'ACTIVA'"
                    size="small"
                    type="primary"
                    :style="{ background: '#e63946', borderColor: '#e63946' }"
                    @click="abrirAsignarTitular"
                  >
                    + Asignar titular
                  </NButton>
                  <NButton
                    v-if="titularesJornada.length > 0"
                    size="small"
                    :style="{ background: '#7c3aed', borderColor: '#7c3aed', color: 'white' }"
                    @click="rolTurnoVisible = true"
                  >
                    🗓 ROL TURNO
                  </NButton>
                </NSpace>
              </div>
              <NEmpty v-if="titularesJornada.length === 0" description="Sin titulares asignados" size="small" class="py-6" />
              <NDataTable
                v-else
                :columns="columnasTitulares"
                :data="titularesJornada"
                size="small"
                :bordered="false"
                :striped="true"
              />
              <div v-if="titularesJornada.length > 0" class="mt-2 text-xs" style="color: hsl(var(--muted-foreground))">
                Tip: usa el botón <strong>📋 Act.</strong> en cada fila para asignar actividades directamente.
              </div>
            </NTabPane>

            <!-- TAB INTERNOS -->
            <NTabPane name="internos" :tab="`🎓 Internos (${internosJornada.length})`">
              <div class="flex items-center justify-between mb-3">
                <span class="text-sm font-semibold" style="color: hsl(var(--muted-foreground))">
                  Internos asignados
                </span>
                <NSpace>
                  <NButton
                    v-if="jornadaSeleccionada?.estado === 'ACTIVA'"
                    size="small"
                    type="primary"
                    :style="{ background: '#1d4ed8', borderColor: '#1d4ed8' }"
                    @click="abrirAsignarInterno"
                  >
                    + Asignar interno
                  </NButton>
                  <NButton
                    v-if="internosJornada.length > 0"
                    size="small"
                    :style="{ background: '#7c3aed', borderColor: '#7c3aed', color: 'white' }"
                    @click="horarioInternoVisible = true"
                  >
                    🗓 HORARIO
                  </NButton>
                </NSpace>
              </div>
              <NEmpty v-if="internosJornada.length === 0" description="Sin internos asignados" size="small" class="py-6" />
              <NDataTable
                v-else
                :columns="columnasInternos"
                :data="internosJornada"
                size="small"
                :bordered="false"
                :striped="true"
              />
              <div v-if="internosJornada.length > 0" class="mt-2 text-xs" style="color: hsl(var(--muted-foreground))">
                Tip: usa el botón <strong>📋 Act.</strong> en cada fila para asignar actividades directamente.
              </div>
            </NTabPane>

            <!-- TAB ACTIVIDADES -->
            <NTabPane name="actividades" :tab="`📋 Actividades (${actividadesJornada.length})`">
<!-- ── Quick-assign: asignar en 2 clicks ───────────── -->
              <div
                v-if="jornadaSeleccionada?.estado === 'ACTIVA'"
                class="mb-3 p-3 rounded-xl"
                style="background: hsl(var(--muted)); border: 1px solid hsl(var(--border))"
              >
                <div class="text-xs font-semibold mb-2" style="color: hsl(var(--muted-foreground))">
                  ⚡ Asignar actividad rápida
                </div>
                <div class="flex gap-2 items-center flex-wrap">
                  <NSelect
                    v-model:value="quickAssign.personaKey"
                    :options="personaOpts"
                    placeholder="Para quién…"
                    size="small"
                    clearable
                    style="flex: 1; min-width: 160px"
                  />
                  <NSelect
                    v-model:value="quickAssign.idActividad"
                    :options="catalogoOpts"
                    :loading="catalogoLoading"
                    placeholder="Actividad del catálogo…"
                    size="small"
                    filterable
                    clearable
                    style="flex: 1.8; min-width: 180px"
                  />
                  <NButton
                    type="primary"
                    size="small"
                    :loading="quickAssign.loading"
                    :disabled="!quickAssign.personaKey || !quickAssign.idActividad"
                    :style="{ background: '#0f766e', borderColor: '#0f766e' }"
                    @click="asignarActividadQuick"
                  >
                    + Asignar
                  </NButton>
                </div>

                <!-- Toggle nueva actividad -->
                <div class="flex items-center gap-2 mt-2">
                  <button
                    type="button"
                    class="text-xs font-medium transition-colors"
                    :style="{ color: mostrarFormNueva ? '#64748b' : '#0f766e', background: 'none', border: 'none', cursor: 'pointer', padding: 0 }"
                    @click="toggleFormNueva"
                  >
                    {{ mostrarFormNueva ? '✕ Cancelar nueva actividad' : '+ Crear nueva actividad' }}
                  </button>
                  <span v-if="!mostrarFormNueva" style="font-size:.71rem; color:#94a3b8">(si no existe en el catálogo)</span>
                </div>

                <!-- Mini-form nueva actividad -->
                <div
                  v-if="mostrarFormNueva"
                  class="mt-3 p-3 rounded-lg space-y-2"
                  style="border: 1px solid #0f766e33"
                >
                  <div style="font-size:.75rem; font-weight:600; color:#0f766e">
                    Nueva actividad — se guardará en el catálogo y quedará seleccionada
                  </div>
                  <NInput
                    v-model:value="formNuevaAct.nombre"
                    placeholder="Nombre de la actividad *"
                    size="small"
                    maxlength="150"
                    clearable
                  />
                  <NSelect
                    v-model:value="formNuevaAct.tipoActividad"
                    :options="TIPO_ACT_OPTIONS"
                    size="small"
                    style="width:100%"
                  />
                  <NInput
                    v-model:value="formNuevaAct.descripcion"
                    type="textarea"
                    :autosize="{ minRows: 1, maxRows: 2 }"
                    placeholder="Descripción breve (opcional)"
                    size="small"
                    maxlength="500"
                  />
                  <div class="flex justify-end">
                    <NButton
                      type="primary"
                      size="small"
                      :loading="loadingNuevaAct"
                      :disabled="!formNuevaAct.nombre.trim()"
                      :style="{ background: '#0f766e', borderColor: '#0f766e' }"
                      @click="crearNuevaActividad"
                    >
                      Crear y seleccionar
                    </NButton>
                  </div>
                </div>

                <div
                  v-if="personaOpts.length === 0"
                  class="text-xs mt-1"
                  style="color:#f97316"
                >
                  ⚠ Asigna titulares o internos primero para poder agregar actividades.
                </div>
              </div>

              <NEmpty
                v-if="actividadesJornada.length === 0"
                description="Sin actividades registradas"
                size="small"
                class="py-4"
              />
              <NDataTable
                v-else
                :columns="columnasActividades"
                :data="actividadesJornada"
                size="small"
                :bordered="false"
                :striped="true"
              />
            </NTabPane>

            <!-- TAB BITÁCORA -->
            <NTabPane name="bitacora" :tab="`📒 Bitácora (${bitacoraJornada.length})`">
              <NSpin :show="bitacoraLoading">
                <NEmpty v-if="bitacoraJornada.length === 0" description="Sin entradas de bitácora" size="small" class="py-4" />
                <div v-else class="space-y-3 py-2">
                  <div
                    v-for="entrada in bitacoraJornada"
                    :key="entrada.idBitacora"
                    class="rounded-lg p-3"
                    style="background: hsl(var(--muted)); border: 1px solid hsl(var(--border))"
                  >
                    <div class="flex items-center justify-between mb-1">
                      <span class="text-sm font-semibold" style="color: hsl(var(--foreground))">
                        {{ entrada.nombrePersonal ?? '—' }}
                      </span>
                      <span class="text-xs" style="color: hsl(var(--muted-foreground))">
                        {{ entrada.fechaHoraEvento?.replace('T', ' ').slice(0, 16) }}
                      </span>
                    </div>
                    <div class="text-xs leading-relaxed" style="color: hsl(var(--muted-foreground))">
                      {{ entrada.descripcion }}
                    </div>
                    <div v-if="entrada.evidenciaDownloadUrl" class="mt-2">
                      <NButton
                        size="tiny"
                        tag="a"
                        :href="entrada.evidenciaDownloadUrl"
                        target="_blank"
                        rel="noopener"
                        tertiary
                        type="primary"
                      >
                        📎 Ver evidencia
                      </NButton>
                    </div>
                  </div>
                </div>
              </NSpin>
            </NTabPane>
</NTabs>
        </NSpin>
</NDrawerContent>

      <!-- ── Modales de asignación: dentro del drawer (z-index > drawer) ── -->

      <!-- Drawer: ROL TURNO (titulares) -->
      <FormRolTurno
        v-model:visible="rolTurnoVisible"
        :titulares="titularesJornada"
        :id-jornada="jornadaSeleccionada?.idJornada ?? null"
        :fecha-inicio="jornadaSeleccionada?.fechaInicio ?? null"
        :fecha-fin="jornadaSeleccionada?.fechaFin ?? null"
        :hora-inicio="jornadaSeleccionada?.horaInicio ?? null"
        :hora-fin="jornadaSeleccionada?.horaFin ?? null"
        @saved="() => {}"
      />

      <!-- Drawer: HORARIO INTERNOS -->
      <FormHorarioInterno
        v-model:visible="horarioInternoVisible"
        :internos="internosJornada"
        :id-jornada="jornadaSeleccionada?.idJornada ?? null"
        :dias-default="jornadaSeleccionada?.diasDefault ?? null"
        :hora-inicio="jornadaSeleccionada?.horaInicio ?? null"
        :hora-fin="jornadaSeleccionada?.horaFin ?? null"
        @saved="() => {}"
      />

      <!-- Modal: Asignar Titulares -->
      <NModal
        v-model:show="asignarTitularVisible"
        preset="card"
        title="👨‍⚕️ Asignar Titulares a la Jornada"
        :style="{ width: '680px' }"
        :z-index="3000"
        :mask-closable="false"
        :bordered="false"
      >
        <FormAsignarTitular
          v-if="asignarTitularVisible"
          :id-jornada="jornadaSeleccionada?.idJornada ?? null"
          :hora-inicio-jornada="jornadaSeleccionada?.horaInicio ?? null"
          :hora-fin-jornada="jornadaSeleccionada?.horaFin ?? null"
          :modalidad="jornadaSeleccionada?.modalidad ?? null"
          :hora-inicio-tarde-jornada="jornadaSeleccionada?.horaInicioTarde ?? null"
          :hora-fin-tarde-jornada="jornadaSeleccionada?.horaFinTarde ?? null"
          @success="onAsignarTitularSuccess"
          @cancel="asignarTitularVisible = false"
        />
      </NModal>

      <!-- Modal: Editar horario de un titular ya asignado -->
      <NModal
        v-model:show="editarHorario.visible"
        preset="card"
        title="🕐 Horario del titular"
        :style="{ width: '460px' }"
        :z-index="3000"
        :bordered="false"
      >
        <div v-if="editarHorario.row" class="space-y-3">
          <div class="text-sm font-semibold" style="color: hsl(var(--foreground))">
            {{ editarHorario.row.nombreCompleto }}
          </div>

          <!-- Franja mañana (o única si la jornada es continua) -->
          <div class="space-y-1">
            <NCheckbox
              v-if="editarHorario.row.jornadaPartida"
              v-model:checked="editarHorario.haceManana"
            >
              <span class="text-xs font-semibold">☀ Cubre la mañana</span>
            </NCheckbox>
            <div
              v-if="!editarHorario.row.jornadaPartida || editarHorario.haceManana"
              class="flex gap-2"
            >
              <NTimePicker
                v-model:formatted-value="editarHorario.inicio"
                format="HH:mm:ss" value-format="HH:mm:ss"
                :minute-step="15" :second-step="0"
                size="small" style="flex: 1" placeholder="Entrada"
              />
              <NTimePicker
                v-model:formatted-value="editarHorario.fin"
                format="HH:mm:ss" value-format="HH:mm:ss"
                :minute-step="15" :second-step="0"
                size="small" style="flex: 1" placeholder="Salida"
              />
            </div>
          </div>

          <!-- Franja tarde: solo en jornada partida -->
          <div v-if="editarHorario.row.jornadaPartida" class="space-y-1">
            <NCheckbox v-model:checked="editarHorario.haceTarde">
              <span class="text-xs font-semibold">🌆 Cubre la tarde</span>
            </NCheckbox>
            <div v-if="editarHorario.haceTarde" class="flex gap-2">
              <NTimePicker
                v-model:formatted-value="editarHorario.inicioTarde"
                format="HH:mm:ss" value-format="HH:mm:ss"
                :minute-step="15" :second-step="0"
                size="small" style="flex: 1" placeholder="Entrada"
              />
              <NTimePicker
                v-model:formatted-value="editarHorario.finTarde"
                format="HH:mm:ss" value-format="HH:mm:ss"
                :minute-step="15" :second-step="0"
                size="small" style="flex: 1" placeholder="Salida"
              />
            </div>
          </div>

          <div v-if="horarioSinFranja" class="text-xs" style="color: #e03131">
            ⚠ Debe cubrir al menos una franja, si no nunca estará en turno.
          </div>
          <div v-else class="text-xs" style="color: hsl(var(--muted-foreground))">
            El GPS lo vigilará solo dentro de las franjas marcadas.
          </div>
        </div>

        <template #footer>
          <NSpace justify="end">
            <NButton @click="editarHorario.visible = false">Cancelar</NButton>
            <NButton
              type="primary"
              :loading="editarHorario.guardando"
              :disabled="horarioSinFranja"
              @click="guardarHorario"
            >
              Guardar
            </NButton>
          </NSpace>
        </template>
      </NModal>

      <!-- Modal: Asignar Internos -->
      <NModal
        v-model:show="asignarInternoVisible"
        preset="card"
        title="🎓 Asignar Internos a la Jornada"
        :style="{ width: '680px' }"
        :z-index="3000"
        :mask-closable="false"
        :bordered="false"
      >
        <FormAsignarInterno
          v-if="asignarInternoVisible"
          :id-jornada="jornadaSeleccionada?.idJornada ?? null"
          :hora-inicio-jornada="jornadaSeleccionada?.horaInicio ?? null"
          :hora-fin-jornada="jornadaSeleccionada?.horaFin ?? null"
          @success="onAsignarInternoSuccess"
          @cancel="asignarInternoVisible = false"
        />
      </NModal>

      <!-- Modal: Cambiar Estado de Asignación -->
      <NModal
        v-model:show="estadoModal.visible"
        preset="card"
        title="↻ Cambiar Estado"
        :style="{ width: '400px' }"
        :z-index="3000"
        :mask-closable="!estadoModal.loading"
        :bordered="false"
      >
        <div class="space-y-4">
          <NSelect
            v-model:value="estadoModal.nuevoEstado"
            :options="estadoModal.tipo === 'actividad' ? ESTADO_ACTIVIDAD_OPTIONS : ESTADO_ASIG_OPTIONS"
            placeholder="Seleccionar nuevo estado"
          />
          <NInput
            v-model:value="estadoModal.observaciones"
            type="textarea"
            placeholder="Observación (opcional)"
            :autosize="{ minRows: 2, maxRows: 3 }"
          />
          <div class="flex justify-end gap-2">
            <NButton :disabled="estadoModal.loading" @click="estadoModal.visible = false">
              Cancelar
            </NButton>
            <NButton
              type="primary"
              :loading="estadoModal.loading"
              :disabled="!estadoModal.nuevoEstado"
              @click="confirmarCambioEstado"
            >
              Confirmar
            </NButton>
          </div>
        </div>
      </NModal>
</NDrawer>
</Page>
</template>

<style scoped>
/* ── Stat badges ─────────────────────────────────────────────────────────── */
.stat-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border-radius: 12px;
  border: 1px solid hsl(var(--border));
  background: hsl(var(--card));
}
.stat-badge__icon  { font-size: 1.2rem; }
.stat-badge__label { font-size: 0.74rem; font-weight: 500; color: hsl(var(--muted-foreground)); }
.stat-badge__value { font-size: 1.25rem; font-weight: 700; line-height: 1; color: hsl(var(--foreground)); }
.stat-badge__value--green  { color: #22c55e; }
.stat-badge__value--orange { color: #f97316; }

/* ── Info cards del drawer ───────────────────────────────────────────────── */
.info-card {
  border-radius: 12px;
  padding: 10px;
  text-align: center;
  background: hsl(var(--card));
  border: 1px solid hsl(var(--border));
}
.info-card__label { font-size: 0.72rem; color: hsl(var(--muted-foreground)); margin-bottom: 2px; }
.info-card__value { font-size: 0.88rem; font-weight: 600; color: hsl(var(--foreground)); }
.info-card__sub   { font-size: 0.72rem; color: hsl(var(--muted-foreground)); margin-top: 1px; }

/* ── Toggle Lista / Calendario ───────────────────────────────────────────── */
.vista-toggle {
  display: flex;
  border: 1px solid hsl(var(--border));
  border-radius: 8px;
  overflow: hidden;
  background: hsl(var(--card));
}
.vista-toggle__btn {
  padding: 5px 14px;
  font-size: 0.75rem;
  font-weight: 600;
  background: transparent;
  border: none;
  cursor: pointer;
  color: hsl(var(--muted-foreground));
  transition: background 0.14s, color 0.14s;
  white-space: nowrap;
}
.vista-toggle__btn:hover {
  background: hsl(var(--accent));
  color: hsl(var(--foreground));
}
.vista-toggle__btn--active {
  background: #0f766e;
  color: #fff;
}
</style>
