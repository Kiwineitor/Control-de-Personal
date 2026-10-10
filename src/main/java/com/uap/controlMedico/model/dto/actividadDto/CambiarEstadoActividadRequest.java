package com.uap.controlMedico.model.dto.actividadDto;

import com.uap.controlMedico.model.enums.EstadoActividad;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class CambiarEstadoActividadRequest {
    
    @NotNull private EstadoActividad estado;
    private String observaciones;
}
