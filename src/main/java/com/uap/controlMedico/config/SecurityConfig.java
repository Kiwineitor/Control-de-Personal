package com.uap.controlMedico.config;


import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.uap.controlMedico.security.JwtAuthFilter;
import com.uap.controlMedico.security.SesionUnicaPolicy;
import com.uap.controlMedico.security.SisuAuthSuccessHandler;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    
    private final JwtAuthFilter          jwtAuthFilter;
    private final SisuAuthSuccessHandler authSuccessHandler;
 
    // ══════════════════════════════════════════════════════════════
    //  CADENA 1 — API REST /api/v1/** → JWT stateless
    // ══════════════════════════════════════════════════════════════
    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/v1/**")
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(s ->
                s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/v1/auth/login",
                    "/api/v1/asistencia/qr/escanear",
                    "/api/v1/auth/reset-password"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    // JwtAuthFilter marca este atributo cuando el token es válido pero
                    // pertenece a una sesión que otro dispositivo ya reemplazó. Sin
                    // distinguirlo, al usuario desplazado le salía "token expirado" y
                    // creía que era un fallo, en vez de entender que entró en otro
                    // equipo. El campo `motivo` permite además que el frontend
                    // reaccione distinto sin tener que interpretar el texto.
                    boolean desplazada = SesionUnicaPolicy.MOTIVO_SESION_DESPLAZADA
                        .equals(request.getAttribute(SesionUnicaPolicy.ATTR_MOTIVO));

                    String mensaje = desplazada
                        ? "Se inició sesión con esta cuenta en otro dispositivo. "
                          + "Vuelva a iniciar sesión para continuar aquí."
                        : "Token inválido o expirado. Inicie sesión nuevamente.";
                    String motivo = desplazada
                        ? SesionUnicaPolicy.MOTIVO_SESION_DESPLAZADA
                        : "TOKEN_INVALIDO";

                    response.setContentType("application/json;charset=UTF-8");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write(
                        "{\"code\":-1,\"success\":false," +
                        "\"motivo\":\"" + motivo + "\"," +
                        "\"message\":\"" + mensaje + "\"}"
                    );
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setContentType("application/json;charset=UTF-8");
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write(
                        "{\"code\":-1,\"success\":false," +
                        "\"message\":\"Sin permisos para acceder a este recurso.\"}"
                    );
                })
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
 
        return http.build();
    }
 
    // ══════════════════════════════════════════════════════════════
    //  CADENA 2 — Admin Web → Form Login con sesión
    // ══════════════════════════════════════════════════════════════
    @Bean
    @Order(2)
    public SecurityFilterChain webFilterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .authorizeHttpRequests(auth -> auth
                // Recursos públicos
                .requestMatchers(
                    "/login",
                    "/",
                    "/index.html",
                    "/auth/**",
                    "/app/**",
                    "/sisu/**",
                    "/admin/**",
                    "/monitoreo/**",
                    "/personal-hospital/**",
                    "/sistema/**",
                    "/assets/**",
                    "/css/**",
                    "/js/**",
                    "/jse/**",
                    "/img/**",
                    "/webjars/**",
                    "/favicon.ico",
                    "/iconosicope.png",
                    "/logo192.png",
                    "/logo512.png",
                    "/_app.config.js",
                    "/ws/**"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .successHandler(authSuccessHandler)
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .sessionManagement(session -> session
                .maximumSessions(1)
                .expiredUrl("/login?expired=true")
            );
 
        return http.build();
    }
 
    // ══════════════════════════════════════════════════════════════
    //  CORS — para Vue.js
    // ══════════════════════════════════════════════════════════════
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
 
        // Patrones (no orígenes exactos): permiten comodines y son compatibles
        // con allowCredentials=true. Cubre cualquier dispositivo de la red local
        // accediendo a la app generada vía IP LAN, en cualquier puerto.
        // PRODUCCIÓN: reemplazar el patrón 192.168.* por el dominio real del despliegue.
        config.setAllowedOriginPatterns(List.of(
            "http://localhost:*",       // Vue dev / preview en cualquier puerto
            "https://localhost:*",
            "http://127.0.0.1:*",
            "http://172.16.21.5:*",
            "http://virtual.uap.edu.bo:*",
            "http://192.168.*.*:*",     // ← cualquier IP/puerto de la red local (build web por IP LAN)
            "http://localhost",           // ← Capacitor Android (androidScheme: http)
            "https://localhost",          // ← Capacitor Android (androidScheme: https)
            "capacitor://localhost"       // ← Capacitor en algunas versiones
        ));
 
        config.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "Content-Disposition"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
 
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/**", config);
 
        return source;
    }

 
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
