package com.uap.controlMedico.controller.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uap.controlMedico.model.dto.responsableInterno.AsignarResponsableRequest;
import com.uap.controlMedico.model.dto.responsableInterno.ResponsableResponseDto;
import com.uap.controlMedico.model.dto.responsableInterno.ResponsableResumenDto;
import com.uap.controlMedico.model.dto.response.ApiResponse;
import com.uap.controlMedico.model.service.responsableInterno.ResponsableInternoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/responsables")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
public class ResponsableApiController {

    private final ResponsableInternoService responsableService;
 
    // ─────────────────────────────────────────────────────────────────
    //  GET /api/v1/responsables/activo
    //  Responsable actual con todos sus internos asignados
    // ─────────────────────────────────────────────────────────────────
    @GetMapping("/activo")
    public ResponseEntity<ApiResponse<ResponsableResumenDto>> getActivo() {
        ResponsableResumenDto activo = responsableService.findResponsableActual();
        if (activo == null) {
            return ResponseEntity.ok(ApiResponse.ok("Sin responsable asignado"));
        }
        return ResponseEntity.ok(ApiResponse.ok(activo));
    }
 
    // ─────────────────────────────────────────────────────────────────
    //  GET /api/v1/responsables/historial
    // ─────────────────────────────────────────────────────────────────
    @GetMapping("/historial")
    public ResponseEntity<ApiResponse<List<ResponsableResponseDto>>> getHistorial() {
        return ResponseEntity.ok(
                ApiResponse.ok(responsableService.findHistorial()));
    }
 
    // ─────────────────────────────────────────────────────────────────
    //  POST /api/v1/responsables/asignar
    //  Body: { idTitular, idInternos: [], asignarTodos: false,
    //          fechaFinAsignacion?, observaciones? }
    // ─────────────────────────────────────────────────────────────────
    @PostMapping("/asignar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<ResponsableResponseDto>>> asignar(
            @Valid @RequestBody AsignarResponsableRequest request) {
 
        List<ResponsableResponseDto> resultado = responsableService.asignar(request);
        String msg = "Responsable asignado a " + resultado.size() + " interno(s)";
        return ResponseEntity.ok(ApiResponse.ok(msg, resultado));
    }
 
    // ─────────────────────────────────────────────────────────────────
    //  DELETE /api/v1/responsables/{id}/desasignar
    //  Desactiva la asignación de un interno específico
    // ─────────────────────────────────────────────────────────────────
    @PostMapping("/{id}/desasignar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> desasignar(@PathVariable Long id) {
        responsableService.desasignar(id);
        return ResponseEntity.ok(ApiResponse.ok("Asignación desactivada"));
    }

}