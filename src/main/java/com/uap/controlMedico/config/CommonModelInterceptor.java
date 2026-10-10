package com.uap.controlMedico.config;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import com.uap.controlMedico.model.dto.usuarioDto.UsuarioSesion;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CommonModelInterceptor implements HandlerInterceptor {
        
        @Override
    public void postHandle(HttpServletRequest  request,
                           HttpServletResponse response,
                           Object handler,
                           ModelAndView modelAndView) {
 
        if (modelAndView == null) return;
 
        // URI actual para marcar ítem activo en sidebar
        modelAndView.addObject("currentUri", request.getRequestURI());
 
        // Datos del usuario desde sesión (cargados por SisuAuthSuccessHandler)
        Object sesionObj = request.getSession(false) != null
            ? request.getSession().getAttribute("usuarioSisu")
            : null;
 
        if (sesionObj instanceof UsuarioSesion sesion) {
            String nombre = sesion.getNombreCompleto().isBlank()
                ? sesion.getUsername()
                : sesion.getNombreCompleto();
            modelAndView.addObject("authUsername", nombre);
            modelAndView.addObject("authRol",      sesion.getRolDisplay());
        }
    }
}