package com.uap.controlMedico.model.dto.responsableInterno;

import java.time.LocalDate;
import java.util.List;

import com.uap.controlMedico.model.dto.internosDto.InternoAsignadoDto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResponsableResumenDto {
    
    // Titular responsable
    private Long   idTitular;
    private String nombreCompleto;
    private String cargo;
    private String especialidad;
    private String email;
    private String telefono;
 
    // Internos bajo su cargo
    private List<InternoAsignadoDto> internos;
    private int totalInternos;
 
    // Fecha de la asignación más reciente
    private LocalDate fechaAsignacion;

}
