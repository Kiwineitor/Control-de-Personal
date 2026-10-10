package com.uap.controlMedico.model.dto.permisosDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder
@NoArgsConstructor @AllArgsConstructor
public class AccionConObservacionRequest {
    /**
     * Requerido para RECHAZAR.
     * Opcional para CANCELAR / CANCELAR delegación.
     */
    private String observacion;
}
