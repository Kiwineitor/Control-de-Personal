package com.uap.controlMedico.model.dto.usuarioDto;

import java.util.List;

import com.uap.controlMedico.model.enums.RolNombre;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class UsuarioRequest {
    @NotBlank(message = "El username es obligatorio")
    @Size(min = 4, max = 50, message = "El username debe tener entre 4 y 50 caracteres")
    private String username;

    /** Solo obligatorio al CREAR. Null al editar = no cambia la contraseña. */
    @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
    private String password;

    /** ID de persona existente en el sistema (selección desde el select). */
    private Long idPersona;

    /** Datos para crear una nueva Persona inline (si idPersona es null). */
    @Valid
    private PersonaRequest personaNueva;

    /** Fallback: nombre directo cuando no hay persona vinculada. */
    @Size(max = 100)
    private String nombre;

    /** Fallback: apellido directo cuando no hay persona vinculada. */
    @Size(max = 100)
    private String apellido;

    private boolean activo = true;

    @NotNull(message = "El rol es obligatorio")
    private Long idRol;

    /** ID de persona en Londra. */
    private Long perId;

    private List<RolNombre> roles;
}
