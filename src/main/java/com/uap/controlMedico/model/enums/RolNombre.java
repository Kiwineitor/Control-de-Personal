package com.uap.controlMedico.model.enums;

public enum RolNombre {
    ADMIN,       // Administrador total del sistema
    SUPERVISOR,  // Supervisor (aprueba permisos, delegaciones)
    MEDICO,      // Médico: turnos, actividades, permisos, alertas, gestión internos
    ENFERMERO,   // Enfermero: turnos, actividades, permisos, alertas
    CAJERO,      // Administrativo: permisos, alertas
    FARMACIA,    // Farmacia: permisos, alertas
    LIMPIEZA,    // Limpieza/Servicios: permisos, alertas
    INTERNO      // Estudiante en rotación: solo actividades asignadas
}
