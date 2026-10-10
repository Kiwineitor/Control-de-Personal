package com.uap.controlMedico.model.dto.actividadDto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SubirEvidenciaResponse {
    private Long   idActividadJornada;
    private String evidenciaUrl;
    private String downloadUrl;
    private String mensaje;
}
