package com.uap.controlMedico.controller.api;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.uap.controlMedico.model.dto.push.PushSubscripcionRequest;
import com.uap.controlMedico.model.dto.response.ApiResponse;
import com.uap.controlMedico.model.service.push.PushNotificationService;
import com.uap.controlMedico.model.service.push.PushNotificationServiceImpl;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/push")
@RequiredArgsConstructor
public class PushController {

    private final PushNotificationService pushService;

    /**
     * GET /api/v1/push/public-key
     * Devuelve la clave pública VAPID para que el frontend suscriba el usuario.
     */
    @GetMapping("/public-key")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPublicKey() {
        String key = pushService.getPublicKey();
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "publicKey", key != null ? key : "",
            "enabled",   pushService.isEnabled()
        )));
    }

    /**
     * POST /api/v1/push/subscribe
     * Guarda la suscripción push del usuario autenticado.
     */
    @PostMapping("/subscribe")
    public ResponseEntity<ApiResponse<Void>> subscribe(
            Principal principal,
            @Valid @RequestBody PushSubscripcionRequest request) {

        pushService.guardarSubscripcion(principal.getName(), request);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /**
     * DELETE /api/v1/push/subscribe
     * Elimina la suscripción del usuario (logout o permiso revocado).
     */
    @DeleteMapping("/subscribe")
    public ResponseEntity<ApiResponse<Void>> unsubscribe(
            Principal principal,
            @RequestParam String endpoint) {

        pushService.eliminarSubscripcion(principal.getName(), endpoint);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /**
     * POST /api/v1/push/admin/generate-keys  (solo ADMIN)
     * Genera un nuevo par de claves VAPID. Copiar en application.properties.
     */
    @PostMapping("/admin/generate-keys")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> generateKeys() {
        if (!(pushService instanceof PushNotificationServiceImpl impl)) {
            return ResponseEntity.ok(ApiResponse.error("Servicio no disponible"));
        }
        var keys = impl.generateKeys();
        return ResponseEntity.ok(ApiResponse.ok(
            "Claves generadas. Cópialas en application.properties y reinicia.",
            Map.of("publicKey", keys.publicKey(), "privateKey", keys.privateKey())
        ));
    }
}
