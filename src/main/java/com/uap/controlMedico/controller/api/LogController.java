package com.uap.controlMedico.controller.api;

import java.security.Principal;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.uap.controlMedico.model.dto.log.ClienteLogRequest;
import com.uap.controlMedico.model.dto.response.ApiResponse;
import com.uap.controlMedico.shared.logging.LogBuffer;

/**
 * Consola del sistema: expone el buffer de logs (backend + frontend) para que el
 * admin pueda diagnosticar incidentes en producción sin acceso a la consola del
 * servidor. El frontend (incluida la app) envía su consola por POST /cliente.
 */
@RestController
@RequestMapping("/api/v1/logs")
public class LogController {

    /** Lista logs filtrados. Solo ADMIN/SUPERVISOR (pueden contener datos sensibles). */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ApiResponse<List<LogBuffer.Entry>>> listar(
        @RequestParam(required = false) String origin,   // BACKEND | FRONTEND
        @RequestParam(required = false) String level,    // nivel mínimo
        @RequestParam(required = false) String q,         // texto a buscar
        @RequestParam(required = false) Long   sinceId,   // solo ids mayores (polling incremental)
        @RequestParam(defaultValue = "500") int limit
    ) {
        final String origenF = origin != null ? origin.trim() : "";
        final String texto   = q != null ? q.trim().toLowerCase() : "";
        final int nivelMin   = severidad(level);

        List<LogBuffer.Entry> filtrados = LogBuffer.snapshot().stream()
            .filter(e -> sinceId == null || e.id() > sinceId)
            .filter(e -> origenF.isEmpty() || origenF.equalsIgnoreCase(e.origin()))
            .filter(e -> nivelMin == 0 || severidad(e.level()) >= nivelMin)
            .filter(e -> texto.isEmpty()
                || (e.message() != null && e.message().toLowerCase().contains(texto))
                || (e.logger()  != null && e.logger().toLowerCase().contains(texto)))
            .toList();

        // Conservar solo los últimos `limit`.
        int desde = Math.max(0, filtrados.size() - Math.max(1, limit));
        return ResponseEntity.ok(ApiResponse.ok(filtrados.subList(desde, filtrados.size())));
    }

    /** Recibe la consola del frontend (cualquier usuario autenticado, incl. la app). */
    @PostMapping("/cliente")
    public ResponseEntity<ApiResponse<Void>> recibirCliente(
        Principal principal,
        @RequestBody List<ClienteLogRequest> entradas
    ) {
        String usuario = principal != null ? principal.getName() : null;
        if (entradas != null) {
            for (ClienteLogRequest e : entradas) {
                LogBuffer.add("FRONTEND",
                    e.getLevel(), e.getLogger(), null, e.getMessage(), e.getDetalle(), usuario);
            }
        }
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /** Vacía el buffer. */
    @DeleteMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<ApiResponse<Void>> limpiar() {
        LogBuffer.clear();
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /** Severidad numérica para filtrar "nivel mínimo". 0 = sin filtro. */
    private static int severidad(String level) {
        if (level == null) return 0;
        switch (level.trim().toUpperCase()) {
            case "TRACE": return 1;
            case "DEBUG": return 2;
            case "INFO":  return 3;
            case "WARN":  return 4;
            case "ERROR": return 5;
            default:      return 0;
        }
    }
}
