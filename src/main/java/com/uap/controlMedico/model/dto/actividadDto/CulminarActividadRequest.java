package com.uap.controlMedico.model.dto.actividadDto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CulminarActividadRequest {
    @Size(max = 500, message = "Las observaciones no pueden superar 500 caracteres")
    private String observaciones;
}
