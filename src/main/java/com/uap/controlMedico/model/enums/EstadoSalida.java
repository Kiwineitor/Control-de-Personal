package com.uap.controlMedico.model.enums;

public enum EstadoSalida {
    COMPLETO,           // Salió al finalizar el turno correctamente
    SALIDA_ANTICIPADA,  // Salió antes de terminar el turno
    NO_REGISTRADA       // No registró salida (puede implicar abandono de puesto)
}
