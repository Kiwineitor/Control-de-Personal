package com.uap.controlMedico.model.dto.permisosDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder
@NoArgsConstructor @AllArgsConstructor
public class AprobarPermisoRequest  {
    
    /**
     * Interno que cubrirá al titular.
     * Si es null → la solicitud se aprueba SIN crear delegación (sin reemplazo).
     */
    private Long idInternoResponsable;

    /** HH:mm — toma precedencia sobre el horario de la solicitud. */
    private String horaInicioPlanificada;

    /** HH:mm */
    private String horaFinPlanificada;

    private String observacion;
}
