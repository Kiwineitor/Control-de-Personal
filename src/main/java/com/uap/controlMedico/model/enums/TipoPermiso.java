package com.uap.controlMedico.model.enums;

public enum TipoPermiso {
    AUSENCIA_TOTAL          ("Ausencia Total"),
    PERMISO_MEDICO          ("Permiso Médico"),
    TRAMITE_ADMINISTRATIVO  ("Trámite Administrativo"),
    OTRO                    ("Otro");

    private final String label;
    TipoPermiso(String label) { this.label = label; }
    public String getLabel()  { return label; }
}
