package com.uap.controlMedico.model.enums;

public enum EstadoRonda {
    PENDIENTE,   // Ronda creada pero no iniciada
    EN_CURSO,    // Verificaciones enviadas, esperando respuestas
    COMPLETADA,  // Todas las verificaciones procesadas
    CANCELADA    // Ronda anulada manualmente
}
