package com.uap.controlMedico.controller.api;

import java.io.IOException;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.uap.controlMedico.model.dto.actividadDto.ActividadDto;
import com.uap.controlMedico.model.dto.actividadDto.ActividadJornadaDto;
import com.uap.controlMedico.model.dto.actividadDto.ActividadJornadaResponse;
import com.uap.controlMedico.model.dto.actividadDto.CambiarEstadoActividadRequest;
import com.uap.controlMedico.model.dto.actividadDto.CulminarActividadRequest;
import com.uap.controlMedico.model.dto.actividadDto.EstadisticasActividadesDto;
import com.uap.controlMedico.model.dto.actividadDto.ResumenActividadesDto;
import com.uap.controlMedico.model.dto.actividadDto.SubirEvidenciaResponse;
import com.uap.controlMedico.model.dto.actividadDto.ValidarActividadRequest;
import com.uap.controlMedico.model.dto.catalagoDto.ActividadRequest;
import com.uap.controlMedico.model.dto.jornadaDto.ActividadJornadaRequest;
import com.uap.controlMedico.model.dto.response.ApiResponse;
import com.uap.controlMedico.model.enums.EstadoCatalogo;
import com.uap.controlMedico.model.enums.TipoActividad;
import com.uap.controlMedico.model.service.actividad.ActividadJornadaV2Service;
import com.uap.controlMedico.model.service.actividad.ActividadService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/actividades")
@RequiredArgsConstructor
public class ActividadRestController {
    
    private final ActividadService actividadService;
    private final ActividadJornadaV2Service actividadJornadaV2Service;

