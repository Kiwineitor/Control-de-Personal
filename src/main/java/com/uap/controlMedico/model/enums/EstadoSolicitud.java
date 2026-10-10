package com.uap.controlMedico.model.enums;

public enum EstadoSolicitud {
    PENDIENTE       ("Pendiente"),
    APROBADA        ("Aprobada"),
    RECHAZADA       ("Rechazada"),
    CANCELADA       ("Cancelada"),
    EN_OBSERVACION  ("En observación");

    private final String label;
    EstadoSolicitud(String label) { this.label = label; }
    public String getLabel()      { return label; }
}
