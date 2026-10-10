import { h }                from 'vue';
import { NButton, NSpace, NTag } from 'naive-ui';
import type { LugarResponse, RolResponse, UsuarioResponse } from '#/api/admin/configuracion';

// ── Metadatos visuales ─────────────────────────────────────────────────

export const ESTADO_META: Record<string, { label: string; type: any }> = {
  ACTIVO:   { label: 'Activo',   type: 'success' },
  INACTIVO: { label: 'Inactivo', type: 'error'   },
};

export const ROL_META: Record<string, { label: string; type: any }> = {
  ADMIN:      { label: 'Administrador', type: 'error'   },
  SUPERVISOR: { label: 'Supervisor',    type: 'warning' },
  MEDICO:     { label: 'Médico',        type: 'success' },
  ENFERMERO:  { label: 'Enfermero',     type: 'info'    },
  CAJERO:     { label: 'Cajero',        type: 'default' },
  INTERNO:    { label: 'Interno',       type: 'default' },
};

export const TIPO_LUGAR_OPCIONES = [
  { label: 'Sala de Hospitalización', value: 'SALA_HOSPITALIZACION' },
  { label: 'Laboratorio',             value: 'LABORATORIO'          },
  { label: 'Consulta Externa',        value: 'CONSULTA_EXTERNA'     },
  { label: 'Urgencias',               value: 'URGENCIAS'            },
  { label: 'Quirófano',               value: 'QUIROFANO'            },
  { label: 'Administración',          value: 'ADMINISTRACION'       },
  { label: 'Farmacia',                value: 'FARMACIA'             },
  { label: 'Bodega',                  value: 'BODEGA'               },
  { label: 'Otro',                    value: 'OTRO'                 },
];

// ── Acción params ──────────────────────────────────────────────────────
export type ActionCode =
  | 'editar' | 'activar' | 'desactivar'
  | 'ver-mapa' | 'token-reset' | 'forzar-reset'
  | 'vistas' | 'permisos';

export interface ActionParams<T> { code: ActionCode; row: T }

// ── Columnas Lugares ───────────────────────────────────────────────────
export function useLugarColumnas(
  onAction: (p: ActionParams<LugarResponse>) => void
): any[] {
  return [
    { type: 'seq' as const, width: 50, align: 'center' as const },
    { field: 'nombre',    title: 'Nombre',    minWidth: 150 },
    { field: 'tipoLugar', title: 'Tipo',      width: 160, align: 'center' as const,
      slots: { default: ({ row }: { row: LugarResponse }) => [
        row.tipoLugar
          ? h(NTag, { type: 'info', size: 'small' }, () =>
              TIPO_LUGAR_OPCIONES.find(o => o.value === row.tipoLugar)?.label ?? row.tipoLugar)
          : h('span', { class: 'text-gray-400' }, '—')
      ]}
    },
    { field: 'capacidad', title: 'Capacidad', width: 100, align: 'center' as const,
      slots: { default: ({ row }: { row: LugarResponse }) => [
        h('span', row.capacidad ? `${row.capacidad} pers.` : '—')
      ]}
    },
    { field: 'tieneGeofencing', title: 'Geofencing', width: 110, align: 'center' as const,
      slots: { default: ({ row }: { row: LugarResponse }) => [
        h(NTag,
          { type: row.tieneGeofencing ? 'success' : 'default', size: 'small', round: true },
          () => row.tieneGeofencing ? '✓ Activo' : 'Sin config'
        )
      ]}
    },
    { field: 'activo', title: 'Estado', width: 90, align: 'center' as const,
      slots: { default: ({ row }: { row: LugarResponse }) => [
        h(NTag,
          { type: row.activo ? 'success' : 'error', size: 'small', round: true },
          () => row.activo ? 'Activo' : 'Inactivo'
        )
      ]}
    },
    { title: 'Acciones', fixed: 'right' as const, width: 190, align: 'center' as const,
      slots: { default: ({ row }: { row: LugarResponse }) => [
        h(NSpace, { size: 4 }, () => [
          h(NButton, { size: 'tiny', type: 'primary',
            onClick: () => onAction({ code: 'editar', row }) }, () => 'Editar'),
          row.tieneGeofencing && h(NButton, { size: 'tiny', type: 'info',
            onClick: () => onAction({ code: 'ver-mapa', row }) }, () => '🗺 Mapa'),
          h(NButton, {
            size: 'tiny',
            type: row.activo ? 'warning' : 'success',
            onClick: () => onAction({ code: row.activo ? 'desactivar' : 'activar', row })
          }, () => row.activo ? 'Desactivar' : 'Activar'),
        ])
      ]}
    },
  ];
}

