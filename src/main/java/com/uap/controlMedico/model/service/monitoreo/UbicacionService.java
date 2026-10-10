package com.uap.controlMedico.model.service.monitoreo;

import java.util.List;

import com.uap.controlMedico.model.dto.monitoreo.LoteUbicacionResponse;
import com.uap.controlMedico.model.dto.monitoreo.UbicacionRequest;
import com.uap.controlMedico.model.dto.monitoreo.UbicacionResponse;

public interface UbicacionService {
    UbicacionResponse registrarUbicacion(String username, UbicacionRequest request);

    /**
     * Procesa un lote de ubicaciones bufferizadas en el dispositivo (store-and-forward).
     * Las reproduce en orden de timestamp de dispositivo, con idempotencia por
     * clientEventId. Al reproducir fixes DENTRO de zona, la lógica existente resuelve
     * automáticamente cualquier abandono provisional abierto → reconciliación.
     */
    LoteUbicacionResponse procesarLote(String username, List<UbicacionRequest> fixes);

    /** Cierra la sesión GPS activa del usuario (llamado al hacer logout). */
    void cerrarSesionActiva(String username);
}
