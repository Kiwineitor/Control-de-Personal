// apps/web-naive/src/views/sisu/actividades/data.ts
import { h } from 'vue';
import type { Ref } from 'vue';
import { NButton, NSpace, NTag } from 'naive-ui';
import type { ActividadDto, TipoActividad, EstadoCatalogo, EstadoActividad } from '#/api/sisu/actividades';

// ── Metadatos de dominio ──────────────────────────────────────────────────

export const TIPO_ACTIVIDAD_META: Record<TipoActividad, {
  label: string; color: string; bg: string; darkColor: string; darkBg: string;
}> = {
  CLINICA:        { label: 'Clínica',        color: '#dc2626', bg: '#fee2e2', darkColor: '#fca5a5', darkBg: 'rgba(220,38,38,.18)'  },
  ADMINISTRATIVA: { label: 'Administrativa', color: '#1d4ed8', bg: '#dbeafe', darkColor: '#93c5fd', darkBg: 'rgba(29,78,216,.18)'   },
  ACADEMICA:      { label: 'Académica',      color: '#7c3aed', bg: '#ede9fe', darkColor: '#c4b5fd', darkBg: 'rgba(124,58,237,.18)'  },
  SUPERVISION:    { label: 'Supervisión',    color: '#b45309', bg: '#fef3c7', darkColor: '#fcd34d', darkBg: 'rgba(180,83,9,.18)'    },
  EMERGENCIA:     { label: 'Emergencia',     color: '#be123c', bg: '#fecaca', darkColor: '#fca5a5', darkBg: 'rgba(190,18,60,.18)'   },
  OTRA:           { label: 'Otra',           color: '#475569', bg: '#f1f5f9', darkColor: '#94a3b8', darkBg: 'rgba(71,85,105,.18)'   },
};

export const ESTADO_CATALOGO_META: Record<EstadoCatalogo, { label: string; type: any }> = {
  ACTIVO:    { label: 'Activo',    type: 'success' },
  INACTIVO:  { label: 'Inactivo',  type: 'warning' },
  ELIMINADO: { label: 'Eliminado', type: 'error'   },
};

export const ESTADO_ACTIVIDAD_META: Record<EstadoActividad, { label: string; type: any; color: string }> = {
  PENDIENTE:   { label: 'Pendiente',   type: 'default', color: '#94a3b8' },
  EN_PROGRESO: { label: 'En progreso', type: 'info',    color: '#3b82f6' },
  COMPLETADA:  { label: 'Completada',  type: 'success', color: '#22c55e' },
  VALIDADA:    { label: 'Validada',    type: 'success', color: '#16a34a' },
  CANCELADA:   { label: 'Cancelada',   type: 'error',   color: '#ef4444' },
};

// ── Tipos de acción ───────────────────────────────────────────────────────

export type ActividadActionCode = 'edit' | 'desactivar';

export interface ActividadActionParams {
  code: ActividadActionCode;
  row:  ActividadDto;
}

// ── Columnas ─────────────────────────────────────────────────────────────
// Sin importar VxeGridPropTypes directamente — el tipo se infiere por el
// consumer (useVbenVxeGrid), igual que en jornadas/data.ts y personal/data.ts.

export function useActividadColumns(
  onAction: (p: ActividadActionParams) => void,
  isDark?: Ref<boolean>,
) {
  const dark = () => isDark?.value ?? false;

  return [
    { type: 'seq' as const, width: 55, title: '#', align: 'center' as const },

    {
      field: 'nombre',
      title: 'Actividad',
      minWidth: 210,
      slots: {
        default: ({ row }: { row: ActividadDto }) => {
          const tipo = TIPO_ACTIVIDAD_META[row.tipoActividad];
          return [
            h('div', { style: { fontWeight: 600, fontSize: '0.84rem' } }, row.nombre),
            h('div', { style: { marginTop: '3px' } }, [
              h('span', {
                style: {
                  padding: '1px 8px', borderRadius: '10px', fontSize: '0.7rem',
                  fontWeight: 600,
                  background: dark() ? (tipo?.darkBg ?? 'rgba(71,85,105,.18)') : (tipo?.bg ?? '#f1f5f9'),
                  color:      dark() ? (tipo?.darkColor ?? '#94a3b8')           : (tipo?.color ?? '#475569'),
                },
              }, tipo?.label ?? row.tipoActividad),
            ]),
          ];
        },
      },
    },

    {
      field: 'descripcion',
      title: 'Descripción',
      minWidth: 190,
      slots: {
        default: ({ row }: { row: ActividadDto }) => [
          h('span', { style: { fontSize: '0.81rem', color: 'hsl(var(--muted-foreground))' } },
            row.descripcion ?? '—'),
        ],
      },
    },

    {
      field: 'vecesUsada',
      title: 'Usos',
      width: 75,
      align: 'center' as const,
      slots: {
        default: ({ row }: { row: ActividadDto }) => [
          h('span', { style: { fontWeight: 700, color: dark() ? '#93c5fd' : '#1d4ed8', fontSize: '0.9rem' } },
            String(row.vecesUsada)),
        ],
      },
    },

    {
      field: 'estado',
      title: 'Estado',
      width: 110,
      align: 'center' as const, // 👈 Faltaba aquí
      slots: {
        default: ({ row }: { row: ActividadDto }) => {
          const m = ESTADO_CATALOGO_META[row.estado];
          return [h(NTag, { type: m?.type ?? 'default', size: 'small', round: true }, () => m?.label)];
        },
      },
    },

    {
      title: 'Acciones',
      width: 150,
      align: 'center' as const, // 👈 Faltaba aquí
      fixed: 'right' as const,  // 👈 Faltaba aquí
      slots: {
        default: ({ row }: { row: ActividadDto }) => [
          h(NSpace, { size: 4, justify: 'center' }, () => [
            h(NButton, {
              size: 'tiny', ghost: true,
              onClick: () => onAction({ code: 'edit', row }),
            }, () => '✏ Editar'),
            row.activo
              ? h(NButton, {
                  size: 'tiny', type: 'warning', ghost: true,
                  onClick: () => onAction({ code: 'desactivar', row }),
                }, () => 'Desactivar')
              : null,
          ]),
        ],
      },
    },
  ];
}