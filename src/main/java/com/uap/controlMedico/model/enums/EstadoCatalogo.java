package com.uap.controlMedico.model.enums;

public enum EstadoCatalogo {
    ACTIVO,     // Disponible para usar
    INACTIVO,   // Deshabilitado temporalmente
    ELIMINADO   // Soft-delete; nunca se muestra al usuario
}
