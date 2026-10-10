package com.uap.controlMedico.model.dto.actividadDto;

import java.time.LocalTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AutoReporteRequest {
    
    @NotNull(message = "La actividad es obligatoria")
    private Long idActividad;

    @Size(max = 500)
    private String observaciones;

    private LocalTime horaInicio;
    private LocalTime horaFin;
}
