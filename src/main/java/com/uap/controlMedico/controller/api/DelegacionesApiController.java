package com.uap.controlMedico.controller.api;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.uap.controlMedico.model.dto.permisosDto.AccionConObservacionRequest;
import com.uap.controlMedico.model.dto.permisosDto.DelegacionRequest;
import com.uap.controlMedico.model.dto.permisosDto.DelegacionResponse;
import com.uap.controlMedico.model.dto.response.ApiResponse;
import com.uap.controlMedico.model.enums.EstadoDelegacion;
import com.uap.controlMedico.model.service.permiso.PermisoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/delegaciones")
@RequiredArgsConstructor
public class DelegacionesApiController {
    
    private final PermisoService permisosService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<DelegacionResponse>>> getDelegaciones(
            @RequestParam(required = false) Long idJornada,
            @RequestParam(required = false) EstadoDelegacion estado,
            @PageableDefault(size = 20, sort = "fechaCreacion", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
            permisosService.getDelegaciones(idJornada, estado, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DelegacionResponse>> getDelegacion(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(permisosService.getDelegacion(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DelegacionResponse>> crearDelegacion(
            @Valid @RequestBody DelegacionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(
            permisosService.crearDelegacion(request, userDetails.getUsername())));
    }

    @PostMapping("/{id}/aprobar")
    public ResponseEntity<ApiResponse<DelegacionResponse>> aprobarDelegacion(
            @PathVariable Long id,
            @RequestBody(required = false) AccionConObservacionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(
            permisosService.aprobarDelegacion(id, request, userDetails.getUsername())));
    }

    @PostMapping("/{id}/rechazar")
    public ResponseEntity<ApiResponse<DelegacionResponse>> rechazarDelegacion(
            @PathVariable Long id,
            @RequestBody AccionConObservacionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(
            permisosService.rechazarDelegacion(id, request, userDetails.getUsername())));
    }

    @PostMapping("/{id}/iniciar")
    public ResponseEntity<ApiResponse<DelegacionResponse>> iniciarDelegacion(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(permisosService.iniciarDelegacion(id)));
    }

    @PostMapping("/{id}/completar")
    public ResponseEntity<ApiResponse<DelegacionResponse>> completarDelegacion(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(permisosService.completarDelegacion(id)));
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<ApiResponse<DelegacionResponse>> cancelarDelegacion(
            @PathVariable Long id,
            @RequestBody(required = false) AccionConObservacionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(permisosService.cancelarDelegacion(id, request)));
    }

    @GetMapping("/activas")
    public ResponseEntity<ApiResponse<List<DelegacionResponse>>> getActivas() {
        return ResponseEntity.ok(ApiResponse.ok(permisosService.getDelegacionesActivas()));
    }

    @GetMapping("/por-jornada/{idJornada}")
    public ResponseEntity<ApiResponse<List<DelegacionResponse>>> getPorJornada(@PathVariable Long idJornada) {
        return ResponseEntity.ok(ApiResponse.ok(permisosService.getDelegacionesPorJornada(idJornada)));
    }
}
