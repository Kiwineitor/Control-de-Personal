package com.uap.controlMedico.security;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

/**
 * UserDetails de SISU: además de lo estándar transporta el identificador de la
 * sesión activa del usuario (sesión única).
 *
 * Se lleva aquí en lugar de consultarlo aparte porque {@code JwtAuthFilter} ya
 * carga el usuario desde la BD en cada petición; adosar el dato a ese mismo
 * resultado evita una segunda consulta por request.
 */
public class SisuUserDetails extends User {

    private final transient String sesionActual;

    public SisuUserDetails(String username,
                           String password,
                           boolean enabled,
                           boolean accountNonExpired,
                           boolean credentialsNonExpired,
                           boolean accountNonLocked,
                           Collection<? extends GrantedAuthority> authorities,
                           String sesionActual) {
        super(username, password, enabled, accountNonExpired,
              credentialsNonExpired, accountNonLocked, authorities);
        this.sesionActual = sesionActual;
    }

    /** UUID de la sesión vigente, o null si el usuario aún no abrió ninguna. */
    public String getSesionActual() {
        return sesionActual;
    }
}
