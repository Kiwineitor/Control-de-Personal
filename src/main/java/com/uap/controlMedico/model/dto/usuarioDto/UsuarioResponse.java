package com.uap.controlMedico.model.dto.usuarioDto;

import java.time.LocalDateTime;
import java.util.List;

import com.uap.controlMedico.model.enums.RolNombre;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Builder
public class UsuarioResponse {
    private Long idUsuario;
    private String username;
    private String nombre;
    private String apellido;
    private String email;
    private boolean activo;
    private boolean requiereReset;
    private LocalDateTime ultimoAcceso;
    private Long perId;
    private Long idRol;
    private List<RolNombre> roles;
    private LocalDateTime fechaCreacion;
    // Datos de Persona vinculada
    private Long   idPersona;
    private String ci;
    private String telefono;
    private String nombreCompleto;
}
