package com.uap.controlMedico.controller.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.uap.controlMedico.model.dto.response.ApiResponse;
import com.uap.controlMedico.model.dto.usuarioDto.CambiarPasswordRequest;
import com.uap.controlMedico.model.dto.usuarioDto.TokenResetResponse;
import com.uap.controlMedico.model.dto.usuarioDto.UsuarioRequest;
import com.uap.controlMedico.model.dto.usuarioDto.UsuarioResponse;
import com.uap.controlMedico.model.service.usuario.UsuarioService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
public class UsuarioApiController {
    
    private final UsuarioService usuarioService;

    // ── Grid paginado ───────────────────────────────────────────────────
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<UsuarioResponse>>> getAll(
            @RequestParam(required = false) String  termino,
            @RequestParam(required = false) String  rolNombre,
            @RequestParam(required = false) Boolean activo,
            @PageableDefault(size = 20, sort = "apellido",
                             direction = Sort.Direction.ASC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.ok(
            usuarioService.getAll(termino, rolNombre, activo, pageable)));
    }

    // ── Por ID ──────────────────────────────────────────────────────────
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UsuarioResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(usuarioService.buscarPorId(id)));
    }

    // ── Crear ───────────────────────────────────────────────────────────
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UsuarioResponse>> crear(
            @Valid @RequestBody UsuarioRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(usuarioService.crear(request)));
    }

    // ── Editar (Decision #3: POST, no PATCH) ────────────────────────────
    @PostMapping("/{id}/editar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UsuarioResponse>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody UsuarioRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(usuarioService.actualizar(id, request)));
    }

    // ── Activar / Desactivar ────────────────────────────────────────────
    @PostMapping("/{id}/activar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> activar(@PathVariable Long id) {
        usuarioService.activar(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/{id}/desactivar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> desactivar(@PathVariable Long id) {
        usuarioService.desactivar(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ── Reset password (Admin habilita) ────────────────────────────────
    @PostMapping("/{id}/generar-token-reset")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TokenResetResponse>> generarTokenReset(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
            usuarioService.generarTokenReset(id)));
    }

    @PostMapping("/{id}/forzar-reset")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> forzarReset(@PathVariable Long id) {
        usuarioService.forzarCambioPassword(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ── Cambiar propia contraseña (cualquier usuario autenticado) ───────
    @PostMapping("/cambiar-password")
    public ResponseEntity<ApiResponse<Void>> cambiarPassword(
            Authentication authentication,
            @Valid @RequestBody CambiarPasswordRequest request) {
        usuarioService.cambiarPassword(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
