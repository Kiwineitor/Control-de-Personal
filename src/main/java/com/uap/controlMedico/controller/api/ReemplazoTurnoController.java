package com.uap.controlMedico.controller.api;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uap.controlMedico.model.dao.JornadaDao;
import com.uap.controlMedico.model.dao.ReemplazoTurnoDao;
import com.uap.controlMedico.model.dao.TitularDao;
import com.uap.controlMedico.model.dao.UsuarioDao;
import com.uap.controlMedico.model.dto.response.ApiResponse;
import com.uap.controlMedico.model.dto.turnoDto.ReemplazoTurnoDto;
import com.uap.controlMedico.model.dto.turnoDto.ReemplazoTurnoRequest;
import com.uap.controlMedico.model.entity.Jornada;
import com.uap.controlMedico.model.entity.ReemplazoTurno;
import com.uap.controlMedico.model.entity.Titular;
import com.uap.controlMedico.model.entity.Usuario;
import com.uap.controlMedico.model.service.turno.HistorialCambioService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/reemplazos")
@RequiredArgsConstructor
public class ReemplazoTurnoController {

    private final ReemplazoTurnoDao reemplazoDao;
    private final JornadaDao jornadaDao;
    private final TitularDao titularDao;
    private final UsuarioDao usuarioDao;
    private final HistorialCambioService historialService;

    @GetMapping("/jornada/{idJornada}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ApiResponse<List<ReemplazoTurnoDto>>> listarPorJornada(
            @PathVariable Long idJornada) {
        List<ReemplazoTurnoDto> dtos = reemplazoDao.findConDetalleByJornada(idJornada)
            .stream()
            .map(this::toDto)
            .toList();
        return ResponseEntity.ok(ApiResponse.ok(dtos));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ApiResponse<ReemplazoTurnoDto>> crear(
            @Valid @RequestBody ReemplazoTurnoRequest request) {

        Jornada jornada = jornadaDao.findById(request.getIdJornada())
            .orElseThrow(() -> new IllegalArgumentException("Jornada no encontrada"));
        Titular original = titularDao.findById(request.getIdOriginal())
            .orElseThrow(() -> new IllegalArgumentException("Titular original no encontrado"));
        Titular reemplazante = titularDao.findById(request.getIdReemplazo())
            .orElseThrow(() -> new IllegalArgumentException("Reemplazante no encontrado"));

        if (reemplazoDao.existsByJornadaIdJornadaAndFechaReemplazoAndOriginalIdTitular(
                request.getIdJornada(), request.getFechaReemplazo(), request.getIdOriginal())) {
            throw new IllegalArgumentException("Ya existe un reemplazo para este titular en esta fecha");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Usuario usuario = usuarioDao.findByUsername(auth.getName()).orElse(null);

        ReemplazoTurno reemplazo = ReemplazoTurno.builder()
            .jornada(jornada)
            .fechaReemplazo(request.getFechaReemplazo())
            .original(original)
            .reemplazante(reemplazante)
            .motivo(request.getMotivo())
            .estado("PENDIENTE")
            .autorizadoPor(usuario)
            .build();

        reemplazo = reemplazoDao.save(reemplazo);

        historialService.registrarAccion("REEMPLAZO", reemplazo.getIdReemplazo(),
            "CREADO", "reemplazo",
            original.getPersona().getApellido() + " " + original.getPersona().getNombre(),
            reemplazante.getPersona().getApellido() + " " + reemplazante.getPersona().getNombre());

        return ResponseEntity.ok(ApiResponse.ok(toDto(reemplazo)));
    }

    @PutMapping("/{id}/aprobar")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ApiResponse<ReemplazoTurnoDto>> aprobar(@PathVariable Long id) {
        ReemplazoTurno r = reemplazoDao.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Reemplazo no encontrado"));
        if (!r.isPendiente()) {
            throw new IllegalArgumentException("Solo se pueden aprobar reemplazos pendientes");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Usuario usuario = usuarioDao.findByUsername(auth.getName()).orElse(null);

        r.setEstado("APROBADO");
        r.setAutorizadoPor(usuario);
        r.setFechaAutorizacion(LocalDateTime.now());
        r = reemplazoDao.save(r);

        historialService.registrarAccion("REEMPLAZO", r.getIdReemplazo(),
            "MODIFICADO", "estado", "PENDIENTE", "APROBADO");

        return ResponseEntity.ok(ApiResponse.ok(toDto(r)));
    }

    @PutMapping("/{id}/rechazar")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ApiResponse<ReemplazoTurnoDto>> rechazar(@PathVariable Long id) {
        ReemplazoTurno r = reemplazoDao.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Reemplazo no encontrado"));
        if (!r.isPendiente()) {
            throw new IllegalArgumentException("Solo se pueden rechazar reemplazos pendientes");
        }
        r.setEstado("RECHAZADO");
        r = reemplazoDao.save(r);

        historialService.registrarAccion("REEMPLAZO", r.getIdReemplazo(),
            "MODIFICADO", "estado", "PENDIENTE", "RECHAZADO");

        return ResponseEntity.ok(ApiResponse.ok(toDto(r)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ApiResponse<Void>> eliminar(@PathVariable Long id) {
        ReemplazoTurno r = reemplazoDao.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Reemplazo no encontrado"));
        if (!r.isPendiente()) {
            throw new IllegalArgumentException("Solo se pueden eliminar reemplazos pendientes");
        }
        reemplazoDao.delete(r);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    private ReemplazoTurnoDto toDto(ReemplazoTurno r) {
        String nombreOriginal = r.getOriginal() != null && r.getOriginal().getPersona() != null
            ? r.getOriginal().getPersona().getApellido() + " " + r.getOriginal().getPersona().getNombre()
            : "—";
        String nombreReemplazo = r.getReemplazante() != null && r.getReemplazante().getPersona() != null
            ? r.getReemplazante().getPersona().getApellido() + " " + r.getReemplazante().getPersona().getNombre()
            : "—";
        String nombreAutorizador = r.getAutorizadoPor() != null
            ? r.getAutorizadoPor().getUsername() : null;

        return ReemplazoTurnoDto.builder()
            .idReemplazo(r.getIdReemplazo())
            .idJornada(r.getJornada() != null ? r.getJornada().getIdJornada() : null)
            .fechaReemplazo(r.getFechaReemplazo())
            .idOriginal(r.getOriginal() != null ? r.getOriginal().getIdTitular() : null)
            .nombreOriginal(nombreOriginal)
            .idReemplazante(r.getReemplazante() != null ? r.getReemplazante().getIdTitular() : null)
            .nombreReemplazo(nombreReemplazo)
            .motivo(r.getMotivo())
            .estado(r.getEstado())
            .autorizadoPorNombre(nombreAutorizador)
            .fechaAutorizacion(r.getFechaAutorizacion())
            .build();
    }
}
