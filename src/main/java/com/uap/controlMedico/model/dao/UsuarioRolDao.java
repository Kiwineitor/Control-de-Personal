package com.uap.controlMedico.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uap.controlMedico.model.entity.Rol;
import com.uap.controlMedico.model.entity.Usuario;
import com.uap.controlMedico.model.entity.UsuarioRol;
import com.uap.controlMedico.model.enums.RolNombre;

public interface UsuarioRolDao extends JpaRepository<UsuarioRol, Long>{

       List<UsuarioRol> findByUsuario(Usuario usuario);
 
    List<UsuarioRol> findByUsuario_IdUsuario(Long idUsuario);
 
    boolean existsByUsuarioAndRol(Usuario usuario, Rol rol);
 
    /**
     * CORREGIDO: la entidad UsuarioRol ya no tiene idUsuario ni estado
     * como campos directos. Navega por las FKs reales.
     */
    @Query("""
        SELECT ur FROM UsuarioRol ur
        WHERE ur.usuario.idUsuario = :idUsuario
          AND ur.rol.idRol         = :idRol
        """)
    Optional<UsuarioRol> buscarPorIdUsuarioYRol(
            @Param("idUsuario") Long idUsuario,
            @Param("idRol")     Long idRol);
 
    @Modifying
    @Query("DELETE FROM UsuarioRol ur WHERE ur.usuario.idUsuario = :id")
    void deleteByUsuarioId(@Param("id") Long id);
 
    @Query("""
        SELECT COUNT(ur) > 0 FROM UsuarioRol ur
        WHERE ur.usuario.idUsuario = :idUsuario
          AND ur.rol.nombre        = :rolNombre
        """)
    boolean tieneRol(
            @Param("idUsuario") Long idUsuario,
            @Param("rolNombre") RolNombre rolNombre);
 
    /**
     * Retorna los nombres de los roles del usuario.
     * Retorna String (no enum) porque Rol.nombre es VARCHAR en BD.
     * Resultado esperado: ["ADMIN"] o ["SUPERVISOR"] etc.
     */
    @Query("""
        SELECT r.nombre
        FROM UsuarioRol ur
        JOIN ur.rol r
        WHERE ur.usuario.idUsuario = :idUsuario
          AND r.estado = 'ACTIVO'
        """)
    List<String> findNombresRolByUsuarioId(@Param("idUsuario") Long idUsuario);


    /** Verifica si el usuario ya tiene un rol asignado (regla: máx. 1 rol) */
    @Query("""
        SELECT COUNT(ur) > 0
        FROM UsuarioRol ur
        WHERE ur.usuario.idUsuario = :idUsuario
        """)
    boolean tieneRolAsignado(@Param("idUsuario") Long idUsuario);

    @Modifying
    @Query("DELETE FROM UsuarioRol ur WHERE ur.usuario.idUsuario = :idUsuario")
    void deleteByUsuario_IdUsuario(@Param("idUsuario") Long idUsuario);
} 