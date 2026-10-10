package com.uap.controlMedico.model.enums;

/** Tipo de ajuste por usuario sobre las vistas que le da su rol. */
public enum TipoOverrideVista {
    /** Agrega la vista aunque el rol no la incluya. */
    GRANT,
    /** Quita la vista aunque el rol la incluya. */
    DENY
}
