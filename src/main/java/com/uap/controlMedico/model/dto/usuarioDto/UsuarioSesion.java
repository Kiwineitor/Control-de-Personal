package com.uap.controlMedico.model.dto.usuarioDto;

import java.io.Serializable;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioSesion implements Serializable {
    
    private Long   idUsuario;
    private String username;
    private String nombre;
    private String apellido;
    private String email;
    private String rolPrincipal;      // "ADMIN", "SUPERVISOR", etc. (sin ROLE_)
    private List<String> roles;       // todos los roles
 
    /** Nombre completo para mostrar en topbar */
    public String getNombreCompleto() {
        String n = nombre   != null ? nombre   : "";
        String a = apellido != null ? apellido : "";
        return (n + " " + a).trim();
    }
 
    /** Rol principal formateado para mostrar en topbar */
    public String getRolDisplay() {
        return rolPrincipal != null ? rolPrincipal : "USUARIO";
    }

}
