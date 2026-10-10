package com.uap.controlMedico.controller.api;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.uap.controlMedico.model.dao.PersonaDao;
import com.uap.controlMedico.model.dao.UsuarioDao;
import com.uap.controlMedico.model.dto.response.ApiResponse;
import com.uap.controlMedico.model.dto.usuarioDto.PersonaParaUsuarioResponse;
import com.uap.controlMedico.model.entity.Persona;
import com.uap.controlMedico.model.entity.Usuario;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/personas")
@RequiredArgsConstructor
public class PersonaApiController {

    private final PersonaDao personaDao;
    private final UsuarioDao usuarioDao;

    /**
     * Retorna todas las personas con el estado de su usuario (si tienen o no,
     * y si está activo). El frontend usa esto para:
     * - Mostrar el select con badges de estado.
     * - Deshabilitar personas que ya tienen usuario en modo creación.
     */
    @GetMapping("/para-usuario")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<PersonaParaUsuarioResponse>>> getParaUsuario() {
        List<Object[]> rows = personaDao.findAllConUsuario();

        List<PersonaParaUsuarioResponse> result = rows.stream().map(row -> {
            Persona p = (Persona) row[0];
            Usuario u = (Usuario) row[1];
            return PersonaParaUsuarioResponse.builder()
                    .idPersona(p.getIdPersona())
                    .nombre(p.getNombre())
                    .apellido(p.getApellido())
                    .nombreCompleto(p.getNombreCompleto())
                    .ci(p.getCi())
                    .email(p.getEmail())
                    .telefono(p.getTelefono())
                    .tieneUsuario(u != null)
                    .usuarioActivo(u != null ? u.isActivo() : null)
                    .build();
        }).toList();

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * Búsqueda libre de personas para usarla en los formularios de
     * Titular e Interno (selector "buscar persona existente").
     * Accesible a ADMIN y SUPERVISOR.
     */
    @GetMapping("/buscar")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ApiResponse<List<PersonaParaUsuarioResponse>>> buscar(
            @RequestParam(required = false, defaultValue = "") String q) {

        String qN = q.toLowerCase().trim();
        List<Persona> personas = personaDao.buscarPorTermino(qN, PageRequest.of(0, 50));

        List<PersonaParaUsuarioResponse> result = personas.stream().map(p ->
            PersonaParaUsuarioResponse.builder()
                .idPersona(p.getIdPersona())
                .nombre(p.getNombre())
                .apellido(p.getApellido())
                .nombreCompleto(p.getNombreCompleto())
                .ci(p.getCi())
                .email(p.getEmail())
                .telefono(p.getTelefono())
                .build()
        ).toList();

        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
