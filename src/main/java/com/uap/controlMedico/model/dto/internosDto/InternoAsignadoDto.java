package com.uap.controlMedico.model.dto.internosDto;

import java.time.LocalDate;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InternoAsignadoDto {
    
    private Long      idInterno;
    private Long      idResponsableInterno;
    private String    nombreCompleto;
    private String    carrera;
    private String    universidad;
    private boolean   estaEnRotacion;
    private LocalDate fechaAsignacion;

}
