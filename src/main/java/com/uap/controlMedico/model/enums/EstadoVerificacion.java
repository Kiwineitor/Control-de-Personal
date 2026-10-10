package com.uap.controlMedico.model.enums;

public enum EstadoVerificacion {
    PENDIENTE,      // Notificación enviada, esperando respuesta
    PRESENTE,       // Personal confirmó presencia con GPS/foto
    AUSENTE,        // Personal respondió indicando que no está
    SIN_RESPUESTA   // Venció el timeout sin respuesta → alerta generada
}