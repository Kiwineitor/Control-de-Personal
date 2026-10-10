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

import com.uap.controlMedico.model.dto.monitoreo.PersonalEnTurnoDto;
import com.uap.controlMedico.model.dto.response.ApiResponse;
import com.uap.controlMedico.model.dto.rolturnoDto.RolTurnoDto;
import com.uap.controlMedico.model.dto.rolturnoDto.RolTurnoRequest;
import com.uap.controlMedico.model.service.rolturno.RolTurnoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/rol-turno")
@RequiredArgsConstructor
public class RolTurnoController {

    private final RolTurnoService rolTurnoService;

    @PostMapping
    public ResponseEntity<ApiResponse<RolTurnoDto>> guardar(
        @Valid @RequestBody RolTurnoRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(rolTurnoService.guardar(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RolTurnoDto>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(rolTurnoService.findById(id)));
    }

    @GetMapping("/jornada/{idJornada}")
    public ResponseEntity<ApiResponse<List<RolTurnoDto>>> findByJornada(@PathVariable Long idJornada) {
        return ResponseEntity.ok(ApiResponse.ok(rolTurnoService.findByJornada(idJornada)));
    }

    @GetMapping("/titular-jornada/{idTitularJornada}")
    public ResponseEntity<ApiResponse<List<RolTurnoDto>>> findByTitularJornada(
        @PathVariable Long idTitularJornada
    ) {
        return ResponseEntity.ok(ApiResponse.ok(rolTurnoService.findByTitularJornada(idTitularJornada)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminar(@PathVariable Long id) {
        rolTurnoService.eliminar(id);
        return ResponseEntity.ok(ApiResponse.ok("ROL TURNO desactivado"));
    }

    @GetMapping("/en-turno-ahora")
    public ResponseEntity<ApiResponse<List<PersonalEnTurnoDto>>> enTurnoAhora() {
        return ResponseEntity.ok(ApiResponse.ok(rolTurnoService.getPersonalEnTurnoAhora()));
    }
}
