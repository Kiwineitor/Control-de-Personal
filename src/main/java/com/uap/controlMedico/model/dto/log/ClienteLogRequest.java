package com.uap.controlMedico.model.dto.log;

import lombok.Data;

/** Una línea de log enviada por el frontend (consola de la app) al backend. */
@Data
public class ClienteLogRequest {
    private String level;     // ERROR | WARN | INFO | DEBUG
    private String logger;    // origen lógico (ruta/vista), opcional
    private String message;
    private String detalle;   // stack/extra, opcional
}
