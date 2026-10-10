package com.uap.controlMedico.model.enums;

public enum EstadoPermiso {
    PENDIENTE,  // Solicitud registrada, esperando revisión
    APROBADA,   // Aprobada por supervisor/admin
    RECHAZADA   // Rechazada con observación obligatoria
}
