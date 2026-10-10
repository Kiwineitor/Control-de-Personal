package com.uap.controlMedico.model.dto.internosDto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResponsableInternoRequest {
    
    @NotNull(message = "El titular es obligatorio")
    private Long idTitular;
 
    @NotNull(message = "El interno es obligatorio")
    private Long idInterno;
 
    @NotNull(message = "La fecha de asignación es obligatoria")
    private LocalDate fechaAsignacion;
 
    private LocalDate fechaFinAsignacion;
 
    private String observaciones;

}
