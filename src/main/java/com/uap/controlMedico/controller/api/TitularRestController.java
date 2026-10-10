package com.uap.controlMedico.controller.api;

import java.time.LocalDate;
import java.util.List;

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

import com.uap.controlMedico.model.dto.response.ApiResponse;
import com.uap.controlMedico.model.dto.titularDto.TitularRequest;
import com.uap.controlMedico.model.dto.titularDto.TitularResponse;
import com.uap.controlMedico.model.dto.titularDto.TitularResumenResponse;
import com.uap.controlMedico.model.service.titulares.TitularService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/titulares")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
public class TitularRestController {
    
        private final TitularService titularService;
 
    // ─────────────────────────────────────────────────────────────────
    //  GET /api/v1/titulares
    //  Listado paginado con filtros opcionales
    //  Params: page, size, q (búsqueda libre), cargo, especialidad, activo
    // ─────────────────────────────────────────────────────────────────
    @GetMapping
    public ResponseEntity<ApiResponse<Page<TitularResponse>>> listar(
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "20")  int size,
            @RequestParam(required = false)     String q,
            @RequestParam(required = false)     String cargo,
            @RequestParam(required = false)     String especialidad,
            @RequestParam(required = false)     Boolean activo) {
 
        PageRequest pageable = PageRequest.of(page, size,
            Sort.by("persona.apellido").ascending());

        Page<TitularResponse> resultado =
                titularService.listarConFiltros(q, cargo, especialidad, activo, pageable);
 
        return ResponseEntity.ok(ApiResponse.ok(resultado));
    }
 
    // ─────────────────────────────────────────────────────────────────
    //  GET /api/v1/titulares/{id}
    // ─────────────────────────────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TitularResponse>> buscarPorId(
            @PathVariable Long id) {
 
        return ResponseEntity.ok(ApiResponse.ok(titularService.buscarPorId(id)));
    }
 
    // ─────────────────────────────────────────────────────────────────
    //  POST /api/v1/titulares  — Crear
    // ─────────────────────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<ApiResponse<TitularResponse>> crear(
            @Valid @RequestBody TitularRequest request) {
 
        TitularResponse creado = titularService.crear(request);
        // ✅ FIX: ok(mensaje, datos) en lugar de success(datos, mensaje)
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Titular registrado exitosamente", creado));
    }


    // ─────────────────────────────────────────────────────────────────
    //  PUT /api/v1/titulares/{id}  — Actualizar
    // ─────────────────────────────────────────────────────────────────
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TitularResponse>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody TitularRequest request) {
 
        TitularResponse actualizado = titularService.actualizar(id, request);
        // ✅ FIX
        return ResponseEntity.ok(ApiResponse.ok("Titular actualizado exitosamente", actualizado));
    }
 
    // ─────────────────────────────────────────────────────────────────
    //  PATCH /api/v1/titulares/{id}/toggle  — Activar / Desactivar
    // ─────────────────────────────────────────────────────────────────
    @PostMapping("/{id}/toggle")
    public ResponseEntity<ApiResponse<TitularResponse>> toggleActivo(@PathVariable Long id) {
 
        TitularResponse resultado = titularService.toggleActivo(id);
        // ✅ FIX: isActivo() en lugar de getActivo() (boolean primitivo en entidad)
        String mensaje = resultado.isActivo()
                ? "Titular activado exitosamente"
                : "Titular desactivado exitosamente";
        return ResponseEntity.ok(ApiResponse.ok(mensaje, resultado));
    }
 
    // ─────────────────────────────────────────────────────────────────
    //  POST /api/v1/titulares/{id}/sincronizar  — Sync con Londra
    // ─────────────────────────────────────────────────────────────────
    @PostMapping("/{id}/sincronizar")
    public ResponseEntity<ApiResponse<TitularResponse>> sincronizar(@PathVariable Long id) {
        TitularResponse sincronizado = titularService.sincronizarDesdeLondra(id);
        // ✅ FIX
        return ResponseEntity.ok(ApiResponse.ok("Datos sincronizados desde Londra", sincronizado));
    }

    // ─────────────────────────────────────────────────────────────────
    //  GET /api/v1/titulares/activos  — Lista simple para dropdowns
    // ─────────────────────────────────────────────────────────────────
    @GetMapping("/activos")
    public ResponseEntity<ApiResponse<List<TitularResponse>>> listarActivos() {
        return ResponseEntity.ok(ApiResponse.ok(titularService.findAllActivos()));
    }
 
    // ─────────────────────────────────────────────────────────────────
    //  GET /api/v1/titulares/cargos  — Valores únicos para filtro
    // ─────────────────────────────────────────────────────────────────
    @GetMapping("/cargos")
    public ResponseEntity<ApiResponse<List<String>>> getCargos() {
        return ResponseEntity.ok(ApiResponse.ok(titularService.getDistinctCargos()));
    }
 
    // ─────────────────────────────────────────────────────────────────
    //  GET /api/v1/titulares/especialidades
    // ─────────────────────────────────────────────────────────────────
    @GetMapping("/especialidades")
    public ResponseEntity<ApiResponse<List<String>>> getEspecialidades() {
        return ResponseEntity.ok(ApiResponse.ok(titularService.getDistinctEspecialidades()));
    }
}