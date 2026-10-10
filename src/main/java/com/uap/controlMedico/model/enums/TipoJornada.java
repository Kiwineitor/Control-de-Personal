package com.uap.controlMedico.model.enums;

public enum TipoJornada {
    REGULAR,        // Turno estándar (mañana, tarde, noche)
    GUARDIA_12H,    // Guardia de 12 horas
    GUARDIA_24H,    // Guardia de 24 horas
    NOCTURNA,       // Turno nocturno (ej. 19:00-07:00)
    ROTACION,       // Rotación MIR/Internos
    LLAMADA,        // Personal de guardia pasiva (on-call)
    EMERGENCIA,     // Convocatoria por emergencia
    COBERTURA,      // Cubre una ausencia puntual
    ADMINISTRATIVA, // Personal administrativo/apoyo con horario fijo
    GUARDIA         // Legacy — alias de GUARDIA_24H (compatibilidad)
}