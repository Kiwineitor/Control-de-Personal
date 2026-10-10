package com.uap.controlMedico.model.service.permiso;

import java.util.List;
import java.util.Set;

import com.uap.controlMedico.model.dto.permisovista.OverrideVistaDto;
import com.uap.controlMedico.model.dto.permisovista.UsuarioVistasResumen;
import com.uap.controlMedico.model.entity.Vista;

/**
 * Calcula y administra las "vistas" (opciones de menú) del modelo híbrido:
 * base del/los rol(es) ∪ GRANT por usuario − DENY por usuario. ADMIN ve todo.
 */
public interface PermisoVistaService {

    /** Códigos de vista efectivos del usuario (para filtrar el menú en el frontend). */
    Set<String> getVistasEfectivas(String username);

    /** Catálogo completo de vistas activas. */
    List<Vista> getCatalogo();

    /** Códigos de vista base asignados a un rol. */
    List<String> getCodigosDeRol(Long idRol);

    /** Reemplaza el conjunto de vistas base de un rol. */
    void setVistasDeRol(Long idRol, List<String> codigos);

    /** Resumen de un usuario (base por rol + overrides + efectivas) para la UI. */
    UsuarioVistasResumen getResumenUsuario(Long idUsuario);

    /** Reemplaza los overrides (GRANT/DENY) de un usuario. */
    void setOverridesDeUsuario(Long idUsuario, List<OverrideVistaDto> overrides);
}
