package com.uap.controlMedico.model.enums;

/**
 * Estado del flujo de justificación de una incidencia punible (abandono, etc.).
 * Refleja el flujo de los permisos:
 *
 *   PENDIENTE  --(funcionario sube justificación)-->  EN_REVISION
 *   EN_REVISION --(admin aprueba)-->  JUSTIFICADA   (no penaliza)
 *   EN_REVISION --(admin rechaza)-->  RECHAZADA     (queda firme/penaliza)
 *
 * NO_REQUERIDA: la incidencia no es punible (p.ej. retornó a zona antes de
 * confirmarse) y no necesita justificación.
 */
public enum EstadoJustificacion {
    NO_REQUERIDA ("No requerida"),
    PENDIENTE    ("Pendiente de justificar"),
    EN_REVISION  ("En revisión"),
    JUSTIFICADA  ("Justificada"),
    RECHAZADA    ("Rechazada");

    private final String label;
    EstadoJustificacion(String label) { this.label = label; }
    public String getLabel()          { return label; }
}
