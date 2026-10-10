package com.uap.controlMedico.model.dto.responsableInterno;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResponsableResponseDto {
    
    // Datos de la asignación
    private Long        idResponsableInterno;
    private boolean     activo;
    private LocalDate   fechaAsignacion;
    private LocalDate   fechaFinAsignacion;
    private String      observaciones;
    private String      creadoPor;
    private LocalDateTime fechaCreacion;
 
    // Datos del titular responsable
    private Long   idTitular;
    private String nombreCompletoTitular;
    private String cargoTitular;
    private String especialidadTitular;
    private String emailTitular;
    private String telefonoTitular;
 
    // Datos del interno asignado
    private Long   idInterno;
    private String nombreCompletoInterno;
    private String carreraInterno;
    private String universidadInterno;
}