    // ── Catálogo ──────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ActividadDto>>> listar(
        @RequestParam(defaultValue = "")  String q,
        @RequestParam(required = false)   TipoActividad tipo,
        @RequestParam(required = false)   EstadoCatalogo estado,
        @PageableDefault(size = 20, sort = "nombre", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            actividadService.listarConFiltros(q, tipo, estado, pageable)
        ));
    }

    /** Lista simplificada para NSelect en formularios. */
    @GetMapping("/catalogo")
    public ResponseEntity<ApiResponse<List<ActividadDto>>> catalogo() {
        return ResponseEntity.ok(ApiResponse.ok(actividadService.getCatalogoActivo()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ActividadDto>> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(actividadService.buscarPorId(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ActividadDto>> crear(
        @Valid @RequestBody ActividadRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(actividadService.crear(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ActividadDto>> actualizar(
        @PathVariable Long id,
        @Valid @RequestBody ActividadRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(actividadService.actualizar(id, request)));
    }

    // @PostMapping (no @PatchMapping — decisión #3)
    @PostMapping("/{id}/desactivar")
    public ResponseEntity<ApiResponse<Void>> desactivar(@PathVariable Long id) {
        actividadService.desactivar(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ── Asignaciones ──────────────────────────────────────────────────────

    @PostMapping("/asignar")
    public ResponseEntity<ApiResponse<ActividadJornadaDto>> asignar(
        @Valid @RequestBody ActividadJornadaRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(actividadService.asignar(request)));
    }

    @PostMapping("/{id}/estado")
    public ResponseEntity<ApiResponse<ActividadJornadaDto>> cambiarEstado(
        @PathVariable Long id,
        @Valid @RequestBody CambiarEstadoActividadRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(actividadService.cambiarEstado(id, request)));
    }

    @PostMapping("/{id}/validar")
    public ResponseEntity<ApiResponse<ActividadJornadaDto>> validar(
        @PathVariable Long id,
        @Valid @RequestBody ValidarActividadRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(actividadService.validar(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> remover(@PathVariable Long id) {
        actividadService.remover(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ── Consultas ─────────────────────────────────────────────────────────

    @GetMapping("/por-jornada/{idJornada}")
    public ResponseEntity<ApiResponse<List<ActividadJornadaDto>>> porJornada(
        @PathVariable Long idJornada
    ) {
        return ResponseEntity.ok(ApiResponse.ok(actividadService.findByJornada(idJornada)));
    }

    @GetMapping("/por-interno-jornada/{idInternoJornada}")
    public ResponseEntity<ApiResponse<List<ActividadJornadaDto>>> porInternoJornada(
        @PathVariable Long idInternoJornada
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            actividadService.findByInternoJornada(idInternoJornada)));
    }

    @GetMapping("/por-titular-jornada/{idTitularJornada}")
    public ResponseEntity<ApiResponse<List<ActividadJornadaDto>>> porTitularJornada(
        @PathVariable Long idTitularJornada
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            actividadService.findByTitularJornada(idTitularJornada)));
    }

    @GetMapping("/pendientes-interno/{idInterno}")
    public ResponseEntity<ApiResponse<List<ActividadJornadaDto>>> pendientesInterno(
        @PathVariable Long idInterno
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            actividadService.findPendientesInterno(idInterno)));
    }

    @GetMapping("/historial-interno/{idInterno}")
    public ResponseEntity<ApiResponse<List<ActividadJornadaDto>>> historialInterno(
        @PathVariable Long idInterno
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            actividadService.findHistorialInterno(idInterno)));
    }

    @GetMapping("/historial-titular/{idTitular}")
    public ResponseEntity<ApiResponse<List<ActividadJornadaDto>>> historialTitular(
        @PathVariable Long idTitular
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            actividadService.findHistorialTitular(idTitular)));
    }

    // ── Reportes ──────────────────────────────────────────────────────────

    @GetMapping("/resumen-jornada/{idJornada}")
    public ResponseEntity<ApiResponse<ResumenActividadesDto>> resumenJornada(
        @PathVariable Long idJornada
    ) {
        return ResponseEntity.ok(ApiResponse.ok(actividadService.resumenJornada(idJornada)));
    }

    @GetMapping("/estadisticas")
    public ResponseEntity<ApiResponse<EstadisticasActividadesDto>> estadisticas() {
        return ResponseEntity.ok(ApiResponse.ok(actividadService.estadisticasGlobales()));
    }


    // Lista asignaciones de una jornada con filtro opcional
    @GetMapping("/asignacion/jornada/{idJornada}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','MEDICO','ENFERMERO','CAJERO','INTERNO')")
    public ResponseEntity<ApiResponse<Page<ActividadJornadaResponse>>> listarPorJornada(
        @PathVariable Long idJornada,
        @RequestParam(required = false) Boolean culminada,
        @PageableDefault(size = 20, sort = "fechaCreacion",
            direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            actividadJornadaV2Service.listarPorJornada(idJornada, culminada, pageable)));
    }

    // Detalle de una asignación
    @GetMapping("/asignacion/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','MEDICO','ENFERMERO','CAJERO','INTERNO')")
    public ResponseEntity<ApiResponse<ActividadJornadaResponse>> findById(
        @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.ok(actividadJornadaV2Service.findById(id)));
    }

    // Subir (o reemplazar) evidencia — regla #3: @PostMapping para updates
    @PostMapping("/asignacion/{id}/subir-evidencia")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','MEDICO','ENFERMERO','CAJERO','INTERNO')")
    public ResponseEntity<ApiResponse<SubirEvidenciaResponse>> subirEvidencia(
        @PathVariable Long id,
        @RequestParam("file") MultipartFile file
    ) throws IOException {
        return ResponseEntity.ok(ApiResponse.ok(
            actividadJornadaV2Service.subirEvidencia(id, file)));
    }

    // Marcar actividad como culminada
    @PostMapping("/asignacion/{id}/culminar")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','MEDICO','ENFERMERO','CAJERO','INTERNO')")
    public ResponseEntity<ApiResponse<ActividadJornadaResponse>> culminar(
        @PathVariable Long id,
        @Valid @RequestBody CulminarActividadRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            actividadJornadaV2Service.culminarActividad(id, request)));
    }
}
