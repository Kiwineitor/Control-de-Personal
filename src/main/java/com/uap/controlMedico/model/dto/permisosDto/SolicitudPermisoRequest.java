package com.uap.controlMedico.model.dto.permisosDto;

import com.uap.controlMedico.model.enums.TipoPermiso;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder
@NoArgsConstructor @AllArgsConstructor
public class SolicitudPermisoRequest {
    
    @NotNull(message = "El titular es obligatorio")
    private Long idTitular;

    /** Contexto jornada: si se provee, la jornada se deriva automáticamente. */
    private Long idTitularJornada;

    /** Contexto alternativo: jornada directa sin asignación específica. */
    private Long idJornada;

    @NotNull(message = "El tipo de permiso es obligatorio")
    private TipoPermiso tipoPermiso;

    @NotBlank(message = "El motivo de la solicitud es obligatorio")
    private String motivoSolicitud;

    /** Formato: yyyy-MM-dd */
    private String fechaPermiso;

    /** Formato: HH:mm */
    private String horaInicioSolicitada;

    /** Formato: HH:mm */
    private String horaFinSolicitada;
}
