package com.uap.controlMedico.model.dto.actividadDto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class ValidarActividadRequest {
    
    @NotNull(message = "El titular validador es obligatorio")
    private Long   idTitular;
    private String observaciones;
}
