package com.uap.controlMedico.model.dto.jornadaDto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TitularJornadaRequest {
    
    @NotNull(message = "El titular es obligatorio")
    private Long idTitular;
 
    @NotNull(message = "La jornada es obligatoria")
    private Long idJornada;
 
    @NotNull(message = "El lugar es obligatorio")
    private Long idLugar;
 
    private String observaciones;

}
