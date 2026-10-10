package com.uap.controlMedico.model.enums;

/**
 * Tipo de incidencia registrada durante un turno. El libro de incidencias es la
 * entidad {@code EventoAbandono} (mantiene ese nombre por compatibilidad con los
 * datos existentes), pero clasifica varios tipos:
 *
 *  - ABANDONO_ZONA    : salió de la geocerca del lugar asignado.
 *  - AUSENCIA_SENAL   : dejó de reportar ubicación (sin señal / GPS apagado).
 *  - ATRASO           : llegó/checó después del inicio del turno (Fase posterior).
 *  - FALTA            : no se presentó durante el turno (Fase posterior).
 *  - SALIDA_TEMPRANA  : dejó el puesto antes del fin del turno (Fase posterior).
 *  - LOGOUT_EN_TURNO  : cerró sesión durante el turno.
 *
 * Los tipos marcados "Fase posterior" se detectarán junto con el trabajo offline
 * (la confirmación fiable depende de la reconciliación). El campo ya existe para
 * que enchufen sin migración.
 */
public enum TipoIncidencia {
    ABANDONO_ZONA   ("Abandono de zona"),
    AUSENCIA_SENAL  ("Ausencia por pérdida de señal"),
    ATRASO          ("Atraso"),
    FALTA           ("Falta"),
    SALIDA_TEMPRANA ("Salida temprana"),
    LOGOUT_EN_TURNO ("Cierre de sesión en turno");

    private final String label;
    TipoIncidencia(String label) { this.label = label; }
    public String getLabel()     { return label; }
}
