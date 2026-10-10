package com.uap.controlMedico.security;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uap.controlMedico.model.dao.UsuarioDao;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {
    
    private final UsuarioDao    usuarioDao;
 
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        // findByUsernameWithRoles usa LEFT JOIN FETCH → 1 sola query (no N+1)
        var usuario = usuarioDao.findByUsernameWithRoles(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuario no encontrado: " + username));

        // "ROLE_" + nombre → compatibilidad con hasRole('ADMIN') en @PreAuthorize
        List<SimpleGrantedAuthority> authorities = usuario.getUsuariosRol().stream()
                .filter(ur -> "ACTIVO".equals(ur.getRol().getEstado()))
                .map(ur -> new SimpleGrantedAuthority("ROLE_" + ur.getRol().getNombre()))
                .collect(Collectors.toList());

        // Equivalente al antiguo User.builder(), más el id de sesión única que
        // JwtAuthFilter / JwtChannelInterceptor contrastan con el claim `sid`.
        return new SisuUserDetails(
                usuario.getUsername(),
                usuario.getPassword(),
                usuario.isActivo(),          // enabled
                true,                        // accountNonExpired
                !usuario.isRequiereReset(),  // credentialsNonExpired → fuerza cambio de clave
                usuario.isActivo(),          // accountNonLocked
                authorities,
                usuario.getSesionActual());
    }
}