package com.uap.controlMedico.model.dto.actividadDto;

import com.uap.controlMedico.model.enums.TipoPermiso;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MiSolicitudRequest {
    
    @NotNull(message = "El tipo de permiso es obligatorio")
    private TipoPermiso tipoPermiso;

    @NotBlank(message = "El motivo es obligatorio")
    @Size(max = 500)
    private String motivoSolicitud;

    private String fechaPermiso;    // yyyy-MM-dd
    private String horaInicio;      // HH:mm
    private String horaFin;         // HH:mm
}
