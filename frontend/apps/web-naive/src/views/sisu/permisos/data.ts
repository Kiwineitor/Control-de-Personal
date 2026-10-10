import { h } from 'vue';
import { NButton, NDropdown, NTag, NText } from 'naive-ui';
import type { SolicitudPermisoResponse, DelegacionResponse } from '#/api/sisu/permisos';

// ── Metadatos de estados ────────────────────────────────────────────────
export const ESTADO_SOL_META: Record<string, { label: string; type: any }> = {
  PENDIENTE:       { label: 'Pendiente',       type: 'warning' },
  APROBADA:        { label: 'Aprobada',         type: 'success' },
  RECHAZADA:       { label: 'Rechazada',        type: 'error'   },
  CANCELADA:       { label: 'Cancelada',        type: 'default' },
  EN_OBSERVACION:  { label: 'En Observación',   type: 'info'    },
};

export const ESTADO_DEL_META: Record<string, { label: string; type: any }> = {
  PENDIENTE_APROBACION: { label: 'Pend. Aprobación', type: 'warning' },
  APROBADA:             { label: 'Aprobada',          type: 'info'    },
  EN_CURSO:             { label: 'En Curso',           type: 'success' },
  COMPLETADA:           { label: 'Completada',         type: 'default' },
  RECHAZADA:            { label: 'Rechazada',          type: 'error'   },
  CANCELADA:            { label: 'Cancelada',          type: 'default' },
};

export const TIPO_PERMISO_ICON: Record<string, string> = {
  AUSENCIA_TOTAL:         '🚫',
  PERMISO_MEDICO:         '🏥',
  TRAMITE_ADMINISTRATIVO: '📋',
  OTRO:                   '📝',
};

// ── Opciones para NSelect ───────────────────────────────────────────────
export const tipoPermisoOptions = [
  { label: '🚫 Ausencia Total',           value: 'AUSENCIA_TOTAL'         },
  { label: '🏥 Permiso Médico',           value: 'PERMISO_MEDICO'         },
  { label: '📋 Trámite Administrativo',   value: 'TRAMITE_ADMINISTRATIVO' },
  { label: '📝 Otro',                     value: 'OTRO'                   },
];

export const estadoSolicitudOptions = [
  { label: 'Pendiente',       value: 'PENDIENTE'      },
  { label: 'Aprobada',        value: 'APROBADA'       },
  { label: 'Rechazada',       value: 'RECHAZADA'      },
  { label: 'Cancelada',       value: 'CANCELADA'      },
  { label: 'En Observación',  value: 'EN_OBSERVACION' },
];

export const estadoDelegacionOptions = [
  { label: 'Pendiente Aprobación', value: 'PENDIENTE_APROBACION' },
  { label: 'Aprobada',             value: 'APROBADA'             },
  { label: 'En Curso',             value: 'EN_CURSO'             },
  { label: 'Completada',           value: 'COMPLETADA'           },
  { label: 'Rechazada',            value: 'RECHAZADA'            },
  { label: 'Cancelada',            value: 'CANCELADA'            },
];

// ── Tipos de acciones ───────────────────────────────────────────────────
export interface ActionParamsSol {
  code: 'aprobar' | 'observar' | 'rechazar' | 'cancelar' | 'justificante' | 'detalle';
  row:  SolicitudPermisoResponse;
}
export interface ActionParamsDel {
  code: 'aprobar' | 'rechazar' | 'iniciar' | 'completar' | 'cancelar';
  row:  DelegacionResponse;
}

// ── Mapa compacto para el badge de tipo permiso ─────────────────────────
const TIPO_BADGE: Record<string, { label: string; type: 'success' | 'warning' | 'error' | 'info' | 'default' }> = {
  PERMISO_MEDICO:         { label: '🏥 Médico',       type: 'success' },
  TRAMITE_ADMINISTRATIVO: { label: '📋 Trámite',      type: 'info'    },
  AUSENCIA_TOTAL:         { label: '🚫 Ausencia',     type: 'error'   },
  OTRO:                   { label: '📝 Otro',          type: 'default' },
};

