package com.uap.controlMedico.model.dto.jornadaDto;

import com.uap.controlMedico.model.enums.EstadoAsignacion;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class CambiarEstadoAsignacionRequest {
    
    @NotNull(message = "El nuevo estado es obligatorio")
    private EstadoAsignacion estado;

    @Size(max = 500)
    private String observaciones;
}
