/**
 * Definición de columnas VxeTable — Módulo Personal
 * Ruta: apps/web-naive/src/views/sisu/personal/data.ts
 */
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { TitularDto, InternoDto } from '#/api/sisu/personal';
import { h } from 'vue';
import { NButton, NSpace, NTag, NTooltip } from 'naive-ui';

// ─────────────────────────────────────────────────────────────────────────
// Acciones
// ─────────────────────────────────────────────────────────────────────────
export type TitularActionCode = 'edit' | 'toggle' | 'sync';
export type InternoActionCode  = 'edit' | 'toggle' | 'qr';

export interface ActionParams<T, C extends string> {
  code: C;
  row: T;
}

// ─────────────────────────────────────────────────────────────────────────
// Helpers de render
// ─────────────────────────────────────────────────────────────────────────

/** Badge de estado activo/inactivo */
function renderEstado(activo: boolean) {
  return h(
    NTag,
    { type: activo ? 'success' : 'error', size: 'small', round: true },
    { default: () => (activo ? 'Activo' : 'Inactivo') },
  );
}

/** Avatar circular con inicial del apellido */
function renderAvatar(apellido: string, color = '#e63946') {
  const inicial = (apellido ?? 'X').charAt(0).toUpperCase();
  return h(
    'span',
    {
      style: {
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        width: '30px',
        height: '30px',
        borderRadius: '50%',
        background: color,
        color: '#fff',
        fontWeight: '700',
        fontSize: '0.78rem',
        flexShrink: '0',
        marginRight: '8px',
      },
    },
    inicial,
  );
}

// ═══════════════════════════════════════════════════════════════════════════
//  COLUMNAS — TITULAR
// ═══════════════════════════════════════════════════════════════════════════

export function useTitularColumns(
  onAction: (params: ActionParams<TitularDto, TitularActionCode>) => void,
): VxeTableGridOptions['columns'] {
  return [
    { type: 'seq', width: 50, title: '#', fixed: 'left' },
    {
      field: 'nombreCompleto',
      title: 'Nombre Completo',
      minWidth: 220,
      fixed: 'left',
      slots: {
        default: ({ row }: { row: TitularDto }) =>
          h('div', { style: 'display:flex;align-items:center' }, [
            renderAvatar(row.apellido, '#e63946'),
            h('div', {}, [
              h('div', { style: 'font-weight:600;font-size:.83rem' }, row.nombreCompleto ?? '—'),
              h('div', { style: 'color:#94a3b8;font-size:.73rem' }, row.email ?? '—'),
            ]),
          ]),
      },
    },
    { field: 'ci', title: 'CI', width: 110 },
    {
      field: 'cargo',
      title: 'Cargo',
      minWidth: 160,
      slots: {
        default: ({ row }: { row: TitularDto }) =>
          row.cargo
            ? h(
                'span',
                {
                  style:
                    'background:#f1f5f9;color:#374151;font-size:.75rem;font-weight:500;padding:.2em .55em;border-radius:5px',
                },
                row.cargo,
              )
            : h('span', { style: 'color:#94a3b8' }, '—'),
      },
    },
    { field: 'especialidad', title: 'Especialidad', minWidth: 140 },
    {
      field: 'activo',
      title: 'Estado',
      width: 90,
      slots: {
        default: ({ row }: { row: TitularDto }) => renderEstado(row.activo),
      },
    },
    {
      field: 'totalInternosSupervisados',
      title: 'Internos',
      width: 80,
      slots: {
        default: ({ row }: { row: TitularDto }) =>
          h(
            NTag,
            { type: 'info', size: 'small', round: true },
            { default: () => row.totalInternosSupervisados ?? 0 },
          ),
      },
    },
    {
      title: 'Acciones',
      width: 130,
      fixed: 'right',
      slots: {
        default: ({ row }: { row: TitularDto }) =>
          h(NSpace, { size: 4, justify: 'center' }, {
            default: () => [
              h(
                NTooltip,
                {},
                {
                  trigger: () =>
                    h(
                      NButton,
                      {
                        size: 'tiny',
                        type: 'warning',
                        ghost: true,
                        onClick: () => onAction({ code: 'edit', row }),
                      },
                      { default: () => '✏️' },
                    ),
                  default: () => 'Editar',
                },
              ),
              h(
                NTooltip,
                {},
                {
                  trigger: () =>
                    h(
                      NButton,
                      {
                        size: 'tiny',
                        type: row.activo ? 'error' : 'success',
                        ghost: true,
                        onClick: () => onAction({ code: 'toggle', row }),
                      },
                      { default: () => (row.activo ? '🚫' : '✅') },
                    ),
                  default: () => (row.activo ? 'Desactivar' : 'Activar'),
                },
              ),
              h(
                NTooltip,
                {},
                {
                  trigger: () =>
                    h(
                      NButton,
                      {
                        size: 'tiny',
                        type: 'info',
                        ghost: true,
                        onClick: () => onAction({ code: 'sync', row }),
                      },
                      { default: () => '🔄' },
                    ),
                  default: () => 'Sync Londra',
                },
              ),
            ],
          }),
      },
    },
  ];
}

