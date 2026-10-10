package com.uap.controlMedico.controller.api;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uap.controlMedico.model.dto.response.ApiResponse;
import com.uap.controlMedico.model.dto.usuarioDto.LoginRequest;
import com.uap.controlMedico.model.dto.usuarioDto.LoginResponse;
import com.uap.controlMedico.model.dto.usuarioDto.ResetPasswordRequest;
import com.uap.controlMedico.model.service.monitoreo.UbicacionService;
import com.uap.controlMedico.model.service.permiso.PermisoVistaService;
import com.uap.controlMedico.model.service.usuario.UsuarioService;
import com.uap.controlMedico.security.JwtUtil;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthRestController {
    
    private final UsuarioService       usuarioService;
    private final JwtUtil              jwtUtil;
    private final UserDetailsService   userDetailsService;
    private final UbicacionService     ubicacionService;
    private final PermisoVistaService  permisoVistaService;
 
    /**
     * Login de usuario.
     * Devuelve el token JWT para usar en las demás peticiones.
     *
     * POST /api/v1/auth/login
     * Body: { "username": "...", "password": "..." }
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {
 
        // Valida credenciales y obtiene datos del usuario
        LoginResponse loginData = usuarioService.login(request);

        // Sesión única: se abre una sesión nueva ANTES de emitir el token. A partir
        // de este punto el JWT del dispositivo anterior deja de ser aceptado, tanto
        // en REST (JwtAuthFilter) como en WebSocket (JwtChannelInterceptor).
        String sesionId = usuarioService.abrirSesionUnica(loginData.getIdUsuario());

        // Genera el JWT ligado a esa sesión
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getUsername());
        String token = jwtUtil.generarToken(userDetails, sesionId);

        // Devuelve respuesta con token incluido
        LoginResponse response = LoginResponse.builder()
                .accessToken(token)
                .tipo("Bearer")
                .idUsuario(loginData.getIdUsuario())
                .username(loginData.getUsername())
                .nombre(loginData.getNombre())
                .apellido(loginData.getApellido())
                .roles(loginData.getRoles())
                .build();
 
        return ResponseEntity.ok(ApiResponse.ok("Login exitoso", response));
    }

    /**
     * GET /api/v1/auth/codes
     * Vben Admin lo llama para obtener los códigos de permisos del usuario.
     * Devolvemos los roles del usuario autenticado.
     */
    @GetMapping("/codes")
    public ResponseEntity<ApiResponse<List<String>>> getCodes(
            Authentication authentication) {

        // Roles (ROLE_*) ∪ vistas efectivas del usuario. Aditivo: las rutas tagueadas
        // por rol (portal /app) siguen funcionando; el menú admin se filtra por vistas.
        java.util.LinkedHashSet<String> codes = new java.util.LinkedHashSet<>();
        authentication.getAuthorities().forEach(a -> codes.add(a.getAuthority()));
        codes.addAll(permisoVistaService.getVistasEfectivas(authentication.getName()));

        return ResponseEntity.ok(ApiResponse.ok(new java.util.ArrayList<>(codes)));
    }

    /**
     * POST /api/v1/auth/refresh
     * Stub: en JWT stateless no hay refresh real.
     * Retornamos error 401 para que Vben Admin fuerce re-login.
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Void>> refresh() {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Token expirado. Por favor inicie sesión nuevamente."));
    }

    /**
     * POST /api/v1/auth/logout
     * Cierra la sesión GPS activa del usuario antes de salir.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(Authentication authentication) {
        if (authentication != null && authentication.getName() != null) {
            try {
                ubicacionService.cerrarSesionActiva(authentication.getName());
            } catch (Exception e) {
                // No bloquear el logout si falla el cierre de sesión GPS
            }
            try {
                // Sesión única: al cerrarla, el token que se acaba de usar deja de valer.
                usuarioService.cerrarSesionUnica(authentication.getName());
            } catch (Exception e) {
                // No bloquear el logout si falla el cierre de la sesión única
            }
        }
        return ResponseEntity.ok(ApiResponse.ok("Sesión cerrada exitosamente"));
    }


    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        usuarioService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
