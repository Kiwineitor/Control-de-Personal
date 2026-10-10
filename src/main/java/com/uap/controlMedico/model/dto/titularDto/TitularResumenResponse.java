package com.uap.controlMedico.model.dto.titularDto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TitularResumenResponse {
    private Long idTitular;
    private Long perId;
    private String nombreCompleto;
    private String ci;
    private String cargo;
    private String especialidad;
    private boolean activo;
}
