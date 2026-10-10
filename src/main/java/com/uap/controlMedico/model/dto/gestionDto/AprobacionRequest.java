package com.uap.controlMedico.model.dto.gestionDto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AprobacionRequest {
    
    @NotNull(message = "Debe indicar si aprueba o rechaza")
    private Boolean aprobado;
 
    /**
     * Obligatorio si aprobado = false.
     * El supervisor debe explicar el rechazo.
     */
    private String observacion;

}
