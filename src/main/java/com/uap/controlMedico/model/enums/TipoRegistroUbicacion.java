package com.uap.controlMedico.model.enums;

public enum TipoRegistroUbicacion {
    TRANSICION_ZONA,   // Cambio de estado: DENTRO↔FUERA
    SNAPSHOT_PERIODICO, // Registro periódico (cada 5 min, sin cambio de zona)
    INICIO_SESION,     // Primera ubicación al iniciar el turno
    FIN_SESION         // Última ubicación al cerrar el turno
}
