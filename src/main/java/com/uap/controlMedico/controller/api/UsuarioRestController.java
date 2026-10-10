package com.uap.controlMedico.controller.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uap.controlMedico.model.dao.UsuarioDao;
import com.uap.controlMedico.model.dto.response.ApiResponse;
import com.uap.controlMedico.model.entity.Usuario;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UsuarioRestController {

    private final UsuarioDao usuarioDao;
    
    // ─────────────────────────────────────────────────────────────────
    //  GET /api/v1/user/info
    //  Retorna la info del usuario autenticado en el formato UserInfo
    //  que espera Vben Admin (@vben/types)
    // ─────────────────────────────────────────────────────────────────
    @GetMapping("/info")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserInfo(
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.error("No autenticado"));
        }

        String username = authentication.getName();

        // Cargar usuario real desde BD (nombre, apellido, id)
        Usuario usuario = usuarioDao.findByUsername(username).orElse(null);

        // Roles en formato BasicRole[] que espera Vben Admin
        // value = "ROLE_ADMIN" → usado por meta.authority en las rutas
        List<Map<String, String>> roles = authentication.getAuthorities()
                .stream()
                .map(auth -> buildRole(auth.getAuthority()))
                .toList();

        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("userId",   usuario != null
                                    ? String.valueOf(usuario.getIdUsuario())
                                    : String.valueOf(username.hashCode()));
        userInfo.put("username", username);
        userInfo.put("realName", usuario != null
                                    ? usuario.getNombre() + " " + usuario.getApellido()
                                    : username);
        userInfo.put("avatar",   "");
        userInfo.put("roles",    roles);
        userInfo.put("desc",     "Usuario SISU Hospital UAP");

        return ResponseEntity.ok(ApiResponse.ok(userInfo));
    }
 
    // ─────────────────────────────────────────────────────────────────
    //  Helpers privados
    // ─────────────────────────────────────────────────────────────────
 
    /**
     * Convierte el authority de Spring Security al formato BasicRole de Vben.
     * Ejemplo: "ADMIN" → { roleName: "Administrador", value: "ADMIN" }
     */
    private Map<String, String> buildRole(String authority) {
        String roleName = switch (authority) {
            case "ADMIN"      -> "Administrador";
            case "SUPERVISOR" -> "Supervisor";
            case "MEDICO"     -> "Médico";
            case "ENFERMERO"  -> "Enfermero";
            case "CAJERO"     -> "Cajero";
            case "INTERNO"    -> "Interno";
            default                -> authority.replace("ROLE_", "");
        };
        return Map.of("roleName", roleName, "value", authority);
    }
}