package com.uap.controlMedico.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uap.controlMedico.model.dto.rol.RolResponse;
import com.uap.controlMedico.model.entity.Rol;
import com.uap.controlMedico.model.enums.RolNombre;

public interface RolDao extends JpaRepository<Rol, Long>{

    Optional<Rol> findByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCase(String nombre);

    // ── Lista completa para selects en formularios ─────────────────────
    @Query("""
        SELECT r FROM Rol r
        WHERE r.estado = 'ACTIVO'
        ORDER BY r.nombre
        """)
    List<Rol> findAllActivos();


    // ── Paginado con filtros (Decision #4: params siempre String, nunca null) ──
    @Query(value = """
        SELECT r FROM Rol r
        WHERE (:nombre = '' OR LOWER(r.nombre) LIKE LOWER(CONCAT('%', :nombre, '%')))
          AND (:estado = '' OR r.estado = :estado)
        ORDER BY r.nombre
        """,
        countQuery = """
        SELECT COUNT(r) FROM Rol r
        WHERE (:nombre = '' OR LOWER(r.nombre) LIKE LOWER(CONCAT('%', :nombre, '%')))
          AND (:estado = '' OR r.estado = :estado)
        """)
    Page<Rol> findWithFilters(
        @Param("nombre") String nombre,
        @Param("estado") String estado,
        Pageable pageable
    );

    // ── Proyección con cantidad de usuarios (evita lazy N+1) ──────────
    // ── Proyección con cantidad de usuarios (evita lazy N+1) ──────────
    @Query( value = """
        SELECT new com.uap.controlMedico.model.dto.rol.RolResponse(
            r.idRol,
            r.nombre,
            r.estado,
            COUNT(ur),
            r.fechaCreacion
        )
        FROM Rol r
        LEFT JOIN r.usuariosRol ur
        WHERE (:nombre = '' OR LOWER(r.nombre) LIKE LOWER(CONCAT('%', :nombre, '%')))
          AND (:estado = '' OR r.estado = :estado)
        GROUP BY r.idRol, r.nombre, r.estado, r.fechaCreacion
        ORDER BY r.nombre
        """,
        countQuery = """
        SELECT COUNT(DISTINCT r) FROM Rol r
        WHERE (:nombre = '' OR LOWER(r.nombre) LIKE LOWER(CONCAT('%', :nombre, '%')))
          AND (:estado = '' OR r.estado = :estado)
        """)
    Page<RolResponse> findWithFiltersAndCount(
        @Param("nombre") String nombre,
        @Param("estado") String estado,
        Pageable pageable
    );

        // ── Búsquedas base ─────────────────────────────────────────────────
 
    /**
     * Usado en DataInitializer y asignación de roles.
     * Reemplaza el antiguo buscarPorNombre con estado != 'ELIMINADO'
     * → ahora usamos el enum RolNombre y soft-delete no aplica a Rol.
     */
    Optional<Rol> findByNombre(RolNombre nombre);
 
    boolean existsByNombre(RolNombre nombre);
 
    List<Rol> findAll();
 
    // ── Queries corregidas — nueva estructura de UsuarioRol ────────────
 
    /**
     * Lista los roles de un usuario por su ID.
     *
     * ANTES (incorrecto con nueva entidad):
     *   JOIN u.rol r WHERE u.idUsuario = ?1 AND u.estado != 'ELIMINADO'
     *   → UsuarioRol ya no tiene idUsuario ni estado como campos directos.
     *
     * AHORA (correcto):
     *   → u.usuario.idUsuario navega por la FK real hacia Usuario.
     */
    @Query("""
        SELECT ur.rol FROM UsuarioRol ur
        WHERE ur.usuario.idUsuario = :idUsuario
        """)
    List<Rol> listarRolesPorUsuario(@Param("idUsuario") Long idUsuario);
 
    /**
     * Busca un rol específico asignado a un usuario.
     *
     * ANTES (incorrecto): u.idUsuario = ?1 AND u.idRol = ?2
     * AHORA: navega por las FKs reales.
     */
    @Query("""
        SELECT ur.rol FROM UsuarioRol ur
        WHERE ur.usuario.idUsuario = :idUsuario
          AND ur.rol.idRol         = :idRol
        """)
    Optional<Rol> buscarRolPorUsuarioYRol(
            @Param("idUsuario") Long idUsuario,
            @Param("idRol")     Long idRol);
 
    /**
     * Verifica si un usuario tiene un rol específico por nombre.
     */
    @Query("""
        SELECT COUNT(ur) > 0 FROM UsuarioRol ur
        WHERE ur.usuario.idUsuario = :idUsuario
          AND ur.rol.nombre        = :rolNombre
        """)
    boolean usuarioTieneRol(
            @Param("idUsuario")  Long idUsuario,
            @Param("rolNombre")  RolNombre rolNombre);
}
