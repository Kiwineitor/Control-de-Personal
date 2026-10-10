package com.uap.controlMedico.controller.api;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.uap.controlMedico.model.dto.asistenciaDto.QrEscaneoRequest;
import com.uap.controlMedico.model.dto.asistenciaDto.RegistroAsistenciaRequest;
import com.uap.controlMedico.model.dto.asistenciaDto.RegistroAsistenciaResponse;
import com.uap.controlMedico.model.dto.response.ApiResponse;
import com.uap.controlMedico.model.service.asistencia.AsistenciaService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/asistencia")
@RequiredArgsConstructor
public class AsistenciaRestController {
    
        private final AsistenciaService asistenciaService;
 
    /**
     * POST /api/v1/asistencia/qr/escanear
     * Endpoint PÚBLICO — no requiere JWT (el QR es la credencial).
     * Usado desde el dispositivo de escaneo en la entrada del hospital.
     */
    @PostMapping("/qr/escanear")
    public ResponseEntity<ApiResponse<RegistroAsistenciaResponse>> escanearQr(
            @Valid @RequestBody QrEscaneoRequest request) {
        RegistroAsistenciaResponse registro = asistenciaService.registrarPorQr(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(
                    request.getEsEntrada() ? "Entrada registrada" : "Salida registrada",
                    registro));
    }
 
    /**
     * POST /api/v1/asistencia/manual
     * Registro manual por administrador o supervisor.
     */
    @PostMapping("/manual")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ApiResponse<RegistroAsistenciaResponse>> registroManual(
            @Valid @RequestBody RegistroAsistenciaRequest request) {
        RegistroAsistenciaResponse registro = asistenciaService.registrar(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Asistencia registrada manualmente", registro));
    }
 
    /** GET /api/v1/asistencia/hoy → Panel de asistencia del día */
    @GetMapping("/hoy")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ApiResponse<List<RegistroAsistenciaResponse>>> hoy() {
        return ResponseEntity.ok(
            ApiResponse.ok(asistenciaService.findByFecha(LocalDate.now())));
    }
 
    /** GET /api/v1/asistencia/por-fecha?fecha=2024-05-10 */
    @GetMapping("/por-fecha")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ApiResponse<List<RegistroAsistenciaResponse>>> porFecha(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(ApiResponse.ok(asistenciaService.findByFecha(fecha)));
    }
 
    /** GET /api/v1/asistencia/tardanzas?desde=2024-05-01&hasta=2024-05-31 */
    @GetMapping("/tardanzas")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ApiResponse<List<RegistroAsistenciaResponse>>> tardanzas(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(ApiResponse.ok(asistenciaService.findTardanzas(desde, hasta)));
    }
 
    /** GET /api/v1/asistencia/inasistencias?desde=...&hasta=... */
    @GetMapping("/inasistencias")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ApiResponse<List<RegistroAsistenciaResponse>>> inasistencias(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(ApiResponse.ok(asistenciaService.findInasistencias(desde, hasta)));
    }
 
    /** GET /api/v1/asistencia/sin-salida?fecha=2024-05-10 → Posibles abandonos */
    @GetMapping("/sin-salida")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ApiResponse<List<RegistroAsistenciaResponse>>> sinSalida(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        LocalDate f = fecha != null ? fecha : LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok(asistenciaService.findConEntradaSinSalida(f)));
    }
 
    /** GET /api/v1/asistencia/historial/{idInterno}?desde=...&hasta=... */
    @GetMapping("/historial/{idInterno}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','MEDICO')")
    public ResponseEntity<ApiResponse<List<RegistroAsistenciaResponse>>> historialInterno(
            @PathVariable Long idInterno,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(
            ApiResponse.ok(asistenciaService.findHistorialDeInterno(idInterno, desde, hasta)));
    }
 
    /** GET /api/v1/asistencia/jornada/{idInternoJornada} */
    @GetMapping("/jornada/{idInternoJornada}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ApiResponse<RegistroAsistenciaResponse>> porJornada(
            @PathVariable Long idInternoJornada) {
        return ResponseEntity.ok(
            ApiResponse.ok(asistenciaService.buscarPorInternoJornada(idInternoJornada)));
    }

}
