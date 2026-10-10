package com.uap.controlMedico.model.enums;

public enum EstadoEntrada {
    A_TIEMPO,        // Llegó dentro del margen de tolerancia
    TARDANZA,        // Llegó después del margen de tolerancia
    AUSENTE,   // No registró entrada durante el turno
    JUSTIFICADO
}