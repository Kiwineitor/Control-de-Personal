package com.uap.controlMedico.controller.api;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.uap.controlMedico.model.dto.personalDto.PersonalHospitalDto;
import com.uap.controlMedico.model.dto.personalDto.RegistrarPersonalRequest;
import com.uap.controlMedico.model.dto.personalDto.TipoPersonalDto;
import com.uap.controlMedico.model.dto.personalDto.TipoPersonalRequest;
import com.uap.controlMedico.model.dto.response.ApiResponse;
import com.uap.controlMedico.model.service.personal.PersonalService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/personal")
@RequiredArgsConstructor
public class PersonalController {

    private final PersonalService personalService;

    // ── TipoPersonal ──────────────────────────────────────────────────

    @GetMapping("/tipos")
    public ResponseEntity<ApiResponse<List<TipoPersonalDto>>> listarTipos() {
        return ResponseEntity.ok(ApiResponse.ok(personalService.listarTipos()));
    }

    @GetMapping("/tipos/{id}")
    public ResponseEntity<ApiResponse<TipoPersonalDto>> findTipo(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(personalService.findTipoById(id)));
    }

    @PostMapping("/tipos")
    public ResponseEntity<ApiResponse<TipoPersonalDto>> crearTipo(
        @Valid @RequestBody TipoPersonalRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(personalService.crearTipo(request)));
    }

    @PutMapping("/tipos/{id}")
    public ResponseEntity<ApiResponse<TipoPersonalDto>> actualizarTipo(
        @PathVariable Long id,
        @Valid @RequestBody TipoPersonalRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(personalService.actualizarTipo(id, request)));
    }

    @PostMapping("/tipos/{id}/toggle")
    public ResponseEntity<ApiResponse<Void>> toggleTipo(@PathVariable Long id) {
        personalService.toggleTipo(id);
        return ResponseEntity.ok(ApiResponse.ok("Estado actualizado"));
    }

    // ── Personal (Titular + Persona) ─────────────────────────────────

    @GetMapping
    public ResponseEntity<ApiResponse<Page<PersonalHospitalDto>>> listarPersonal(
        @RequestParam(defaultValue = "")    String  q,
        @RequestParam(required = false)     Long    idTipoPersonal,
        @RequestParam(required = false)     Boolean activo,
        @PageableDefault(size = 20, sort = "persona.apellido", direction = Sort.Direction.ASC)
            Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            personalService.listarPersonal(q, idTipoPersonal, activo, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PersonalHospitalDto>> findPersonal(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(personalService.findPersonalById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PersonalHospitalDto>> registrar(
        @Valid @RequestBody RegistrarPersonalRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(personalService.registrar(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PersonalHospitalDto>> actualizar(
        @PathVariable Long id,
        @Valid @RequestBody RegistrarPersonalRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(personalService.actualizar(id, request)));
    }

    @PostMapping("/{id}/toggle")
    public ResponseEntity<ApiResponse<Void>> togglePersonal(@PathVariable Long id) {
        personalService.togglePersonal(id);
        return ResponseEntity.ok(ApiResponse.ok("Estado actualizado"));
    }
}
