package com.uap.controlMedico.model.dto.monitoreo;

import lombok.Builder;
import lombok.Getter;

/**
 * Vista que el admin/supervisor usa para revisar una justificación enviada por
 * un funcionario (incidencia en estado de justificación EN_REVISION).
 */
@Getter
@Builder
public class IncidenciaRevisionDto {

    private Long    idEvento;
    private String  nombrePersonal;
    /** TipoIncidencia: ABANDONO_ZONA | AUSENCIA_SENAL | LOGOUT_EN_TURNO | ... */
    private String  tipo;
    private String  tipoLabel;
    /** EstadoEvento: ABANDONO_CONFIRMADO | ... */
    private String  estado;
    /** EstadoJustificacion: PENDIENTE | EN_REVISION | JUSTIFICADA | RECHAZADA */
    private String  estadoJustificacion;
    private String  nombreLugar;
    private String  timestampSalida;
    private Long    tiempoFueraMinutos;
    private Integer minutosIncidencia;
    private String  observaciones;

    // Datos de la justificación enviada por el funcionario.
    private String  motivoJustificacion;
    private String  justificanteUrl;
    private String  fechaSolicitudJustificacion;
}
