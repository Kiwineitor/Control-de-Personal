// apps/web-naive/src/views/sisu/monitoreo/data.ts
import { h } from 'vue';
import { NButton, NSpace, NTag, NProgress } from 'naive-ui';
import type { RondaVerificacionDto, TipoRonda, EstadoRonda,
              EstadoVerificacion, EstadoEntrada, EstadoSalida,
              MetodoVerificacion, EstadoZona, EstadoEvento } from '#/api/sisu/monitoreo';

// ── Metadatos: Monitoreo Continuo ─────────────────────────────────────────

export const ESTADO_ZONA_META: Record<EstadoZona, { label: string; type: any; color: string }> = {
  DENTRO:      { label: 'Dentro',      type: 'success', color: '#18a058' },
  FUERA:       { label: 'Fuera',       type: 'warning', color: '#f08c00' },
  DESCONOCIDO: { label: 'Sin datos',   type: 'default', color: '#888'    },
};

export const ESTADO_EVENTO_META: Record<EstadoEvento, { label: string; type: any }> = {
  FUERA_ZONA:          { label: '⚠️ Fuera de zona',    type: 'warning' },
  ABANDONO_CONFIRMADO: { label: '🚨 Abandono',          type: 'error'   },
  RETORNADO:           { label: '✅ Retornado',          type: 'success' },
  RESUELTO:            { label: '✅ Resuelto',           type: 'success' },
};

// ── Metadatos ─────────────────────────────────────────────────────────────

export const TIPO_RONDA_META: Record<TipoRonda, { label: string; color: string; bg: string }> = {
  ALEATORIA:  { label: 'Aleatoria',  color: '#7c3aed', bg: '#ede9fe' },
  PROGRAMADA: { label: 'Programada', color: '#1d4ed8', bg: '#dbeafe' },
  MANUAL:     { label: 'Manual',     color: '#b45309', bg: '#fef3c7' },
};

export const ESTADO_RONDA_META: Record<EstadoRonda, { label: string; type: any; color: string }> = {
  PENDIENTE:  { label: 'Pendiente',  type: 'default', color: '#94a3b8' },
  EN_CURSO:   { label: 'En curso',   type: 'info',    color: '#3b82f6' },
  COMPLETADA: { label: 'Completada', type: 'success', color: '#22c55e' },
  CANCELADA:  { label: 'Cancelada',  type: 'error',   color: '#ef4444' },
};

export const ESTADO_VERIF_META: Record<EstadoVerificacion, { label: string; type: any; color: string }> = {
  PENDIENTE:     { label: 'Pendiente',    type: 'default', color: '#94a3b8' },
  PRESENTE:      { label: 'Presente ✓',  type: 'success', color: '#22c55e' },
  AUSENTE:       { label: 'Ausente',      type: 'error',   color: '#ef4444' },
  SIN_RESPUESTA: { label: 'Sin respuesta', type: 'warning', color: '#f59e0b' },
};

export const ESTADO_ENTRADA_META: Record<EstadoEntrada, { label: string; type: any }> = {
  A_TIEMPO:    { label: 'A tiempo',    type: 'success' },
  TARDANZA:    { label: 'Tardanza',    type: 'warning' },
  AUSENTE:     { label: 'Ausente',     type: 'error'   },
  JUSTIFICADO: { label: 'Justificado', type: 'info'    },
};

export const ESTADO_SALIDA_META: Record<EstadoSalida, { label: string; type: any }> = {
  COMPLETA:          { label: 'Completa',           type: 'success' },
  SALIDA_ANTICIPADA: { label: 'Salida anticipada',  type: 'warning' },
  NO_REGISTRADA:     { label: 'No registrada',      type: 'default' },
};

export const METODO_VERIF_META: Record<MetodoVerificacion, { label: string; color: string }> = {
  GPS:    { label: '📍 GPS',   color: '#3b82f6' },
  QR:     { label: '📱 QR',   color: '#7c3aed' },
  FOTO:   { label: '📷 Foto', color: '#b45309' },
  MANUAL: { label: '✏ Manual', color: '#64748b' },
};

// ── Columnas VxeGrid — Rondas ─────────────────────────────────────────────

