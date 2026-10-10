package com.uap.controlMedico.model.dto.monitoreo;

import lombok.Builder;
import lombok.Getter;

/**
 * Resultado de procesar un lote de ubicaciones bufferizadas (store-and-forward).
 * El cliente nativo lo usa para confirmar la sincronización y reflejar el estado
 * resultante del fix más reciente.
 */
@Getter
@Builder
public class LoteUbicacionResponse {
    /** Fixes recibidos en el lote. */
    private int recibidos;
    /** Fixes efectivamente procesados. */
    private int procesados;
    /** Fixes omitidos por idempotencia (duplicados o ya procesados antes). */
    private int omitidos;
    /** Estado resultante tras el fix más reciente del lote (null si no se procesó ninguno). */
    private UbicacionResponse ultimoEstado;
}
