package com.uap.controlMedico.controller.api;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Pageable;
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

import com.uap.controlMedico.model.dto.response.ApiResponse;
import com.uap.controlMedico.model.dto.rol.RolRequest;
import com.uap.controlMedico.model.dto.rol.RolResponse;
import com.uap.controlMedico.model.service.RolService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RolApiController {
    
    private final RolService rolService;

    // ── GET paginado (grid) ─────────────────────────────────────────────
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<RolResponse>>> getAll(
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String estado,
            @PageableDefault(size = 20, sort = "nombre",
                             direction = Sort.Direction.ASC) Pageable pageable) {

        return ResponseEntity.ok(
            ApiResponse.ok(rolService.getAll(nombre, estado, pageable)));
    }

    // ── GET select (formularios de usuario) ────────────────────────────
    @GetMapping("/activos")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<List<RolResponse>>> getAllActivos() {
        return ResponseEntity.ok(ApiResponse.ok(rolService.getAllActivos()));
    }

    // ── GET por ID ──────────────────────────────────────────────────────
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RolResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(rolService.getById(id)));
    }

    // ── POST crear ─────────────────────────────────────────────────────
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RolResponse>> crear(
            @Valid @RequestBody RolRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(rolService.crear(request)));
    }

    // ── POST editar (Decision #3: no @PatchMapping) ────────────────────
    @PostMapping("/{id}/editar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RolResponse>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody RolRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(rolService.actualizar(id, request)));
    }

    // ── POST cambiar estado ────────────────────────────────────────────
    @PostMapping("/{id}/activar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> activar(@PathVariable Long id) {
        rolService.activar(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/{id}/desactivar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> desactivar(@PathVariable Long id) {
        rolService.desactivar(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
