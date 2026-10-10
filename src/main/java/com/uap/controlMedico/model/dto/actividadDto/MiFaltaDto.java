package com.uap.controlMedico.model.dto.actividadDto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MiFaltaDto {

    private Long    idEvento;
    /** FUERA_ZONA | ABANDONO_CONFIRMADO | RETORNADO | RESUELTO */
    private String  estado;
    private String  nombreLugar;
    private String  timestampSalida;
    private String  timestampRetorno;
    private Long    tiempoFueraMinutos;
    private String  observaciones;

    // ── Tipo + justificación ────────────────────────────────────────────────
    /** TipoIncidencia: ABANDONO_ZONA | AUSENCIA_SENAL | LOGOUT_EN_TURNO | ... */
    private String  tipo;
    private String  tipoLabel;
    /** EstadoJustificacion: NO_REQUERIDA | PENDIENTE | EN_REVISION | JUSTIFICADA | RECHAZADA */
    private String  estadoJustificacion;
    private String  estadoJustificacionLabel;
    /** True si el funcionario aún puede enviar una justificación de esta incidencia. */
    private boolean puedeJustificar;
    private String  motivoJustificacion;
    private String  observacionRevision;
    /** Minutos de la incidencia cuando aplica (atraso/salida temprana). Null si no aplica. */
    private Integer minutosIncidencia;
}