export type RondaActionCode = 'view' | 'iniciar' | 'cerrar' | 'cancelar';
export interface RondaActionParams { code: RondaActionCode; row: RondaVerificacionDto; }

export function useRondaColumns(onAction: (p: RondaActionParams) => void) {
  return [
    { type: 'seq' as const, width: 55, title: '#', align: 'center' as const},
    {
      field: 'nombre',
      title: 'Ronda',
      minWidth: 200,
      slots: {
        default: ({ row }: { row: RondaVerificacionDto }) => {
          const tipo = TIPO_RONDA_META[row.tipoRonda];
          const label = row.nombre || `Ronda ${row.tipoRonda}`;
          return [
            h('div', { style: { fontWeight: 600, fontSize: '0.84rem' } }, label),
            h('span', {
              style: {
                padding: '1px 8px', borderRadius: '10px', fontSize: '0.7rem',
                fontWeight: 600, background: tipo?.bg, color: tipo?.color, marginTop: '3px',
                display: 'inline-block',
              },
            }, tipo?.label),
          ];
        },
      },
    },
    {
      field: 'fechaHoraInicio',
      title: 'Inicio',
      width: 160,
      align: 'center' as const,
      slots: {
        default: ({ row }: { row: RondaVerificacionDto }) => {
          const dt = row.fechaHoraInicio ? new Date(row.fechaHoraInicio) : null;
          return dt ? [
            h('div', { style: { fontSize: '0.81rem', fontWeight: 500 } },
              dt.toLocaleDateString('es-BO', { day: '2-digit', month: '2-digit', year: '2-digit' })),
            h('div', { style: { fontSize: '0.74rem', color: '#94a3b8' } },
              dt.toLocaleTimeString('es-BO', { hour: '2-digit', minute: '2-digit' })),
          ] : [h('span', '—')];
        },
      },
    },
    {
      field: 'estado',
      title: 'Estado',
      width: 115,
      align: 'center' as const,
      slots: {
        default: ({ row }: { row: RondaVerificacionDto }) => {
          const m = ESTADO_RONDA_META[row.estado];
          return [h(NTag, { type: m?.type ?? 'default', size: 'small', round: true }, () => m?.label)];
        },
      },
    },
    {
      title: '% Presencia',
      width: 130,
      align: 'center' as const,
      slots: {
        default: ({ row }: { row: RondaVerificacionDto }) => [
          h('div', { style: { fontSize: '0.8rem', fontWeight: 700, marginBottom: '3px' } },
            `${row.porcentajePresencia}%  (${row.totalPresentes}/${row.totalVerificaciones})`),
          h(NProgress, {
            type: 'line', percentage: row.porcentajePresencia,
            color: row.porcentajePresencia >= 80 ? '#22c55e' : row.porcentajePresencia >= 50 ? '#f59e0b' : '#ef4444',
            height: 6, showIndicator: false,
          }),
        ],
      },
    },
    {
      title: 'Acciones',
      width: 175,
      align: 'center' as const,
      fixed: 'right' as const,
      slots: {
        default: ({ row }: { row: RondaVerificacionDto }) => [
          h(NSpace, { size: 3, justify: 'center' }, () => [
            h(NButton, {
              size: 'tiny', type: 'primary', ghost: true,
              onClick: () => onAction({ code: 'view', row }),
            }, () => '👁 Ver'),
            row.estado === 'PENDIENTE'
              ? h(NButton, {
                  size: 'tiny', type: 'info', ghost: true,
                  onClick: () => onAction({ code: 'iniciar', row }),
                }, () => '▶ Iniciar')
              : null,
            row.estado === 'EN_CURSO'
              ? h(NButton, {
                  size: 'tiny', type: 'warning', ghost: true,
                  onClick: () => onAction({ code: 'cerrar', row }),
                }, () => '🔒 Cerrar')
              : null,
            (row.estado === 'PENDIENTE' || row.estado === 'EN_CURSO')
              ? h(NButton, {
                  size: 'tiny', type: 'error', ghost: true,
                  onClick: () => onAction({ code: 'cancelar', row }),
                }, () => '✕')
              : null,
          ]),
        ],
      },
    },
  ];
}