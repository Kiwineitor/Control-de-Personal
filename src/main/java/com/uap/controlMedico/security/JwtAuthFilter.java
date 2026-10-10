package com.uap.controlMedico.security;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
     
    private final JwtUtil                jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;
    private final SesionUnicaPolicy      sesionUnicaPolicy;

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        final String header = request.getHeader(HEADER);

        if (header == null || !header.startsWith(PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        final String token = header.substring(PREFIX.length());
        final String username;

        try {
            username = jwtUtil.extraerUsername(token);
        } catch (Exception e) {
            chain.doFilter(request, response);
            return;
        }

        // Carga authorities siempre desde BD (garantiza roles actualizados)
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (jwtUtil.esValido(token, userDetails)) {
                    // Sesión única: además de firma y expiración, el token debe
                    // pertenecer al último login del usuario. Si inició sesión en otro
                    // dispositivo, este token ya no vale → 401 → la app va al login.
                    // Los roles salen de BD (no del token) para que un cambio de rol
                    // aplique de inmediato.
                    String sesionEnBd = (userDetails instanceof SisuUserDetails sisu)
                            ? sisu.getSesionActual()
                            : null;
                    List<String> roles = userDetails.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .collect(Collectors.toList());

                    if (sesionUnicaPolicy.tokenVigente(token, sesionEnBd, roles)) {
                        UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                        auth.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    } else {
                        // Se marca el motivo para que el entry point distinga este caso
                        // de un token simplemente vencido: al usuario hay que decirle
                        // que entró en otro dispositivo, no que "expiró la sesión".
                        request.setAttribute(
                            SesionUnicaPolicy.ATTR_MOTIVO,
                            SesionUnicaPolicy.MOTIVO_SESION_DESPLAZADA);
                    }
                }
            } catch (UsernameNotFoundException e) {
                // Usuario eliminado/desactivado → continuar sin autenticar → 401
            }
        }

        chain.doFilter(request, response);
    }
}