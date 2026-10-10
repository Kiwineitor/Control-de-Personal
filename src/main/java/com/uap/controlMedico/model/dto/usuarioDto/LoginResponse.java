package com.uap.controlMedico.model.dto.usuarioDto;

import java.util.List;

import com.uap.controlMedico.model.enums.RolNombre;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {
    private String accessToken;
    private String tipo;        // "Bearer"
    private Long idUsuario;
    private String username;
    private String nombre;
    private String apellido;
    private List<String> roles;
}
