package com.uap.controlMedico.security;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Política de sesión única: decide si un token sigue siendo aceptable dado que el
 * usuario pudo haber iniciado sesión en otro dispositivo.
 *
 * Vive aparte porque la regla se aplica en DOS sitios —{@code JwtAuthFilter} para
 * REST y {@code JwtChannelInterceptor} para WebSocket— y ambos tienen que decidir
 * exactamente lo mismo: si divergieran, un dispositivo desplazado seguiría
 * recibiendo alertas y enviando GPS por el socket pese a estar fuera por REST.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SesionUnicaPolicy {

    /** Atributo de request que marca por qué se rechazó la autenticación. */
    public static final String ATTR_MOTIVO = "sisu.auth.motivo";

    /** Valor de {@link #ATTR_MOTIVO} cuando el usuario entró en otro dispositivo. */
    public static final String MOTIVO_SESION_DESPLAZADA = "SESION_DESPLAZADA";

    private final JwtUtil jwtUtil;

    /**
     * Roles que SÍ pueden mantener varias sesiones simultáneas.
     *
     * Por defecto, los de escritorio: un supervisor suele tener el mapa de monitoreo
     * abierto en la PC mientras lleva el teléfono encima, y aplicarles sesión única
     * los echaría de uno u otro cada vez que cambian de dispositivo. El personal de
     * campo sí queda restringido a una sesión, que es donde el requisito importa
     * (los conflictos de estado vienen del GPS y los turnos).
     *
     * Vaciar la propiedad para aplicar sesión única a TODOS sin excepción.
     */
    @Value("${sisu.sesion-unica.roles-exentos:ROLE_ADMIN,ROLE_SUPERVISOR}")
    private String rolesExentosRaw;

    private Set<String> rolesExentos = Set.of();

    @PostConstruct
    void init() {
        rolesExentos = rolesExentosRaw == null || rolesExentosRaw.isBlank()
            ? Set.of()
            : Arrays.stream(rolesExentosRaw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toCollection(LinkedHashSet::new));

        log.info("Sesión única activa. Roles exentos (permiten varias sesiones): {}",
            rolesExentos.isEmpty() ? "ninguno" : rolesExentos);
    }

    /** ¿A este usuario se le aplica la restricción de sesión única? */
    public boolean aplicaA(Collection<String> roles) {
        if (roles == null) return true;
        return roles.stream().noneMatch(rolesExentos::contains);
    }

    /**
     * ¿El token pertenece a la sesión vigente del usuario?
     *
     * @param sesionActualEnBd valor de {@code Usuario.sesionActual}; null si el
     *        usuario aún no ha iniciado sesión desde que existe esta función, en
     *        cuyo caso se acepta (migración suave, ver {@link JwtUtil#sesionCoincide}).
     * @param roles authorities del usuario, con prefijo {@code ROLE_}.
     */
    public boolean tokenVigente(String token, String sesionActualEnBd, Collection<String> roles) {
        if (!aplicaA(roles)) return true;
        return jwtUtil.sesionCoincide(token, sesionActualEnBd);
    }
}
