package com.uap.controlMedico.model.dto.monitoreo;

import lombok.Builder;
import lombok.Getter;

/** Una incidencia (abandono, atraso, falta, etc.) para el historial por usuario del admin. */
@Getter
@Builder
public class IncidenciaPersonalDto {
    private Long    idEvento;
    private String  tipo;
    private String  tipoLabel;
    private String  estado;
    private String  estadoJustificacion;
    private String  estadoJustificacionLabel;
    private String  nombreLugar;
    private String  timestampSalida;
    private String  timestampRetorno;
    private Long    tiempoFueraMinutos;
    private Integer minutosIncidencia;
    private String  observaciones;
}
