package com.uap.controlMedico.controller.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uap.controlMedico.model.dto.permanenciaDto.RondaVerificacionRequest;
import com.uap.controlMedico.model.dto.permanenciaDto.RondaVerificacionResponse;
import com.uap.controlMedico.model.dto.permanenciaDto.VerificacionPresenciaRequest;
import com.uap.controlMedico.model.dto.response.ApiResponse;
import com.uap.controlMedico.model.service.permanencia.PermanenciaService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/permanencia")
@RequiredArgsConstructor
public class PermanenciaRestController {
    
        private final PermanenciaService permanenciaService;
 
    /** GET /api/v1/permanencia/rondas/hoy */
    @GetMapping("/rondas/hoy")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ApiResponse<List<RondaVerificacionResponse>>> rondasHoy() {
        return ResponseEntity.ok(ApiResponse.ok(permanenciaService.findRondasHoy()));
    }
 
    /** GET /api/v1/permanencia/rondas/en-curso */
    @GetMapping("/rondas/en-curso")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ApiResponse<List<RondaVerificacionResponse>>> rondasEnCurso() {
        return ResponseEntity.ok(ApiResponse.ok(permanenciaService.findEnCurso()));
    }
 
    /** GET /api/v1/permanencia/rondas/{id} */
    @GetMapping("/rondas/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ApiResponse<RondaVerificacionResponse>> buscarRonda(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(permanenciaService.buscarRonda(id)));
    }
 
    /**
     * POST /api/v1/permanencia/rondas
     * Lanza una ronda manual de verificación de presencia.
     */
    @PostMapping("/rondas")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ApiResponse<RondaVerificacionResponse>> crearRonda(
            @Valid @RequestBody RondaVerificacionRequest request) {
        RondaVerificacionResponse ronda = permanenciaService.crearRonda(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Ronda de verificación iniciada", ronda));
    }
 
    /**
     * POST /api/v1/permanencia/verificaciones/responder
     * El personal responde a su verificación de presencia.
     * Puede ser llamado desde la app o notificación.
     */
    @PostMapping("/verificaciones/responder")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<RondaVerificacionResponse>> responder(
            @Valid @RequestBody VerificacionPresenciaRequest request) {
        RondaVerificacionResponse ronda = permanenciaService.responderVerificacion(request);
        return ResponseEntity.ok(ApiResponse.ok("Verificación registrada", ronda));
    }
 
    /** PATCH /api/v1/permanencia/rondas/{id}/cerrar */
    @PatchMapping("/rondas/{id}/cerrar")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ApiResponse<Void>> cerrarRonda(@PathVariable Long id) {
        permanenciaService.cerrarRonda(id);
        return ResponseEntity.ok(ApiResponse.ok("Ronda finalizada"));
    }

}