// ── Columnas Solicitudes ────────────────────────────────────────────────
export function useSolicitudesColumns(
  onAction: (p: ActionParamsSol) => void,
): any[] {
  return [
    { type: 'seq', width: 50, align: 'center' },
    {
      field: 'apellidoTitular', title: 'Solicitante', minWidth: 185,
      slots: {
        default: ({ row }: { row: SolicitudPermisoResponse }) => {
          const badge = TIPO_BADGE[row.tipoPermiso];
          return [
            h('div', { style: { display: 'flex', flexDirection: 'column', gap: '3px' } }, [
              h('span', { style: { fontWeight: 500, fontSize: '0.9em' } },
                `${row.apellidoTitular ?? ''}, ${row.nombreTitular ?? ''}`),
              h(NTag, {
                type: badge?.type ?? 'default',
                size: 'tiny',
                bordered: false,
                style: { alignSelf: 'flex-start' },
              }, () => badge?.label ?? row.tipoPermisoLabel ?? row.tipoPermiso),
            ]),
          ];
        },
      },
    },
    {
      field: 'motivoSolicitud', title: 'Motivo', minWidth: 160,
      slots: {
        default: ({ row }: { row: SolicitudPermisoResponse }) => {
          const texto = row.motivoSolicitud ?? '—';
          const truncado = texto.length > 55 ? texto.slice(0, 55) + '…' : texto;
          return [
            h('span', {
              title: texto,
              style: { fontSize: '0.85em', cursor: 'default' },
            }, truncado),
          ];
        },
      },
    },
    { field: 'nombreJornada', title: 'Jornada', minWidth: 115 },
    {
      field: 'fechaHoraSolicitud', title: 'Registrado', width: 140, align: 'center',
      slots: {
        default: ({ row }: { row: SolicitudPermisoResponse }) => {
          if (!row.fechaHoraSolicitud) return [h(NText, { depth: 3 }, () => '—')];
          const dt  = new Date(row.fechaHoraSolicitud);
          const dia = dt.toLocaleDateString('es-BO', { day: '2-digit', month: '2-digit', year: '2-digit' });
          const hora = dt.toLocaleTimeString('es-BO', { hour: '2-digit', minute: '2-digit' });
          return [
            h('div', { style: { lineHeight: 1.35 } }, [
              h('div', { style: { fontSize: '0.82em', fontWeight: 500 } }, dia),
              h('div', { style: { fontSize: '0.78em', opacity: 0.65 } }, hora),
            ]),
          ];
        },
      },
    },
    {
      field: 'estadoSolicitud', title: 'Estado', width: 120, align: 'center',
      slots: {
        default: ({ row }: { row: SolicitudPermisoResponse }) => {
          const m = ESTADO_SOL_META[row.estadoSolicitud];
          return [h(NTag, { type: m?.type ?? 'default', size: 'small', round: true },
            () => m?.label ?? row.estadoSolicitud)];
        },
      },
    },
    {
      field: 'estadoDelegacion', title: 'Delegación', width: 140, align: 'center',
      slots: {
        default: ({ row }: { row: SolicitudPermisoResponse }) => {
          if (!row.estadoDelegacion) {
            return [h(NText, { depth: 3, style: { fontSize: '0.8em' } }, () => 'Sin delegación')];
          }
          const m = ESTADO_DEL_META[row.estadoDelegacion];
          return [h(NTag, { type: m?.type ?? 'default', size: 'small' }, () => m?.label ?? row.estadoDelegacion)];
        },
      },
    },
    {
      title: 'Acciones', width: 115, fixed: 'right', align: 'center',
      slots: {
        default: ({ row }: { row: SolicitudPermisoResponse }) => {
          const estado = row.estadoSolicitud;
          const baseOpts = [
            { label: '🔍 Ver detalle', key: 'detalle' },
          ];

          if (estado === 'PENDIENTE' || estado === 'EN_OBSERVACION') {
            const opciones = [
              { label: '✓ Aprobar',       key: 'aprobar',      props: { style: { color: '#18a058' } } },
              { label: '👁 Observar',     key: 'observar',     props: { style: { color: '#f0a020' } } },
              { label: '❌ Rechazar',     key: 'rechazar',     props: { style: { color: '#d03050' } } },
              { type: 'divider', key: 'd1' },
              { label: '📎 Justificante', key: 'justificante' },
              ...baseOpts,
            ];
            return [
              h(NDropdown, {
                trigger: 'click', options: opciones,
                onSelect: (key: string) => onAction({ code: key as ActionParamsSol['code'], row }),
              }, { default: () => h(NButton, { size: 'small', secondary: true }, { default: () => 'Acciones ▾' }) }),
            ];
          }

          if (estado === 'APROBADA') {
            const opciones = [
              { label: '📎 Justificante', key: 'justificante' },
              ...baseOpts,
            ];
            return [
              h(NDropdown, {
                trigger: 'click', options: opciones,
                onSelect: (key: string) => onAction({ code: key as ActionParamsSol['code'], row }),
              }, { default: () => h(NButton, { size: 'small', secondary: true }, { default: () => 'Acciones ▾' }) }),
            ];
          }

          // RECHAZADA / CANCELADA — solo "Ver detalle"
          return [
            h(NButton, {
              size: 'small', secondary: true,
              onClick: () => onAction({ code: 'detalle', row }),
            }, () => '🔍 Ver'),
          ];
        },
      },
    },
  ];
}

