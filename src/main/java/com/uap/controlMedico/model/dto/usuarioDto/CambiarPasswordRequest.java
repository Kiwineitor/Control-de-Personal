package com.uap.controlMedico.model.dto.usuarioDto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Setter @Getter
public class CambiarPasswordRequest {
    @NotBlank(message = "La contraseña actual es obligatoria")
    private String passwordActual;

    @NotBlank(message = "La nueva contraseña es obligatoria")
    @Size(min = 6, message = "Mínimo 6 caracteres")
    private String nuevaPassword;

    @NotBlank(message = "La confirmación es obligatoria")
    private String confirmarPassword;
}