// ═══════════════════════════════════════════════════════════════════════════
//  COLUMNAS — INTERNO
// ═══════════════════════════════════════════════════════════════════════════

export function useInternoColumns(
  onAction: (params: ActionParams<InternoDto, InternoActionCode>) => void,
): VxeTableGridOptions['columns'] {
  return [
    { type: 'seq', width: 50, title: '#', fixed: 'left' },
    {
      field: 'nombreCompleto',
      title: 'Nombre Completo',
      minWidth: 220,
      fixed: 'left',
      slots: {
        default: ({ row }: { row: InternoDto }) =>
          h('div', { style: 'display:flex;align-items:center' }, [
            renderAvatar(row.apellido, '#1d4ed8'),
            h('div', {}, [
              h('div', { style: 'font-weight:600;font-size:.83rem' }, row.nombreCompleto ?? '—'),
              h(
                'div',
                { style: 'color:#94a3b8;font-size:.73rem' },
                row.nroMatricula ? `Mat: ${row.nroMatricula}` : '—',
              ),
            ]),
          ]),
      },
    },
    { field: 'ci', title: 'CI', width: 110 },
    { field: 'universidad', title: 'Universidad', minWidth: 130 },
    {
      field: 'carrera',
      title: 'Carrera / Año',
      minWidth: 150,
      slots: {
        default: ({ row }: { row: InternoDto }) =>
          h('div', {}, [
            h('div', { style: 'font-size:.83rem' }, row.carrera ?? '—'),
            row.anoFormacion
              ? h('div', { style: 'color:#94a3b8;font-size:.73rem' }, `${row.anoFormacion}° año`)
              : null,
          ]),
      },
    },
    {
      field: 'fechaInicioRotacion',
      title: 'Período de Rotación',
      minWidth: 160,
      slots: {
        default: ({ row }: { row: InternoDto }) => {
          const inicio = row.fechaInicioRotacion ?? '—';
          const fin    = row.fechaFinRotacion    ?? '—';
          return h('div', {}, [
            h('div', { style: 'font-size:.8rem' }, inicio),
            h('div', { style: 'color:#94a3b8;font-size:.73rem' }, `hasta ${fin}`),
          ]);
        },
      },
    },
    {
      field: 'activo',
      title: 'Estado',
      width: 110,
      slots: {
        default: ({ row }: { row: InternoDto }) => {
          if (!row.activo)
            return h(NTag, { type: 'error', size: 'small', round: true }, { default: () => 'Inactivo' });
          if (row.estaEnRotacion)
            return h(NTag, { type: 'info', size: 'small', round: true }, { default: () => 'En rotación' });
          return h(NTag, { type: 'warning', size: 'small', round: true }, { default: () => 'Fuera de rot.' });
        },
      },
    },
    {
      title: 'Acciones',
      width: 110,
      fixed: 'right',
      slots: {
        default: ({ row }: { row: InternoDto }) =>
          h(NSpace, { size: 4, justify: 'center' }, {
            default: () => [
              h(
                NTooltip,
                {},
                {
                  trigger: () =>
                    h(
                      NButton,
                      {
                        size: 'tiny',
                        type: 'warning',
                        ghost: true,
                        onClick: () => onAction({ code: 'edit', row }),
                      },
                      { default: () => '✏️' },
                    ),
                  default: () => 'Editar',
                },
              ),
              h(
                NTooltip,
                {},
                {
                  trigger: () =>
                    h(
                      NButton,
                      {
                        size: 'tiny',
                        type: row.activo ? 'error' : 'success',
                        ghost: true,
                        onClick: () => onAction({ code: 'toggle', row }),
                      },
                      { default: () => (row.activo ? '🚫' : '✅') },
                    ),
                  default: () => (row.activo ? 'Desactivar' : 'Activar'),
                },
              ),
              h(
                NTooltip,
                {},
                {
                  trigger: () =>
                    h(
                      NButton,
                      {
                        size: 'tiny',
                        type: 'primary',
                        ghost: true,
                        onClick: () => onAction({ code: 'qr', row }),
                      },
                      { default: () => '📱' },
                    ),
                  default: () => 'Regenerar QR',
                },
              ),
            ],
          }),
      },
    },
  ];
}