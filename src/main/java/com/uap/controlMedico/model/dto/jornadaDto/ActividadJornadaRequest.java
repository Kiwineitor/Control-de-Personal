package com.uap.controlMedico.model.dto.jornadaDto;

import java.time.LocalTime;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ActividadJornadaRequest {
    
    /**
     * Solo uno de los dos puede estar seteado.
     * Si es actividad de un interno → setear idInternoJornada.
     * Si es actividad de un titular → setear idTitularJornada.
     * Validación cruzada en el servicio.
     */
    private Long idInternoJornada;  // nullable — era @NotNull, se quita

    private Long idTitularJornada;  // nuevo campo
 
    @NotNull(message = "La actividad es obligatoria")
    private Long idActividad;
 
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private String observaciones;

}
