package com.uap.controlMedico.model.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.uap.controlMedico.model.entity.Usuario;

@Repository
public interface UsuarioDao extends JpaRepository<Usuario, Long> {
 
    Optional<Usuario> findByEmail(String email);
 
    // ── Vínculo con Londra ─────────────────────────────────────────────
 
    /**
     * Busca el usuario del sistema vinculado a una persona de Londra.
     * Útil para saber si un titular/interno ya tiene cuenta creada.
     */
    Optional<Usuario> findByPerIdAndActivoTrue(Long perId);
 
    boolean existsByPerId(Long perId);
 
    // ── Filtros administrativos ────────────────────────────────────────
 
    List<Usuario> findByActivoTrue();
 
    List<Usuario> findByActivoFalse();
 
    /** Busca usuarios por nombre o apellido (case-insensitive). */
    @Query("""
        SELECT u FROM Usuario u
        WHERE u.activo = true
          AND (LOWER(u.nombre)   LIKE LOWER(CONCAT('%', :termino, '%'))
            OR LOWER(u.apellido) LIKE LOWER(CONCAT('%', :termino, '%'))
            OR LOWER(u.username) LIKE LOWER(CONCAT('%', :termino, '%')))
        ORDER BY u.apellido, u.nombre
        """)
    List<Usuario> buscarPorTermino(@Param("termino") String termino);
 
    /** Usuarios que no han accedido desde una fecha dada. Útil para detectar cuentas inactivas. */
    @Query("""
        SELECT u FROM Usuario u
        WHERE u.activo = true
          AND (u.ultimoAcceso IS NULL OR u.ultimoAcceso < :desde)
        ORDER BY u.ultimoAcceso ASC NULLS FIRST
        """)
    List<Usuario> findInactivosDesde(@Param("desde") LocalDateTime desde);
 
    /** Actualiza el último acceso al hacer login exitoso. */
    @Modifying
    @Query("UPDATE Usuario u SET u.ultimoAcceso = :ahora WHERE u.idUsuario = :id")
    void actualizarUltimoAcceso(@Param("id") Long id, @Param("ahora") LocalDateTime ahora);

    /**
     * Fija la sesión activa del usuario (sesión única). Pasar null la cierra.
     *
     * Al escribir un UUID nuevo, cualquier JWT emitido para la sesión anterior deja
     * de validar en JwtAuthFilter y JwtChannelInterceptor: es lo que expulsa al
     * dispositivo previo cuando el usuario inicia sesión en otro.
     */
    @Modifying
    @Query("UPDATE Usuario u SET u.sesionActual = :sesion WHERE u.idUsuario = :id")
    void actualizarSesionActual(@Param("id") Long id, @Param("sesion") String sesion);

    /* pso 7 papiiiii */
    /** Carga usuario + roles en un solo JOIN para UserDetailsService */
    @Query("""
        SELECT u FROM Usuario u
        LEFT JOIN FETCH u.usuariosRol ur
        LEFT JOIN FETCH ur.rol
        WHERE u.username = :username
        """)
    Optional<Usuario> findByUsernameWithRoles(@Param("username") String username);

    Optional<Usuario> findByUsername(String username);

    /** Carga usuario + persona en un solo JOIN — para identificación en la app. */
    @Query("""
        SELECT u FROM Usuario u
        LEFT JOIN FETCH u.persona
        WHERE u.username = :username
        """)
    Optional<Usuario> findByUsernameWithPersona(@Param("username") String username);

    boolean existsByUsername(String username);

    boolean existsByPersonaIdPersona(Long idPersona);

    /** Busca por token de reset (para validar solicitud de cambio de clave) */
    @Query("""
        SELECT u FROM Usuario u
        WHERE u.tokenReset = :token
          AND u.tokenResetExpiry > CURRENT_TIMESTAMP
        """)
    Optional<Usuario> findByTokenResetValido(@Param("token") String token);

    /** Actualiza último acceso sin disparar @PreUpdate completo */
    @Modifying
    @Query("UPDATE Usuario u SET u.ultimoAcceso = CURRENT_TIMESTAMP WHERE u.idUsuario = :id")
    void actualizarUltimoAcceso(@Param("id") Long id);

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

    @Query("""
        SELECT COUNT(ur) > 0
        FROM UsuarioRol ur
        WHERE ur.usuario.idUsuario = :idUsuario
        """)
    boolean tieneRolAsignado(@Param("idUsuario") Long idUsuario);

    // void deleteByUsuario_IdUsuario(Long idUsuario);


    // Añadir en UsuarioDao.java (Decision #17: LEFT JOIN + countQuery separado)

    @Query(value = """
        SELECT DISTINCT u FROM Usuario u
        LEFT JOIN u.usuariosRol ur
        LEFT JOIN ur.rol r
        WHERE (:termino = ''
            OR LOWER(u.nombre)   LIKE LOWER(CONCAT('%', :termino, '%'))
            OR LOWER(u.apellido) LIKE LOWER(CONCAT('%', :termino, '%'))
            OR LOWER(u.username) LIKE LOWER(CONCAT('%', :termino, '%')))
        AND (:rolNombre = '' OR r.nombre = :rolNombre)
        AND (:activo IS NULL OR u.activo = :activo)
        """,
        countQuery = """
        SELECT COUNT(DISTINCT u) FROM Usuario u
        LEFT JOIN u.usuariosRol ur
        LEFT JOIN ur.rol r
        WHERE (:termino = ''
            OR LOWER(u.nombre)   LIKE LOWER(CONCAT('%', :termino, '%'))
            OR LOWER(u.apellido) LIKE LOWER(CONCAT('%', :termino, '%'))
            OR LOWER(u.username) LIKE LOWER(CONCAT('%', :termino, '%')))
        AND (:rolNombre = '' OR r.nombre = :rolNombre)
        AND (:activo IS NULL OR u.activo = :activo)
        """)
    Page<Usuario> findWithFilters(
        @Param("termino")   String  termino,
        @Param("rolNombre") String  rolNombre,
        @Param("activo")    Boolean activo,
        Pageable pageable
    );

    // Buscar usuario por perId (para notificaciones WebSocket)
    Optional<Usuario> findByPerId(Long perId);

    /** Retorna todos los usuarios activos que tengan alguno de los roles indicados. */
    @Query("""
        SELECT DISTINCT u FROM Usuario u
        JOIN u.usuariosRol ur
        JOIN ur.rol r
        WHERE r.nombre IN :roles AND u.activo = true
        """)
    List<Usuario> findActivosByRoles(@Param("roles") List<String> roles);

    /**
     * Busca el usuario vinculado a una Persona por su PK local (id_persona).
     * Funciona tanto para Titular (que tiene perId de Londra) como para
     * Interno (que solo tiene la relación persona, sin perId propio).
     */
    Optional<Usuario> findByPersonaIdPersona(Long idPersona);

}