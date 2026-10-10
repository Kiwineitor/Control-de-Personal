package com.uap.controlMedico.model.service;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import com.uap.controlMedico.model.dto.rol.RolRequest;
import com.uap.controlMedico.model.dto.rol.RolResponse;
import com.uap.controlMedico.model.entity.Rol;
import com.uap.controlMedico.model.enums.RolNombre;

public interface RolService extends GenericoService <Rol, Long>{
    Optional<Rol> findByNombre(RolNombre nombre);
    List<Rol> listarRolesPorUsuario(Long idUsuario);
    Optional<Rol> buscarRolPorUsuarioYRol(
            @Param("idUsuario") Long idUsuario,
            @Param("idRol")     Long idRol);

    /** Grid principal paginado con cantidad de usuarios por rol */
    Page<RolResponse> getAll(String nombre, String estado, Pageable pageable);

    /** Select de formulario: solo roles ACTIVOS */
    List<RolResponse> getAllActivos();

    RolResponse getById(Long id);

    RolResponse crear(RolRequest request);

    RolResponse actualizar(Long id, RolRequest request);

    /** Activa un rol desactivado */
    void activar(Long id);

    /**
     * Desactiva un rol.
     * Lanza excepción si hay usuarios ACTIVOS con ese rol asignado.
     */
    void desactivar(Long id);
}
