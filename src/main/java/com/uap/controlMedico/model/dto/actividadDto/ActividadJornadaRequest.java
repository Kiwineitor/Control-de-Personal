package com.uap.controlMedico.model.dto.actividadDto;

import java.time.LocalTime;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class ActividadJornadaRequest {
    
    /** Solo uno de los dos puede estar seteado. Validado en el servicio. */
    private Long idInternoJornada;
    private Long idTitularJornada;

    @NotNull(message = "La actividad es obligatoria")
    private Long idActividad;

    private LocalTime horaInicio;
    private LocalTime horaFin;
    private String    observaciones;
}
