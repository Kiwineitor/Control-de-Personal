package com.uap.controlMedico.security;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.uap.controlMedico.model.dao.UsuarioDao;
import com.uap.controlMedico.model.dao.UsuarioRolDao;
import com.uap.controlMedico.model.dto.usuarioDto.UsuarioSesion;
import com.uap.controlMedico.model.service.usuario.UsuarioService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SisuAuthSuccessHandler implements AuthenticationSuccessHandler {
    
    private final UsuarioService usuarioService;
 
    @Override
    public void onAuthenticationSuccess(HttpServletRequest  request,
                                        HttpServletResponse response,
                                        Authentication      authentication)
            throws IOException {
 
        String username = authentication.getName();
        HttpSession session = request.getSession();
        UsuarioSesion sesion = usuarioService.registrarLoginExitoso(username);
 
        if (sesion != null) {
            session.setAttribute("usuarioSisu", sesion);
            session.setAttribute("rol", "ROLE_" + sesion.getRolPrincipal());
            session.setAttribute("nombreUsuario", sesion.getNombreCompleto().isBlank()
                    ? sesion.getUsername()
                    : sesion.getNombreCompleto());

        }
 
        response.sendRedirect(request.getContextPath() + "/sicope");
    }
}