// ── Columnas Roles ─────────────────────────────────────────────────────
export function useRolColumnas(
  onAction: (p: ActionParams<RolResponse>) => void
): any[] {
  return [
    { type: 'seq' as const, width: 50, align: 'center' as const },
    { field: 'nombre',          title: 'Nombre',   minWidth: 150 },
    { field: 'cantidadUsuarios',title: 'Usuarios', width: 100, align: 'center' as const },
    { field: 'estado', title: 'Estado', width: 100, align: 'center' as const,
      slots: { default: ({ row }: { row: RolResponse }) => [
        h(NTag,
          { type: row.estado === 'ACTIVO' ? 'success' : 'error', size: 'small', round: true },
          () => row.estado === 'ACTIVO' ? 'Activo' : 'Inactivo'
        )
      ]}
    },
    { field: 'fechaCreacion', title: 'Creado', width: 120, align: 'center' as const,
      slots: { default: ({ row }: { row: RolResponse }) => [
        h('span', row.fechaCreacion?.substring(0, 10) ?? '—')
      ]}
    },
    { title: 'Acciones', fixed: 'right' as const, width: 230, align: 'center' as const,
      slots: { default: ({ row }: { row: RolResponse }) => [
        h(NSpace, { size: 4 }, () => [
          h(NButton, { size: 'tiny', type: 'primary',
            onClick: () => onAction({ code: 'editar', row }) }, () => 'Editar'),
          h(NButton, { size: 'tiny', type: 'info', ghost: true,
            onClick: () => onAction({ code: 'vistas', row }) }, () => '🧩 Vistas'),
          h(NButton, {
            size: 'tiny',
            type: row.estado === 'ACTIVO' ? 'warning' : 'success',
            onClick: () => onAction({ code: row.estado === 'ACTIVO' ? 'desactivar' : 'activar', row })
          }, () => row.estado === 'ACTIVO' ? 'Desactivar' : 'Activar'),
        ])
      ]}
    },
  ];
}

// ── Columnas Usuarios ──────────────────────────────────────────────────
export function useUsuarioColumnas(
  onAction: (p: ActionParams<UsuarioResponse>) => void
): any[] {
  return [
    { type: 'seq' as const, width: 50, align: 'center' as const },
    { field: 'nombre', title: 'Nombre completo', minWidth: 160,
      slots: { default: ({ row }: { row: UsuarioResponse }) => [
        h('span', `${row.apellido}, ${row.nombre}`)
      ]}
    },
    { field: 'username', title: 'Username', width: 130 },
    { field: 'roles', title: 'Rol', width: 140, align: 'center' as const,
      slots: { default: ({ row }: { row: UsuarioResponse }) => {
        const rolKey = row.roles?.[0];
        const meta   = rolKey ? ROL_META[rolKey] : undefined;
        return [
          meta
            ? h(NTag, { type: meta.type, size: 'small' }, () => meta.label)
            : h('span', { class: 'text-gray-400' }, 'Sin rol'),
        ];
      }}
    },
    { field: 'requiereReset', title: 'Reset', width: 85, align: 'center' as const,
      slots: { default: ({ row }: { row: UsuarioResponse }) => [
        row.requiereReset
          ? h(NTag, { type: 'warning', size: 'small' }, () => '⚠ Pendiente')
          : h('span', { class: 'text-gray-400' }, '—')
      ]}
    },
    { field: 'activo', title: 'Estado', width: 90, align: 'center' as const,
      slots: { default: ({ row }: { row: UsuarioResponse }) => [
        h(NTag,
          { type: row.activo ? 'success' : 'error', size: 'small', round: true },
          () => row.activo ? 'Activo' : 'Inactivo'
        )
      ]}
    },
    { field: 'ultimoAcceso', title: 'Último acceso', width: 140, align: 'center' as const,
      slots: { default: ({ row }: { row: UsuarioResponse }) => [
        h('span', row.ultimoAcceso
          ? new Date(row.ultimoAcceso).toLocaleDateString('es-BO')
          : 'Nunca')
      ]}
    },
    { title: 'Acciones', fixed: 'right' as const, width: 300, align: 'center' as const,
      slots: { default: ({ row }: { row: UsuarioResponse }) => [
        h(NSpace, { size: 4 }, () => [
          h(NButton, { size: 'tiny', type: 'primary',
            onClick: () => onAction({ code: 'editar', row }) }, () => 'Editar'),
          h(NButton, { size: 'tiny', type: 'info', ghost: true,
            onClick: () => onAction({ code: 'permisos', row }) }, () => '🧩 Permisos'),
          h(NButton, { size: 'tiny', type: 'warning',
            onClick: () => onAction({ code: 'token-reset', row }) }, () => '🔑 Reset'),
          h(NButton, {
            size: 'tiny',
            type: row.activo ? 'error' : 'success',
            onClick: () => onAction({ code: row.activo ? 'desactivar' : 'activar', row })
          }, () => row.activo ? 'Desactivar' : 'Activar'),
        ])
      ]}
    },
  ];
}