package com.uap.controlMedico.model.dao;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uap.controlMedico.model.entity.Titular;
import com.uap.controlMedico.model.enums.EstadoAsignacion;
import com.uap.controlMedico.model.enums.EstadoJornada;

public interface TitularDao extends JpaRepository<Titular, Long> {
  Optional<Titular> findByPerId(Long perId);
    Optional<Titular> findByPerIdAndActivoTrue(Long perId);
    Optional<Titular> findByContratoId(Long contratoId);
    boolean existsByPerId(Long perId);
    List<Titular> findByActivoTrue();
    Page<Titular> findByActivoTrue(Pageable pageable);
    Page<Titular> findByEspecialidadContainingIgnoreCaseAndActivoTrue(String especialidad, Pageable pageable);
    List<Titular> findByCargoContainingIgnoreCaseAndActivoTrue(String cargo);
 
    @Query("""
        SELECT t FROM Titular t
        WHERE t.activo = true
          AND (LOWER(t.persona.nombre)   LIKE LOWER(CONCAT('%', :termino, '%'))
            OR LOWER(t.persona.apellido) LIKE LOWER(CONCAT('%', :termino, '%'))
            OR LOWER(t.persona.ci)       LIKE LOWER(CONCAT('%', :termino, '%'))
            OR LOWER(t.cargo)    LIKE LOWER(CONCAT('%', :termino, '%')))
        ORDER BY t.persona.apellido, t.persona.nombre
        """)
    List<Titular> buscarPorTermino(@Param("termino") String termino);
 
    /**
     * FIX Hibernate 6: IN (:list) con enums falla.
     * Solución: OR explícito por cada estado posible.
     */
    @Query("""
        SELECT DISTINCT t FROM Titular t
        JOIN t.titularJornadas tj
        JOIN tj.jornada j
        WHERE t.activo = true
          AND (tj.estado = :estadoEnCurso OR tj.estado = :estadoConfirmado)
          AND j.estado   = :estadoJornada
          AND j.fechaInicio <= CURRENT_DATE
          AND j.fechaFin    >= CURRENT_DATE
        """)
    List<Titular> findTitularesEnTurnoActual(
            @Param("estadoEnCurso")    EstadoAsignacion estadoEnCurso,
            @Param("estadoConfirmado") EstadoAsignacion estadoConfirmado,
            @Param("estadoJornada")    EstadoJornada estadoJornada);
 
    @Query("""
        SELECT t FROM Titular t
        JOIN t.responsablesInterno ri
        WHERE ri.interno.idInterno = :idInterno
          AND ri.activo = true
          AND t.activo  = true
        """)
    List<Titular> findResponsablesDeInterno(@Param("idInterno") Long idInterno);
 
    @Query("""
        SELECT t FROM Titular t
        WHERE t.activo = true
          AND t.idTitular NOT IN (
              SELECT tj.titular.idTitular FROM TitularJornada tj
              JOIN tj.jornada j
              WHERE j.fechaInicio <= :fecha
                AND j.fechaFin    >= :fecha
                AND tj.estado     != :estadoCancelado
          )
        ORDER BY t.persona.apellido, t.persona.nombre
        """)
    List<Titular> findSinTurnoEnFecha(
            @Param("fecha")           LocalDate fecha,
            @Param("estadoCancelado") EstadoAsignacion estadoCancelado);


    @Query("SELECT t FROM Titular t JOIN t.persona p WHERE " +
           "(:paramQ IS NULL OR " +
           "  LOWER(p.nombre) LIKE LOWER(CONCAT('%', :paramQ, '%')) OR " +
           "  LOWER(p.apellido) LIKE LOWER(CONCAT('%', :paramQ, '%')) OR " +
           "  p.ci LIKE CONCAT('%', :paramQ, '%')) " +
           "AND (:paramCargo IS NULL OR t.cargo = :paramCargo) " +
           "AND (:paramEspecialidad IS NULL OR t.especialidad = :paramEspecialidad) " +
           "AND (:paramActivo IS NULL OR t.activo = :paramActivo)")
    Page<Titular> findFiltrados(
            @Param("paramQ") String paramQ,
            @Param("paramCargo") String paramCargo,
            @Param("paramEspecialidad") String paramEspecialidad,
            @Param("paramActivo") Boolean paramActivo,
            Pageable pageable);

    @Query("SELECT t FROM Titular t WHERE t.activo = true")
    List<Titular> findAllActivos();


    /* PARA VUE PAPI */

    /**
     * Búsqueda dinámica con filtros opcionales.
     * Todos los parámetros son opcionales — si son null se ignoran.
     */
        @Query("""
        SELECT t FROM Titular t
        JOIN t.persona p
        WHERE (:q = '' OR
               LOWER(p.nombre)       LIKE CONCAT('%', :q, '%') OR
               LOWER(p.apellido)     LIKE CONCAT('%', :q, '%') OR
               LOWER(p.ci)           LIKE CONCAT('%', :q, '%') OR
               LOWER(t.cargo)        LIKE CONCAT('%', :q, '%') OR
               LOWER(t.especialidad) LIKE CONCAT('%', :q, '%'))
          AND (:cargo        = '' OR LOWER(t.cargo)        = :cargo)
          AND (:especialidad = '' OR LOWER(t.especialidad) = :especialidad)
          AND (:activo IS NULL OR t.activo = :activo)
        """)
    Page<Titular> findConFiltros(
            @Param("q")            String  q,
            @Param("cargo")        String  cargo,
            @Param("especialidad") String  especialidad,
            @Param("activo")       Boolean activo,
            Pageable pageable);

    /** Filtro adicional con TipoPersonal. */
    @Query("""
        SELECT t FROM Titular t
        JOIN t.persona p
        LEFT JOIN t.tipoPersonal tp
        WHERE (:q = '' OR
               LOWER(p.nombre)       LIKE CONCAT('%', :q, '%') OR
               LOWER(p.apellido)     LIKE CONCAT('%', :q, '%') OR
               LOWER(p.ci)           LIKE CONCAT('%', :q, '%') OR
               LOWER(t.cargo)        LIKE CONCAT('%', :q, '%'))
          AND (:idTipo IS NULL OR tp.idTipoPersonal = :idTipo)
          AND (:activo IS NULL OR t.activo = :activo)
        ORDER BY p.apellido ASC
        """)
    Page<Titular> findPersonalConFiltros(
            @Param("q")      String  q,
            @Param("idTipo") Long    idTipoPersonal,
            @Param("activo") Boolean activo,
            Pageable pageable);


    /** Lista simple de activos ordenada — para dropdown. */
    List<Titular> findByActivoTrueOrderByPersonaApellidoAsc();

    @Query("SELECT t FROM Titular t WHERE t.activo = true")
    List<Titular> findActivosOrdenados();

    /** Valores únicos de cargo (ignorando nulls y vacíos). */
    @Query("SELECT DISTINCT t.cargo FROM Titular t " +
           "WHERE t.cargo IS NOT NULL AND t.cargo <> '' ORDER BY t.cargo")
    List<String> findDistinctCargos();


    /** Valores únicos de especialidad. */
    @Query("SELECT DISTINCT t.especialidad FROM Titular t " +
           "WHERE t.especialidad IS NOT NULL AND t.especialidad <> '' ORDER BY t.especialidad")
    List<String> findDistinctEspecialidades();

    @Query("SELECT t FROM Titular t JOIN FETCH t.persona WHERE t.activo = true ORDER BY t.persona.apellido ASC")
    List<Titular> findActivosConPersona();

    Optional<Titular> findByPersona_IdPersona(Long idPersona);

}
