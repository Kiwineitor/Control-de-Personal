package com.uap.controlMedico.controller.api;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Pageable;
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

import com.uap.controlMedico.model.dto.catalagoDto.LugarRequest;
import com.uap.controlMedico.model.dto.catalagoDto.LugarResponse;
import com.uap.controlMedico.model.dto.response.ApiResponse;
import com.uap.controlMedico.model.enums.TipoLugar;
import com.uap.controlMedico.model.service.LugarService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/lugares")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
public class LugarRestController {
    
    private final LugarService lugarService;

    @GetMapping("/paginado")
    public ResponseEntity<ApiResponse<Page<LugarResponse>>> paginado(
            @RequestParam(required = false) String  nombre,
            @RequestParam(required = false) Boolean activo,
            @PageableDefault(size = 20, sort = "nombre",
                             direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(
            ApiResponse.ok(lugarService.getAll(nombre, activo, pageable)));
    }
 
    @GetMapping
    public ResponseEntity<ApiResponse<List<LugarResponse>>> listar() {
        return ResponseEntity.ok(ApiResponse.ok(lugarService.listarActivos()));
    }
 
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LugarResponse>> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(lugarService.buscarPorId(id)));
    }
 
    @GetMapping("/por-tipo")
    public ResponseEntity<ApiResponse<List<LugarResponse>>> porTipo(
            @RequestParam TipoLugar tipo) {
        return ResponseEntity.ok(ApiResponse.ok(lugarService.listarPorTipo(tipo)));
    }
 
    /** GET /api/v1/lugares/disponibles?fecha=2024-05-10 */
    @GetMapping("/disponibles")
    public ResponseEntity<ApiResponse<List<LugarResponse>>> disponibles(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(
            ApiResponse.ok(lugarService.findDisponiblesEnFecha(
                fecha != null ? fecha : LocalDate.now())));
    }

    @GetMapping("/stats/geofencing")
    public ResponseEntity<ApiResponse<Long>> statsGeofencing() {
        return ResponseEntity.ok(ApiResponse.ok(lugarService.countConGeofencing()));
    }
 
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")     // ← FIX: quitar ROLE_
    public ResponseEntity<ApiResponse<LugarResponse>> crear(
            @Valid @RequestBody LugarRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Lugar registrado exitosamente",
                    lugarService.crear(request)));
    }
 
    @PostMapping("/{id}/editar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<LugarResponse>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody LugarRequest request) {
        return ResponseEntity.ok(
            ApiResponse.ok("Lugar actualizado", lugarService.actualizar(id, request)));
    }
 
    @PostMapping("/{id}/desactivar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> desactivar(@PathVariable Long id) {
        lugarService.desactivar(id);
        return ResponseEntity.ok(ApiResponse.ok("Lugar desactivado"));
    }

    @PostMapping("/{id}/activar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> activar(@PathVariable Long id) {
        lugarService.activar(id);
        return ResponseEntity.ok(ApiResponse.ok("Lugar activado"));
    }

}
