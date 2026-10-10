package com.uap.controlMedico.model.enums;

public enum EstadoEvento {
    FUERA_ZONA,         // Detectado fuera de zona, aún dentro del timeout
    ABANDONO_CONFIRMADO, // Timeout superado sin retorno
    RETORNADO,          // Volvió a la zona (resuelto automáticamente)
    RESUELTO            // Resuelto manualmente por el admin
}
