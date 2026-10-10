package com.uap.controlMedico.model.dto.internosDto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InternoResumenResponse {
    
    private Long idInterno;
    private Long perId;
    private String nombreCompleto;
    private String ci;
    private String universidad;
    private String carrera;
    private boolean activo;

}
