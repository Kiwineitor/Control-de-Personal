package com.uap.controlMedico.model.dto.personalDto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TipoPersonalRequest {
    @NotBlank @Size(max = 80)
    private String  nombre;
    @Size(max = 200)
    private String  descripcion;
    private String  color;
    private boolean esPersonalMedico       = false;
    private boolean puedeGestionarInternos = false;
    private boolean activo                 = true;
}
