package com.uap.controlMedico.controller.appController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.uap.controlMedico.model.dto.actividadDto.ActividadCatalogoDto;
import com.uap.controlMedico.model.dto.actividadDto.ActividadJornadaResponse;
import com.uap.controlMedico.model.dto.actividadDto.AutoReporteRequest;
import com.uap.controlMedico.model.dto.actividadDto.DiaTurnoCalendarioDto;
import com.uap.controlMedico.model.dto.actividadDto.MiFaltaDto;
import com.uap.controlMedico.model.dto.actividadDto.MiJornadaResponse;
import com.uap.controlMedico.model.dto.actividadDto.MiSolicitudRequest;
import com.uap.controlMedico.model.dto.bitacoraDto.BitacoraEntradaResponse;
import com.uap.controlMedico.model.dto.bitacoraDto.CrearBitacoraRequest;
import com.uap.controlMedico.model.dto.permisosDto.SolicitudPermisoResponse;
import com.uap.controlMedico.model.dto.response.ApiResponse;
import com.uap.controlMedico.model.service.appActividades.AppFuncionarioService;
import com.uap.controlMedico.model.service.bitacora.BitacoraService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/app")
@RequiredArgsConstructor
public class AppFuncionarioController {
    
    private final AppFuncionarioService appFuncionarioService;
    private final BitacoraService bitacoraService;

    private static final String ROLES_APP =
        "hasAnyRole('MEDICO','ENFERMERO','CAJERO','INTERNO','FARMACIA','LIMPIEZA')";

    /** Mi jornada activa actual + resumen */
    @GetMapping("/mi-jornada")
    @PreAuthorize(ROLES_APP)
    public ResponseEntity<ApiResponse<MiJornadaResponse>> getMiJornada() {
        return ResponseEntity.ok(ApiResponse.ok(appFuncionarioService.getMiJornada()));
    }

    /** Mis actividades (con filtro opcional: ?culminada=true/false) */
    @GetMapping("/mis-actividades")
    @PreAuthorize(ROLES_APP)
    public ResponseEntity<ApiResponse<List<ActividadJornadaResponse>>> getMisActividades(
        @RequestParam(required = false) Boolean culminada
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            appFuncionarioService.getMisActividades(culminada)));
    }

    /** Registrar una actividad propia (auto-reporte) */
    @PostMapping("/mis-actividades")
    @PreAuthorize(ROLES_APP)
    public ResponseEntity<ApiResponse<ActividadJornadaResponse>> crearMiActividad(
        @Valid @RequestBody AutoReporteRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            appFuncionarioService.crearMiActividad(request)));
    }

    /** Catálogo de actividades para el selector del formulario */
    @GetMapping("/catalogo-actividades")
    @PreAuthorize(ROLES_APP)
    public ResponseEntity<ApiResponse<List<ActividadCatalogoDto>>> getCatalogo() {
        return ResponseEntity.ok(ApiResponse.ok(
            appFuncionarioService.getCatalogoActividades()));
    }

    @GetMapping("/mis-permisos")
    @PreAuthorize(ROLES_APP)
    public ResponseEntity<ApiResponse<List<SolicitudPermisoResponse>>> getMisPermisos() {
        return ResponseEntity.ok(ApiResponse.ok(appFuncionarioService.getMisPermisos()));
    }

    @PostMapping("/mis-permisos")
    @PreAuthorize(ROLES_APP)
    public ResponseEntity<ApiResponse<SolicitudPermisoResponse>> crearMiPermiso(
        @Valid @RequestBody MiSolicitudRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(appFuncionarioService.crearMiPermiso(request)));
    }

    @PutMapping("/mis-permisos/{id}")
    @PreAuthorize(ROLES_APP)
    public ResponseEntity<ApiResponse<SolicitudPermisoResponse>> modificarMiPermiso(
        @PathVariable Long id,
        @Valid @RequestBody MiSolicitudRequest request
    ) throws java.io.IOException {
        return ResponseEntity.ok(ApiResponse.ok(appFuncionarioService.modificarMiPermiso(id, request)));
    }

    @PostMapping("/mis-permisos/{id}/justificante")
    @PreAuthorize(ROLES_APP)
    public ResponseEntity<ApiResponse<SolicitudPermisoResponse>> subirJustificante(
        @PathVariable Long id,
        @RequestParam("file") MultipartFile file
    ) throws IOException {
        return ResponseEntity.ok(ApiResponse.ok(
            appFuncionarioService.subirMiJustificante(id, file)));
    }

    @GetMapping("/tipos-permiso")
    @PreAuthorize(ROLES_APP)
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getTiposPermiso() {
        return ResponseEntity.ok(ApiResponse.ok(appFuncionarioService.getTiposPermiso()));
    }

    /** Historial de eventos de abandono de zona del usuario actual */
    @GetMapping("/mis-faltas")
    @PreAuthorize(ROLES_APP)
    public ResponseEntity<ApiResponse<List<MiFaltaDto>>> getMisFaltas() {
        return ResponseEntity.ok(ApiResponse.ok(appFuncionarioService.getMisFaltas()));
    }

    /** Días de turno del mes/año solicitado para el usuario actual */
    @GetMapping("/mis-turnos")
    @PreAuthorize(ROLES_APP)
    public ResponseEntity<ApiResponse<List<DiaTurnoCalendarioDto>>> getMisTurnos(
        @RequestParam(required = false) Integer mes,
        @RequestParam(required = false) Integer anio
    ) {
        java.time.LocalDate hoy = java.time.LocalDate.now();
        int m = mes  != null ? mes  : hoy.getMonthValue();
        int a = anio != null ? anio : hoy.getYear();
        return ResponseEntity.ok(ApiResponse.ok(
            appFuncionarioService.getMisTurnos(m, a)));
    }

    // ══════════════════════════════════════════════════════════════════
    //  BITÁCORA DEL TURNO
    // ══════════════════════════════════════════════════════════════════

    @PostMapping("/bitacora")
    @PreAuthorize(ROLES_APP)
    public ResponseEntity<ApiResponse<BitacoraEntradaResponse>> crearBitacora(
        @Valid @RequestBody CrearBitacoraRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(bitacoraService.crearEntrada(request)));
    }

    @PostMapping("/bitacora/{id}/evidencia")
    @PreAuthorize(ROLES_APP)
    public ResponseEntity<ApiResponse<BitacoraEntradaResponse>> subirEvidenciaBitacora(
        @PathVariable Long id,
        @RequestParam("file") MultipartFile file
    ) throws IOException {
        return ResponseEntity.ok(ApiResponse.ok(bitacoraService.subirEvidencia(id, file)));
    }

    @GetMapping("/bitacora")
    @PreAuthorize(ROLES_APP)
    public ResponseEntity<ApiResponse<List<BitacoraEntradaResponse>>> getMisEntradasBitacora(
        @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.ok(bitacoraService.misEntradas(limit)));
    }

    @GetMapping("/bitacora/jornada/{idTitularJornada}")
    @PreAuthorize(ROLES_APP)
    public ResponseEntity<ApiResponse<List<BitacoraEntradaResponse>>> getBitacoraPorJornada(
        @PathVariable Long idTitularJornada
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            bitacoraService.entradasPorJornada(idTitularJornada)));
    }
}
