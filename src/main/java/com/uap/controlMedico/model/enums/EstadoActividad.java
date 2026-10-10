package com.uap.controlMedico.model.enums;

public enum EstadoActividad {
    PENDIENTE,    // Asignada pero no iniciada
    EN_PROGRESO,  // El interno está ejecutando la actividad
    COMPLETADA,   // Finalizada correctamente
    VALIDADA,
    CANCELADA     // No se realizó
}