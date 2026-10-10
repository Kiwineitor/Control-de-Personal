package com.uap.controlMedico.model.dto.jornadaDto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InternoJornadaRequest {
    
    @NotNull(message = "El interno es obligatorio")
    private Long idInterno;
 
    @NotNull(message = "La jornada es obligatoria")
    private Long idJornada;
 
    @NotNull(message = "El lugar es obligatorio")
    private Long idLugar;
 
    private String observaciones;

}
