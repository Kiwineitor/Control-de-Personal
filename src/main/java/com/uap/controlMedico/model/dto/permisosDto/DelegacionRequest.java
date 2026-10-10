package com.uap.controlMedico.model.dto.permisosDto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder
@NoArgsConstructor @AllArgsConstructor
public class DelegacionRequest {
    
    @NotNull(message = "La asignación de jornada del titular es obligatoria")
    private Long idTitularJornada;

    @NotNull(message = "El interno responsable es obligatorio")
    private Long idInternoResponsable;

    @NotBlank(message = "El motivo es obligatorio")
    private String motivo;

    /** HH:mm */
    private String horaInicioPlanificada;

    /** HH:mm */
    private String horaFinPlanificada;
}
