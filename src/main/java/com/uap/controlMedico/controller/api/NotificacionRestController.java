package com.uap.controlMedico.controller.api;

import java.security.Principal;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.uap.controlMedico.model.dto.notificacionDto.EnviarNotificacionRequest;
import com.uap.controlMedico.model.dto.notificacionDto.NotificacionResponse;
import com.uap.controlMedico.model.dto.response.ApiResponse;
import com.uap.controlMedico.model.service.notificacion.NotificacionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/notificaciones")
@RequiredArgsConstructor
public class NotificacionRestController {

    private final NotificacionService notificacionService;

    /** Mis notificaciones (personales + globales), paginadas. */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<NotificacionResponse>>> getMisNotificaciones(
        @PageableDefault(size = 30, sort = "timestamp",
            direction = Sort.Direction.DESC) Pageable pageable,
        Principal principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            notificacionService.getMisNotificaciones(principal.getName(), pageable)));
    }

    /** Cantidad de notificaciones personales no leídas. */
    @GetMapping("/no-leidas")
    public ResponseEntity<ApiResponse<Long>> getNoLeidas(Principal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
            notificacionService.getCountNoLeidas(principal.getName())));
    }

    /** Marcar una notificación como leída. */
    @PostMapping("/{id}/leer")
    public ResponseEntity<ApiResponse<NotificacionResponse>> marcarLeida(
        @PathVariable Long id,
        Principal principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            notificacionService.marcarLeida(id, principal.getName())));
    }

    /** Marcar todas las notificaciones personales como leídas. */
    @PostMapping("/leer-todas")
    public ResponseEntity<ApiResponse<Integer>> marcarTodasLeidas(Principal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
            notificacionService.marcarTodasLeidas(principal.getName())));
    }

    /**
     * ADMIN / SUPERVISOR: enviar notificación personalizada o broadcast global.
     * Si {@code usernameDestinatario} es null/vacío → se envía a todos.
     */
    @PostMapping("/enviar")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ApiResponse<NotificacionResponse>> enviar(
        @Valid @RequestBody EnviarNotificacionRequest request,
        Principal principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            notificacionService.enviarPersonalizado(request, principal.getName())));
    }

    /**
     * ADMIN / SUPERVISOR: notificaciones personales ya enviadas a un titular/interno.
     * Alimenta la ficha de Vigilancia en Vivo. Mismo par tipo/id que
     * {@code /api/v1/monitoreo/personal/incidencias}.
     */
    @GetMapping("/personal")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ApiResponse<List<NotificacionResponse>>> notificacionesDePersonal(
        @RequestParam String tipo,
        @RequestParam Long id,
        @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            notificacionService.getNotificacionesDePersonal(tipo, id, limit)));
    }
}