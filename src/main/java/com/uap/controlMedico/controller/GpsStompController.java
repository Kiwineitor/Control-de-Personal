package com.uap.controlMedico.controller;

import java.security.Principal;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.uap.controlMedico.model.dto.monitoreo.UbicacionRequest;
import com.uap.controlMedico.model.dto.monitoreo.UbicacionResponse;
import com.uap.controlMedico.model.service.monitoreo.UbicacionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequiredArgsConstructor
@Slf4j
public class GpsStompController {
    
    private final UbicacionService      ubicacionService;
    private final SimpMessagingTemplate messaging;

    /**
     * Recibe coordenadas GPS desde la app vía STOMP.
     * Cliente publica en: /app/gps/location
     */
    @MessageMapping("/gps/location")
    public void recibirUbicacion(UbicacionRequest request, Principal principal) {
        if (principal == null) {
            log.warn("[GPS-PING] recibido SIN principal (sin autenticación STOMP) — descartado");
            return;
        }

        // ── LOG TEMPORAL DE DIAGNÓSTICO ──────────────────────────────────────
        log.info("[GPS-PING] recibido de '{}' → lat={}, lon={}",
            principal.getName(), request.getLatitud(), request.getLongitud());

        UbicacionResponse response =
            ubicacionService.registrarUbicacion(principal.getName(), request);

        log.info("[GPS-PING] '{}' → enTurnoActivo={}, estadoZona={}, lugar={}, dentroRadio={}, mensaje='{}'",
            principal.getName(),
            response.isEnTurnoActivo(),
            response.getEstadoZona(),
            response.getNombreLugar(),
            response.isDentroRadio(),
            response.getMensaje());
        // ─────────────────────────────────────────────────────────────────────

        // Devolver estado al usuario: /user/queue/gps-status
        messaging.convertAndSendToUser(
            principal.getName(),
            "/queue/gps-status",
            response
        );
    }
}
