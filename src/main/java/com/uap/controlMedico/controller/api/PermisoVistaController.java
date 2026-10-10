package com.uap.controlMedico.controller.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uap.controlMedico.model.dto.permisovista.OverrideVistaDto;
import com.uap.controlMedico.model.dto.permisovista.UsuarioVistasResumen;
import com.uap.controlMedico.model.dto.permisovista.VistaDto;
import com.uap.controlMedico.model.dto.response.ApiResponse;
import com.uap.controlMedico.model.entity.Vista;
import com.uap.controlMedico.model.service.permiso.PermisoVistaService;

import lombok.RequiredArgsConstructor;

/**
 * Administración de permisos por VISTAS (menú administrable). Solo ADMIN: define
 * qué vistas trae cada rol y los ajustes (GRANT/DENY) por usuario.
 */
@RestController
@RequestMapping("/api/v1/vistas")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class PermisoVistaController {

    private final PermisoVistaService permisoVistaService;

    /** Catálogo completo de vistas asignables. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<VistaDto>>> catalogo() {
        List<VistaDto> data = permisoVistaService.getCatalogo().stream()
            .map(PermisoVistaController::toDto).toList();
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    /** Códigos de vista base de un rol. */
    @GetMapping("/rol/{idRol}")
    public ResponseEntity<ApiResponse<List<String>>> vistasDeRol(@PathVariable Long idRol) {
        return ResponseEntity.ok(ApiResponse.ok(permisoVistaService.getCodigosDeRol(idRol)));
    }

    /** Reemplaza las vistas base de un rol. */
    @PutMapping("/rol/{idRol}")
    public ResponseEntity<ApiResponse<Void>> setVistasDeRol(
        @PathVariable Long idRol,
        @RequestBody List<String> codigos
    ) {
        permisoVistaService.setVistasDeRol(idRol, codigos);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /** Resumen de permisos de un usuario (base por rol + overrides + efectivas). */
    @GetMapping("/usuario/{idUsuario}")
    public ResponseEntity<ApiResponse<UsuarioVistasResumen>> resumenUsuario(@PathVariable Long idUsuario) {
        return ResponseEntity.ok(ApiResponse.ok(permisoVistaService.getResumenUsuario(idUsuario)));
    }

    /** Reemplaza los overrides (GRANT/DENY) de un usuario. */
    @PutMapping("/usuario/{idUsuario}")
    public ResponseEntity<ApiResponse<Void>> setOverridesDeUsuario(
        @PathVariable Long idUsuario,
        @RequestBody List<OverrideVistaDto> overrides
    ) {
        permisoVistaService.setOverridesDeUsuario(idUsuario, overrides);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    private static VistaDto toDto(Vista v) {
        return VistaDto.builder()
            .idVista(v.getIdVista())
            .codigo(v.getCodigo())
            .nombre(v.getNombre())
            .grupo(v.getGrupo())
            .orden(v.getOrden())
            .activo(v.isActivo())
            .build();
    }
}
