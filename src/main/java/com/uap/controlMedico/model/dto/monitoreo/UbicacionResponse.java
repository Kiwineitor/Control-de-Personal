package com.uap.controlMedico.model.dto.monitoreo;

import com.uap.controlMedico.model.enums.EstadoZona;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class UbicacionResponse {
    private boolean    dentroRadio;
    private double     distanciaMetros;
    private String     nombreLugar;
    private int        radioMetros;
    private String     mensaje;
    private String     timestamp;

    // Campos enriquecidos para GPS adaptativo
    private EstadoZona estadoZona;
    private Long       tiempoFueraMinutos;
    /** Segundos que debe esperar la app antes del próximo envío GPS. */
    private int        intervaloProximoEnvioSeg;
    /** Mensaje personalizado para mostrar al funcionario en la app. */
    private String     mensajePersonal;
    /** True si hay un EventoAbandono confirmado activo. */
    private boolean    abandonoConfirmado;
    /** True si el usuario tiene turno activo en este momento. */
    private boolean    enTurnoActivo;
}
