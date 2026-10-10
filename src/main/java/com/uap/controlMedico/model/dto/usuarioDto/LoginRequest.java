package com.uap.controlMedico.model.dto.usuarioDto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class LoginRequest {
    @NotBlank(message = "El username es obligatorio")
    private String username;
 
    @NotBlank(message = "La contraseña es obligatoria")
    private String password;

}
