package com.uap.controlMedico.model.enums;

public enum EstadoAsignacion {
    PROGRAMADO,  // Asignado pero aún no iniciado
    CONFIRMADO,  // El personal confirmó su asistencia
    EN_CURSO,    // Turno actualmente en ejecución
    COMPLETADO,  // Turno finalizado correctamente
    CANCELADO    // Turno cancelado (reemplazo, permiso, etc.)
}