package com.uap.controlMedico.model.dto.titularDto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TitularResponse {
    
    private Long idTitular;
 
    // Referencia Londra
    private Long perId;
    private Long contratoId;
 
    // Datos cacheados de Londra
    private String nombre;
    private String apellido;
    private String nombreCompleto;
    private String ci;
    private String email;
    private String telefono;
    private String cargo;
    private String especialidad;
 
    // Datos SISU
    private boolean activo;
    private long totalInternosSupervisados;
    private LocalDateTime fechaCreacion;

}
