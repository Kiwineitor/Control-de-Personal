package com.uap.controlMedico.model.dto.gestionDto;

import java.time.LocalTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DelegacionRequest {
    
    /** Asignación del titular que necesita delegar. */
    @NotNull(message = "La asignación de turno del titular es obligatoria")
    private Long idTitularJornada;
 
    /** Interno que cubrirá al titular. */
    @NotNull(message = "El interno responsable es obligatorio")
    private Long idInternoResponsable;
 
    @NotBlank(message = "El motivo de la delegación es obligatorio")
    private String motivo;
 
    private LocalTime horaInicioPlanificada;
    private LocalTime horaFinPlanificada;

}
