package com.uap.controlMedico.controller.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.uap.controlMedico.model.dto.internosDto.InternoRequest;
import com.uap.controlMedico.model.dto.internosDto.InternoResponse;
import com.uap.controlMedico.model.dto.internosDto.InternoResumenResponse;
import com.uap.controlMedico.model.dto.response.ApiResponse;
import com.uap.controlMedico.model.service.InternoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/internos")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
public class InternoRestController {
    
    private final InternoService internoService;
 
    // ─────────────────────────────────────────────────────────────────
    //  GET /api/v1/internos
    //  Params: page, size, q, carrera, universidad, enRotacion, activo
    // ─────────────────────────────────────────────────────────────────
    @GetMapping
    public ResponseEntity<ApiResponse<Page<InternoResponse>>> listar(
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "20")  int size,
            @RequestParam(required = false)     String q,
            @RequestParam(required = false)     String carrera,
            @RequestParam(required = false)     String universidad,
            @RequestParam(required = false)     Boolean enRotacion,
            @RequestParam(required = false)     Boolean activo) {
 
        PageRequest pageable = PageRequest.of(page, size,
            Sort.by("persona.apellido").ascending());

        Page<InternoResponse> resultado =
                internoService.listarConFiltros(q, carrera, universidad, enRotacion, activo, pageable);
 
        return ResponseEntity.ok(ApiResponse.ok(resultado));
    }
 
    // ─────────────────────────────────────────────────────────────────
    //  GET /api/v1/internos/{id}
    // ─────────────────────────────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InternoResponse>> buscarPorId(
            @PathVariable Long id) {
 
        return ResponseEntity.ok(ApiResponse.ok(internoService.buscarPorId(id)));
    }
 
    // ─────────────────────────────────────────────────────────────────
    //  POST /api/v1/internos  — Crear
    // ─────────────────────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<ApiResponse<InternoResponse>> crear(
            @Valid @RequestBody InternoRequest request) {
 
        InternoResponse creado = internoService.crear(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Interno registrado exitosamente", creado));
    }
    
 
    // ─────────────────────────────────────────────────────────────────
    //  PUT /api/v1/internos/{id}  — Actualizar
    // ─────────────────────────────────────────────────────────────────
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<InternoResponse>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody InternoRequest request) {
 
        InternoResponse actualizado = internoService.actualizar(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Interno actualizado exitosamente", actualizado));
    }
 
    // ─────────────────────────────────────────────────────────────────
    //  PATCH /api/v1/internos/{id}/toggle  — Activar / Desactivar
    // ─────────────────────────────────────────────────────────────────
    @PostMapping("/{id}/toggle")
    public ResponseEntity<ApiResponse<InternoResponse>> toggleActivo(@PathVariable Long id) {
 
        InternoResponse resultado = internoService.toggleActivo(id);
        // ✅ FIX: isActivo() — boolean primitivo
        String mensaje = resultado.isActivo()
                ? "Interno activado exitosamente"
                : "Interno desactivado exitosamente";
        return ResponseEntity.ok(ApiResponse.ok(mensaje, resultado));
    }

 
    // ─────────────────────────────────────────────────────────────────
    //  POST /api/v1/internos/{id}/regenerar-qr
    // ─────────────────────────────────────────────────────────────────
    @PostMapping("/{id}/regenerar-qr")
    public ResponseEntity<ApiResponse<Map<String, String>>> regenerarQr(@PathVariable Long id) {
        String codigoNuevo = internoService.regenerarCodigoQr(id);
        return ResponseEntity.ok(ApiResponse.ok(
                "QR regenerado exitosamente",
                Map.of("codigo", codigoNuevo)));
    }
 
    // ─────────────────────────────────────────────────────────────────
    //  GET /api/v1/internos/activos  — Lista simple para dropdowns
    // ─────────────────────────────────────────────────────────────────
    @GetMapping("/activos")
    public ResponseEntity<ApiResponse<List<InternoResponse>>> listarActivos() {
        return ResponseEntity.ok(ApiResponse.ok(internoService.findAllActivos()));
    }
 
    // ─────────────────────────────────────────────────────────────────
    //  GET /api/v1/internos/sin-responsable  — KPI para dashboard
    // ─────────────────────────────────────────────────────────────────
    @GetMapping("/sin-responsable")
    public ResponseEntity<ApiResponse<List<InternoResumenResponse>>> sinResponsable() {
        return ResponseEntity.ok(ApiResponse.ok(internoService.findSinResponsableActivo()));
    }

 
    // ─────────────────────────────────────────────────────────────────
    //  GET /api/v1/internos/carreras  — Valores únicos para filtro
    // ─────────────────────────────────────────────────────────────────
    @GetMapping("/carreras")
    public ResponseEntity<ApiResponse<List<String>>> getCarreras() {
        return ResponseEntity.ok(ApiResponse.ok(internoService.getDistinctCarreras()));
    }
 
    // ─────────────────────────────────────────────────────────────────
    //  GET /api/v1/internos/universidades
    // ─────────────────────────────────────────────────────────────────
    @GetMapping("/universidades")
    public ResponseEntity<ApiResponse<List<String>>> getUniversidades() {
        return ResponseEntity.ok(ApiResponse.ok(internoService.getDistinctUniversidades()));
    }
}