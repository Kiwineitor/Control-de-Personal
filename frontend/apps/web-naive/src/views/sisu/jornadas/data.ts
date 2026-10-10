/**
 * Definición de columnas VxeTable y Metadatos — Módulo Jornadas
 * Ruta: apps/web-naive/src/views/sisu/jornadas/data.ts
 */
import { h } from 'vue';
import { NButton, NSpace, NTag, NTooltip } from 'naive-ui';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { JornadaDto } from '#/api/sisu/jornada';

// ─────────────────────────────────────────────────────────────────────────
//  Metadatos para Etiquetas (Badges) y Colores
// ─────────────────────────────────────────────────────────────────────────

export const TIPO_META: Record<string, { label: string; color: string }> = {
  REGULAR:     { label: '📋 Regular',      color: '#334155' },
  GUARDIA_12H: { label: '🛡️ Guardia 12h',  color: '#0f766e' },
  GUARDIA_24H: { label: '🔒 Guardia 24h',  color: '#155e75' },
  NOCTURNA:    { label: '🌙 Nocturna',      color: '#312e81' },
  ROTACION:    { label: '🔄 Rotación',      color: '#5b21b6' },
  LLAMADA:     { label: '📞 De Llamada',    color: '#d97706' },
  EMERGENCIA:  { label: '🚨 Emergencia',    color: '#e63946' },
  COBERTURA:      { label: '📌 Cobertura',      color: '#9f1239' },
  ADMINISTRATIVA: { label: '🏢 Administrativa', color: '#0369a1' },
  GUARDIA:        { label: '🔒 Guardia',        color: '#0f766e' }, // legacy
};

export const ESTADO_JORNADA_META: Record<string, { label: string; type: 'default' | 'success' | 'warning' | 'error' | 'info' }> = {
  ACTIVA:    { label: '✅ Activa',    type: 'success' },
  CERRADA:   { label: '🔒 Cerrada',   type: 'warning' },
  CANCELADA: { label: '✕ Cancelada', type: 'error'   },
};

// ─────────────────────────────────────────────────────────────────────────
//  Acciones
// ─────────────────────────────────────────────────────────────────────────

export type JornadaActionCode = 'view' | 'edit' | 'cerrar' | 'cancelar';

export interface ActionParams {
  code: JornadaActionCode;
  row: JornadaDto;
}

// ─────────────────────────────────────────────────────────────────────────
//  Helpers de Render (Opcional, para mantener limpio el código de columnas)
// ─────────────────────────────────────────────────────────────────────────

function formatHora(hora: string | null | undefined) {
  return hora ? hora.slice(0, 5) : '—';
}

// ═══════════════════════════════════════════════════════════════════════════
//  COLUMNAS — JORNADAS
// ═══════════════════════════════════════════════════════════════════════════

export function useJornadaColumns(
  onAction: (params: ActionParams) => void,
): VxeTableGridOptions['columns'] {
  return [
    { type: 'seq', width: 50, title: '#', fixed: 'left' },
    
    // Columna 1: Nombre y Tipo de Jornada
    {
      field: 'nombre',
      title: 'Jornada',
      minWidth: 200,
      fixed: 'left',
      slots: {
        default: ({ row }: { row: JornadaDto }) => {
          const metaInfo = TIPO_META[row.tipoJornada] || { label: row.tipoJornada, color: '#94a3b8' };
          return h('div', {}, [
            h('div', { style: 'font-weight:600; font-size:0.85rem;' }, row.nombre || `Jornada ${row.idJornada}`),
            h('div', { style: `color: ${metaInfo.color}; font-size: 0.75rem; font-weight: 500;` }, metaInfo.label),
          ]);
        },
      },
    },

    // Columna 2: Fechas
    {
      field: 'fechaInicio',
      title: 'Período',
      minWidth: 140,
      slots: {
        default: ({ row }: { row: JornadaDto }) => h('div', {}, [
          h('div', { style: 'font-size: 0.8rem;' }, row.fechaInicio),
          h('div', { style: 'color: #94a3b8; font-size: 0.73rem;' }, `→ ${row.fechaFin}`),
        ]),
      },
    },

    // Columna 3: Horario y Duración
    {
      field: 'horaInicio',
      title: 'Horario',
      minWidth: 120,
      slots: {
        default: ({ row }: { row: JornadaDto }) => h('div', {}, [
          h('div', { style: 'font-size: 0.8rem; font-weight: 500;' }, `${formatHora(row.horaInicio)} - ${formatHora(row.horaFin)}`),
          h('div', { style: 'color: #94a3b8; font-size: 0.73rem;' }, `${row.duracionHoras} hrs`),
        ]),
      },
    },

    // Columna 4: Estado (Activa, Cerrada, En Curso)
    {
      field: 'estado',
      title: 'Estado',
      width: 120,
      slots: {
        default: ({ row }: { row: JornadaDto }) => {
          const meta = ESTADO_JORNADA_META[row.estado] || { label: row.estado, type: 'default' };
          
          const tags = [
            h(NTag, { type: meta.type, size: 'small', round: true }, { default: () => meta.label })
          ];

          // Si la jornada está en curso, agregamos un segundo badge
          if (row.estaEnCurso) {
            tags.push(
              h(NTag, { type: 'success', size: 'small', round: true, style: 'margin-left: 4px;' }, { default: () => '▶ En curso' })
            );
          }

          return h('div', { style: 'display: flex; align-items: center; flex-wrap: wrap; gap: 4px;' }, tags);
        },
      },
    },

    // Columna 5: Personal Asignado
    {
      field: 'personal',
      title: 'Personal',
      width: 140,
      slots: {
        default: ({ row }: { row: JornadaDto }) => h(NSpace, { size: 6 }, {
          default: () => [
            h(NTag, { type: 'error', size: 'small', round: true }, { default: () => `${row.totalTitulares || 0} T` }),
            h(NTag, { type: 'info',  size: 'small', round: true }, { default: () => `${row.totalInternos  || 0} I` })
          ]
        })
      }
    },

    // Columna 6: Acciones
    {
      title: 'Acciones',
      width: 160,
      fixed: 'right',
      slots: {
        default: ({ row }: { row: JornadaDto }) => h(NSpace, { size: 4, justify: 'center' }, {
          default: () => {
            const btns = [];

            // Botón: Ver Detalle
            btns.push(h(NTooltip, {}, {
              trigger: () => h(NButton, {
                size: 'tiny', type: 'info', ghost: true, onClick: () => onAction({ code: 'view', row })
              }, { default: () => '👁️' }),
              default: () => 'Ver Detalles'
            }));

            // Solo mostrar Editar, Cerrar y Cancelar si la jornada está ACTIVA
            if (row.estado === 'ACTIVA') {
              btns.push(h(NTooltip, {}, {
                trigger: () => h(NButton, {
                  size: 'tiny', type: 'warning', ghost: true, onClick: () => onAction({ code: 'edit', row })
                }, { default: () => '✏️' }),
                default: () => 'Editar'
              }));

              btns.push(h(NTooltip, {}, {
                trigger: () => h(NButton, {
                  size: 'tiny', type: 'warning', secondary: true, onClick: () => onAction({ code: 'cerrar', row })
                }, { default: () => '🔒' }),
                default: () => 'Cerrar Jornada'
              }));

              btns.push(h(NTooltip, {}, {
                trigger: () => h(NButton, {
                  size: 'tiny', type: 'error', ghost: true, onClick: () => onAction({ code: 'cancelar', row })
                }, { default: () => '✕' }),
                default: () => 'Cancelar Jornada'
              }));
            }

            return btns;
          }
        })
      }
    }
  ];
}