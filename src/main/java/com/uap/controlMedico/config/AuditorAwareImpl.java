package com.uap.controlMedico.config;

import java.util.Optional;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component("auditorAware")
public class AuditorAwareImpl implements AuditorAware<String> {
    
    private static final String SISTEMA = "SISTEMA";
 
    /**
     * Retorna el username del usuario autenticado actualmente.
     *
     * Si no hay autenticación activa (ej: durante tests de integración
     * o procesos programados), retorna "SISTEMA" como fallback.
     */
    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
 
        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return Optional.of(SISTEMA);
        }
 
        return Optional.of(authentication.getName());
    }

}