// ── Columnas Delegaciones ───────────────────────────────────────────────
export function useDelegacionesColumns(
  onAction: (p: ActionParamsDel) => void,
): any[] {
  return [
    { type: 'seq', width: 50, align: 'center' },
    {
      title: 'Titular', minWidth: 150,
      slots: {
        default: ({ row }: { row: DelegacionResponse }) => [
          h('div', {}, [
            h('div', { style: { fontWeight: 600, fontSize: '0.9em' } }, row.nombreTitular ?? '—'),
            h('div', { style: { fontSize: '0.78em', color: '#94a3b8' } }, row.nombreJornada ?? ''),
          ]),
        ],
      },
    },
    { field: 'nombreInterno', title: 'Cubre', minWidth: 145 },
    {
      field: 'lugarNombre', title: 'Lugar', width: 105,
      slots: {
        default: ({ row }: { row: DelegacionResponse }) => [
          row.lugarNombre
            ? h(NTag, { size: 'small', type: 'info' }, () => row.lugarNombre!)
            : h(NText, { depth: 3 }, () => '—'),
        ],
      },
    },
    {
      title: 'Plan.', width: 120, align: 'center',
      slots: {
        default: ({ row }: { row: DelegacionResponse }) => {
          const t = row.horaInicioPlanificada
            ? `${row.horaInicioPlanificada}${row.horaFinPlanificada ? '–' + row.horaFinPlanificada : ''}`
            : '—';
          return [h(NText, { style: { fontSize: '0.85em' } }, () => t)];
        },
      },
    },
    {
      title: 'Real', width: 120, align: 'center',
      slots: {
        default: ({ row }: { row: DelegacionResponse }) => {
          const t = row.horaInicioReal
            ? `${row.horaInicioReal}${row.horaFinReal ? '–' + row.horaFinReal : ''}`
            : '—';
          return [h(NText, { depth: row.horaInicioReal ? 1 : 3, style: { fontSize: '0.85em' } }, () => t)];
        },
      },
    },
    {
      field: 'estado', title: 'Estado', width: 155, align: 'center',
      slots: {
        default: ({ row }: { row: DelegacionResponse }) => {
          const m = ESTADO_DEL_META[row.estado];
          return [
            h('div', {}, [
              h(NTag, { type: m?.type ?? 'default', size: 'small', round: true },
                () => m?.label ?? row.estado),
              row.idSolicitudPermiso
                ? h('div', { style: { fontSize: '0.72em', color: '#94a3b8', marginTop: '2px' } },
                    `Sol. #${row.idSolicitudPermiso}`)
                : null,
            ]),
          ];
        },
      },
    },
    {
      title: 'Acciones', width: 115, fixed: 'right', align: 'center',
      slots: {
        default: ({ row }: { row: DelegacionResponse }) => {
          if (row.estado === 'PENDIENTE_APROBACION') {
            const opts = [
              { label: '✓ Aprobar',  key: 'aprobar',  props: { style: { color: '#18a058' } } },
              { label: '✗ Rechazar', key: 'rechazar', props: { style: { color: '#d03050' } } },
            ];
            return [
              h(NDropdown, {
                trigger: 'click', options: opts,
                onSelect: (key: string) =>
                  onAction({ code: key as ActionParamsDel['code'], row }),
              }, {
                default: () => h(NButton, { size: 'small', secondary: true }, { default: () => 'Acciones ▾' }),
              }),
            ];
          }

          if (row.estado === 'APROBADA') {
            const opts = [
              { label: '▶ Iniciar',  key: 'iniciar',  props: { style: { color: '#2080f0' } } },
              { label: '✕ Cancelar', key: 'cancelar', props: { style: { color: '#d03050' } } },
            ];
            return [
              h(NDropdown, {
                trigger: 'click', options: opts,
                onSelect: (key: string) =>
                  onAction({ code: key as ActionParamsDel['code'], row }),
              }, {
                default: () => h(NButton, { size: 'small', secondary: true }, { default: () => 'Acciones ▾' }),
              }),
            ];
          }

          if (row.estado === 'EN_CURSO') {
            const opts = [
              { label: '✓ Completar', key: 'completar', props: { style: { color: '#18a058' } } },
              { label: '✕ Cancelar',  key: 'cancelar',  props: { style: { color: '#d03050' } } },
            ];
            return [
              h(NDropdown, {
                trigger: 'click', options: opts,
                onSelect: (key: string) =>
                  onAction({ code: key as ActionParamsDel['code'], row }),
              }, {
                default: () => h(NButton, { size: 'small', secondary: true }, { default: () => 'Acciones ▾' }),
              }),
            ];
          }

          return [h(NText, { depth: 3, style: { fontSize: '0.8em' } },
            () => row.aprobadoPor ?? '—')];
        },
      },
    },
  ];
}
