package com.uap.controlMedico.model.dto.permisovista;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/** Resumen de permisos de un usuario para la UI de asignación. */
@Getter
@Builder
public class UsuarioVistasResumen {
    /** Vistas que le dan sus roles (informativo, no editable directamente). */
    private List<String>            baseRol;
    /** Ajustes por usuario (GRANT/DENY). */
    private List<OverrideVistaDto>  overrides;
    /** Resultado final que verá el usuario en el menú. */
    private List<String>            efectivas;
}
