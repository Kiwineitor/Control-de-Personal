package com.uap.controlMedico.controller.api;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.uap.controlMedico.model.dto.permisosDto.AccionConObservacionRequest;
import com.uap.controlMedico.model.dto.permisosDto.AprobarPermisoRequest;
import com.uap.controlMedico.model.dto.permisosDto.SolicitudPermisoRequest;
import com.uap.controlMedico.model.dto.permisosDto.SolicitudPermisoResponse;
import com.uap.controlMedico.model.dto.response.ApiResponse;
import com.uap.controlMedico.model.enums.EstadoPermiso;
import com.uap.controlMedico.model.enums.EstadoSolicitud;
import com.uap.controlMedico.model.enums.TipoPermiso;
import com.uap.controlMedico.model.service.permiso.PermisoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/permisos")
@RequiredArgsConstructor
public class PermisoRestController {
    
    private final PermisoService permisosService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<SolicitudPermisoResponse>>> getSolicitudes(
            @RequestParam(required = false) Long idTitular,
            @RequestParam(required = false) Long idJornada,
            @RequestParam(required = false) EstadoSolicitud estado,
            @RequestParam(required = false) TipoPermiso tipoPermiso,
            @PageableDefault(size = 20, sort = "fechaHoraSolicitud", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
            permisosService.getSolicitudes(idTitular, idJornada, estado, tipoPermiso, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SolicitudPermisoResponse>> getSolicitud(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(permisosService.getSolicitud(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SolicitudPermisoResponse>> crearSolicitud(
            @Valid @RequestBody SolicitudPermisoRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(permisosService.crearSolicitud(request)));
    }

    @PostMapping("/{id}/aprobar")
    public ResponseEntity<ApiResponse<SolicitudPermisoResponse>> aprobarSolicitud(
            @PathVariable Long id,
            @RequestBody AprobarPermisoRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(
            permisosService.aprobarSolicitud(id, request, userDetails.getUsername())));
    }

    @PostMapping("/{id}/rechazar")
    public ResponseEntity<ApiResponse<SolicitudPermisoResponse>> rechazarSolicitud(
            @PathVariable Long id,
            @RequestBody(required = false) AccionConObservacionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(
            permisosService.rechazarSolicitud(id, request, userDetails.getUsername())));
    }

    @PostMapping("/{id}/observar")
    public ResponseEntity<ApiResponse<SolicitudPermisoResponse>> observarSolicitud(
            @PathVariable Long id,
            @RequestBody AccionConObservacionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(
            permisosService.observarSolicitud(id, request, userDetails.getUsername())));
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<ApiResponse<SolicitudPermisoResponse>> cancelarSolicitud(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(permisosService.cancelarSolicitud(id)));
    }

    @GetMapping("/pendientes")
    public ResponseEntity<ApiResponse<List<SolicitudPermisoResponse>>> getPendientes() {
        return ResponseEntity.ok(ApiResponse.ok(permisosService.getSolicitudesPendientes()));
    }

    @GetMapping("/por-titular/{idTitular}")
    public ResponseEntity<ApiResponse<List<SolicitudPermisoResponse>>> getPorTitular(
            @PathVariable Long idTitular) {
        return ResponseEntity.ok(ApiResponse.ok(permisosService.getSolicitudesPorTitular(idTitular)));
    }

    @GetMapping("/estadisticas")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEstadisticas() {
        return ResponseEntity.ok(ApiResponse.ok(permisosService.getEstadisticas()));
    }

    @PostMapping("/{id}/subir-justificante")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','MEDICO','ENFERMERO','CAJERO','FARMACIA','LIMPIEZA')")
    public ResponseEntity<ApiResponse<SolicitudPermisoResponse>> subirJustificante(
        @PathVariable Long id,
        @RequestParam("file") MultipartFile file
    ) throws IOException {
        return ResponseEntity.ok(ApiResponse.ok(
            permisosService.subirJustificante(id, file)));
    }
}