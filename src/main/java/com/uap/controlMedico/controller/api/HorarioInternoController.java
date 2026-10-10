package com.uap.controlMedico.controller.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uap.controlMedico.model.dto.horarioInternoDto.HorarioInternoDto;
import com.uap.controlMedico.model.dto.horarioInternoDto.HorarioInternoRequest;
import com.uap.controlMedico.model.dto.response.ApiResponse;
import com.uap.controlMedico.model.service.horarioInterno.HorarioInternoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/horario-interno")
@RequiredArgsConstructor
public class HorarioInternoController {

    private final HorarioInternoService horarioInternoService;

    @PostMapping
    public ResponseEntity<ApiResponse<HorarioInternoDto>> guardar(
        @Valid @RequestBody HorarioInternoRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(horarioInternoService.guardar(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<HorarioInternoDto>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(horarioInternoService.findById(id)));
    }

    @GetMapping("/jornada/{idJornada}")
    public ResponseEntity<ApiResponse<List<HorarioInternoDto>>> findByJornada(@PathVariable Long idJornada) {
        return ResponseEntity.ok(ApiResponse.ok(horarioInternoService.findByJornada(idJornada)));
    }

    @GetMapping("/interno-jornada/{idInternoJornada}")
    public ResponseEntity<ApiResponse<List<HorarioInternoDto>>> findByInternoJornada(
        @PathVariable Long idInternoJornada
    ) {
        return ResponseEntity.ok(ApiResponse.ok(horarioInternoService.findByInternoJornada(idInternoJornada)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminar(@PathVariable Long id) {
        horarioInternoService.eliminar(id);
        return ResponseEntity.ok(ApiResponse.ok("Horario de interno desactivado"));
    }
}
