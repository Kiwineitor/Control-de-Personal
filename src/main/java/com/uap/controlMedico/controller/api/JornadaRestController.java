package com.uap.controlMedico.controller.api;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.uap.controlMedico.model.dto.internosDto.InternoJornadaDto;
import com.uap.controlMedico.model.dto.jornadaDto.AsignarInternoRequest;
import com.uap.controlMedico.model.dto.jornadaDto.AsignarTitularRequest;
import com.uap.controlMedico.model.dto.jornadaDto.CambiarEstadoAsignacionRequest;
import com.uap.controlMedico.model.dto.jornadaDto.CambiarMonitoreoRequest;
import com.uap.controlMedico.model.dto.jornadaDto.HorarioIndividual;
import com.uap.controlMedico.model.dto.jornadaDto.InternoSelectDto;
import com.uap.controlMedico.model.dto.jornadaDto.JornadaDto;
import com.uap.controlMedico.model.dto.jornadaDto.JornadaRequest;
import com.uap.controlMedico.model.dto.jornadaDto.JornadaTitularDto;
import com.uap.controlMedico.model.dto.jornadaDto.LugarSelectDto;
import com.uap.controlMedico.model.dto.jornadaDto.TitularSelectDto;
import com.uap.controlMedico.model.dto.response.ApiResponse;
import com.uap.controlMedico.model.dto.titularDto.TitularJornadaDto;
import com.uap.controlMedico.model.enums.EstadoJornada;
import com.uap.controlMedico.model.enums.TipoJornada;
import com.uap.controlMedico.model.service.jornada.JornadaService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/jornadas")
@RequiredArgsConstructor
public class JornadaRestController {
    
    private final JornadaService jornadaService;
 
    @GetMapping
    public ResponseEntity<ApiResponse<Page<JornadaDto>>> listar(
        @RequestParam(defaultValue = "") String q,
        @RequestParam(required = false) EstadoJornada estado,
        @RequestParam(required = false) TipoJornada tipo,
        @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaIni,
        @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
        @PageableDefault(size = 20, sort = "fechaInicio", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            jornadaService.listarConFiltros(q, estado, tipo, fechaIni, fechaFin, pageable)
        ));
    }

    @GetMapping("/activas")
    public ResponseEntity<ApiResponse<List<JornadaDto>>> listarActivas() {
        return ResponseEntity.ok(ApiResponse.ok(jornadaService.listarActivas()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<JornadaDto>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(jornadaService.findById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<JornadaDto>> crear(
        @Valid @RequestBody JornadaRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(jornadaService.crear(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<JornadaDto>> actualizar(
        @PathVariable Long id,
        @Valid @RequestBody JornadaRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(jornadaService.actualizar(id, request)));
    }

    @PostMapping("/{id}/cerrar")
    public ResponseEntity<ApiResponse<JornadaDto>> cerrar(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(jornadaService.cerrar(id)));
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<ApiResponse<JornadaDto>> cancelar(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(jornadaService.cancelar(id)));
    }

    /**
     * Detiene o reanuda el control GPS del turno. No cambia su horario ni su
     * gente: mientras está detenido no se abren sesiones ni se registran atrasos,
     * faltas o abandonos, y nadie recibe avisos de ubicación.
     */
    @PutMapping("/{id}/monitoreo")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ApiResponse<JornadaDto>> cambiarMonitoreo(
        @PathVariable Long id,
        @Valid @RequestBody CambiarMonitoreoRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            jornadaService.cambiarMonitoreo(id, request.isPausado())
        ));
    }

    @GetMapping("/{id}/titulares")
    public ResponseEntity<ApiResponse<List<TitularJornadaDto>>> listarTitulares(
        @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.ok(jornadaService.listarTitulares(id)));
    }

    @PostMapping("/{id}/titulares")
    public ResponseEntity<ApiResponse<List<TitularJornadaDto>>> asignarTitulares(
        @PathVariable Long id,
        @Valid @RequestBody AsignarTitularRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(jornadaService.asignarTitulares(id, request)));
    }

    @PostMapping("/{id}/titulares/{idTJ}/estado")
    public ResponseEntity<ApiResponse<TitularJornadaDto>> cambiarEstadoTitular(
        @PathVariable Long id,
        @PathVariable Long idTJ,
        @Valid @RequestBody CambiarEstadoAsignacionRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            jornadaService.cambiarEstadoTitular(idTJ, request)
        ));
    }

    /**
     * Cambia el horario individual de un titular ya asignado (incluye las dos
     * franjas si la jornada es partida). Antes había que quitarlo y reasignarlo.
     */
    @PutMapping("/{id}/titulares/{idTJ}/horario")
    public ResponseEntity<ApiResponse<TitularJornadaDto>> actualizarHorarioTitular(
        @PathVariable Long id,
        @PathVariable Long idTJ,
        @RequestBody HorarioIndividual horario
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            jornadaService.actualizarHorarioTitular(idTJ, horario)
        ));
    }

    @DeleteMapping("/{id}/titulares/{idTJ}")
    public ResponseEntity<ApiResponse<Void>> removerTitular(
        @PathVariable Long id,
        @PathVariable Long idTJ
    ) {
        jornadaService.removerTitular(idTJ);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/{id}/internos")
    public ResponseEntity<ApiResponse<List<InternoJornadaDto>>> listarInternos(
        @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.ok(jornadaService.listarInternos(id)));
    }

    @PostMapping("/{id}/internos")
    public ResponseEntity<ApiResponse<List<InternoJornadaDto>>> asignarInternos(
        @PathVariable Long id,
        @Valid @RequestBody AsignarInternoRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(jornadaService.asignarInternos(id, request)));
    }

    @PostMapping("/{id}/internos/{idIJ}/estado")
    public ResponseEntity<ApiResponse<InternoJornadaDto>> cambiarEstadoInterno(
        @PathVariable Long id,
        @PathVariable Long idIJ,
        @Valid @RequestBody CambiarEstadoAsignacionRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            jornadaService.cambiarEstadoInterno(idIJ, request)
        ));
    }

    @DeleteMapping("/{id}/internos/{idIJ}")
    public ResponseEntity<ApiResponse<Void>> removerInterno(
        @PathVariable Long id,
        @PathVariable Long idIJ
    ) {
        jornadaService.removerInterno(idIJ);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/por-titular/{idTitular}")
    public ResponseEntity<ApiResponse<List<JornadaTitularDto>>> getJornadasPorTitular(
        @PathVariable Long idTitular
    ) {
        return ResponseEntity.ok(ApiResponse.ok(jornadaService.getJornadasPorTitular(idTitular)));
    }

    @GetMapping("/disponibles/titulares")
    public ResponseEntity<ApiResponse<List<TitularSelectDto>>> titularesDisponibles() {
        return ResponseEntity.ok(ApiResponse.ok(jornadaService.getTitularesDisponibles()));
    }

    @GetMapping("/disponibles/internos")
    public ResponseEntity<ApiResponse<List<InternoSelectDto>>> internosDisponibles() {
        return ResponseEntity.ok(ApiResponse.ok(jornadaService.getInternosDisponibles()));
    }

    @GetMapping("/disponibles/lugares")
    public ResponseEntity<ApiResponse<List<LugarSelectDto>>> lugaresDisponibles() {
        return ResponseEntity.ok(ApiResponse.ok(jornadaService.getLugaresDisponibles()));
    }
}