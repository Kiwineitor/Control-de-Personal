package com.uap.controlMedico.model.enums;

public enum ModalidadHorario {
    CONTINUA,  // Una sola franja horaria (horaInicio → horaFin)
    PARTIDA    // Dos franjas: mañana (horaInicio-horaFin) + tarde (horaInicioTarde-horaFinTarde)
}
