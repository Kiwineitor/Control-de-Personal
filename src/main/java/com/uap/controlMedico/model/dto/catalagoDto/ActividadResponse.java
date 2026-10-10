package com.uap.controlMedico.model.dto.catalagoDto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ActividadResponse {
    
    private Long idActividad;
    private String nombre;
    private String descripcion;
    private boolean activo;

}
