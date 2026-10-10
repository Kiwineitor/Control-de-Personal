package com.uap.controlMedico.model.dto.usuarioDto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PersonaParaUsuarioResponse {
    private Long   idPersona;
    private String nombre;
    private String apellido;
    private String nombreCompleto;
    private String ci;
    private String email;
    private String telefono;
    private boolean tieneUsuario;
    private Boolean usuarioActivo;
}
