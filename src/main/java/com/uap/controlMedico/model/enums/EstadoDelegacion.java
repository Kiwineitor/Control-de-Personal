package com.uap.controlMedico.model.enums;

public enum EstadoDelegacion {
    PENDIENTE_APROBACION ("Pendiente de Aprobación"),
    APROBADA             ("Aprobada"),
    EN_CURSO             ("En Curso"),
    COMPLETADA           ("Completada"),
    RECHAZADA            ("Rechazada"),
    CANCELADA            ("Cancelada");

    private final String label;
    EstadoDelegacion(String label) { this.label = label; }
    public String getLabel()       { return label; }
}