package com.uap.controlMedico.controller.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uap.controlMedico.model.dto.response.ApiResponse;
import com.uap.controlMedico.model.dto.turnoDto.HistorialCambioDto;
import com.uap.controlMedico.model.service.turno.HistorialCambioService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/historial")
@RequiredArgsConstructor
public class HistorialCambioController {

    private final HistorialCambioService historialService;

    @GetMapping("/{entidad}/{idEntidad}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ApiResponse<List<HistorialCambioDto>>> obtenerHistorial(
            @PathVariable String entidad,
            @PathVariable Long idEntidad) {

        List<HistorialCambioDto> dtos = historialService.obtenerHistorial(entidad, idEntidad)
            .stream()
            .map(h -> HistorialCambioDto.builder()
                .idHistorial(h.getIdHistorial())
                .entidad(h.getEntidad())
                .idEntidad(h.getIdEntidad())
                .campo(h.getCampo())
                .valorAnterior(h.getValorAnterior())
                .valorNuevo(h.getValorNuevo())
                .accion(h.getAccion())
                .usuario(h.getUsuario())
                .fechaCambio(h.getFechaCambio())
                .build())
            .toList();

        return ResponseEntity.ok(ApiResponse.ok(dtos));
    }
}
