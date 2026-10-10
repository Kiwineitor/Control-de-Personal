package com.uap.controlMedico.model.dao;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.uap.controlMedico.model.entity.Interno;
import com.uap.controlMedico.model.enums.EstadoAsignacion;
import com.uap.controlMedico.model.enums.EstadoJornada;

@Repository
public interface InternoDao extends JpaRepository<Interno, Long> {
    // Optional<Interno> findByPerId(Long perId);
    // Optional<Interno> findByPerIdAndActivoTrue(Long perId);
    // boolean existsByPerId(Long perId);
    List<Interno> findByActivoTrue();
    Page<Interno> findByActivoTrue(Pageable pageable);
    List<Interno> findByUniversidadContainingIgnoreCaseAndActivoTrue(String universidad);
    List<Interno> findByCarreraContainingIgnoreCaseAndActivoTrue(String carrera);
 
    @Query("""
        SELECT i FROM Interno i
        WHERE i.activo = true
          AND (LOWER(i.persona.nombre)       LIKE LOWER(CONCAT('%', :termino, '%'))
            OR LOWER(i.persona.apellido)     LIKE LOWER(CONCAT('%', :termino, '%'))
            OR LOWER(i.persona.ci)           LIKE LOWER(CONCAT('%', :termino, '%'))
            OR LOWER(i.nroMatricula) LIKE LOWER(CONCAT('%', :termino, '%'))
            OR LOWER(i.universidad)  LIKE LOWER(CONCAT('%', :termino, '%')))
        ORDER BY i.persona.apellido, i.persona.nombre
        """)
    List<Interno> buscarPorTermino(@Param("termino") String termino);
 
    @Query("""
        SELECT i FROM Interno i
        WHERE i.activo = true
          AND i.fechaInicioRotacion <= :fecha
          AND i.fechaFinRotacion    >= :fecha
        ORDER BY i.persona.apellido, i.persona.nombre
        """)
    List<Interno> findEnRotacionEnFecha(@Param("fecha") LocalDate fecha);
 
    @Query("""
        SELECT i FROM Interno i
        WHERE i.activo = true
          AND i.fechaFinRotacion BETWEEN :hoy AND :limite
        ORDER BY i.fechaFinRotacion ASC
        """)
    List<Interno> findRotacionProximaAVencer(
            @Param("hoy")    LocalDate hoy,
            @Param("limite") LocalDate limite);
 
    /**
     * FIX Hibernate 6: OR explícito en lugar de IN (:list).
     */
    @Query("""
        SELECT DISTINCT i FROM Interno i
        JOIN i.internoJornadas ij
        JOIN ij.jornada j
        WHERE i.activo = true
          AND (ij.estado = :estadoEnCurso OR ij.estado = :estadoConfirmado)
          AND j.estado   = :estadoJornada
          AND j.fechaInicio <= CURRENT_DATE
          AND j.fechaFin    >= CURRENT_DATE
        """)
    List<Interno> findInternosEnTurnoActual(
            @Param("estadoEnCurso")    EstadoAsignacion estadoEnCurso,
            @Param("estadoConfirmado") EstadoAsignacion estadoConfirmado,
            @Param("estadoJornada")    EstadoJornada estadoJornada);
 
    @Query("""
        SELECT i FROM Interno i
        JOIN i.responsablesInterno ri
        WHERE ri.titular.idTitular = :idTitular
          AND ri.activo = true
          AND i.activo  = true
        ORDER BY i.persona.apellido, i.persona.nombre
        """)
    List<Interno> findByTitularResponsable(@Param("idTitular") Long idTitular);

    @Query("SELECT i FROM Interno i JOIN i.persona p WHERE " +
           "(:paramQi IS NULL OR " +
           "  LOWER(p.nombre) LIKE LOWER(CONCAT('%', :paramQi, '%')) OR " +
           "  LOWER(p.apellido) LIKE LOWER(CONCAT('%', :paramQi, '%')) OR " +
           "  p.ci LIKE CONCAT('%', :paramQi, '%')) " +
           "AND (:paramCarrera IS NULL OR i.carrera = :paramCarrera) " +
           "AND (:paramUniversidad IS NULL OR i.universidad = :paramUniversidad) " +
           "AND (:paramActivoI IS NULL OR i.activo = :paramActivoI) " +
           "AND (:paramEnRotacion IS NULL OR " +
           "     (:paramEnRotacion = true AND CURRENT_DATE BETWEEN i.fechaInicioRotacion AND i.fechaFinRotacion) OR " +
           "     (:paramEnRotacion = false AND (i.fechaInicioRotacion IS NULL OR CURRENT_DATE < i.fechaInicioRotacion OR CURRENT_DATE > i.fechaFinRotacion)))")
    Page<Interno> findFiltrados(
            @Param("paramQi") String paramQi,
            @Param("paramCarrera") String paramCarrera,
            @Param("paramUniversidad") String paramUniversidad,
            @Param("paramActivoI") Boolean paramActivoI,
            @Param("paramEnRotacion") Boolean paramEnRotacion,
            Pageable pageable);

    @Query("SELECT i FROM Interno i WHERE i.activo = true")
    List<Interno> findAllActivos();

    /* PARA VUE PAPI */

    /**
     * Búsqueda dinámica con filtros opcionales.
     * enRotacion filtra por: fechaInicioRotacion <= HOY <= fechaFinRotacion
     */
            @Query("""
        SELECT i FROM Interno i
        JOIN i.persona p
        WHERE (:q = '' OR
               LOWER(p.nombre)       LIKE CONCAT('%', :q, '%') OR
               LOWER(p.apellido)     LIKE CONCAT('%', :q, '%') OR
               LOWER(p.ci)           LIKE CONCAT('%', :q, '%') OR
               LOWER(i.nroMatricula) LIKE CONCAT('%', :q, '%'))
          AND (:carrera     = '' OR LOWER(i.carrera)     = :carrera)
          AND (:universidad = '' OR LOWER(i.universidad) = :universidad)
          AND (:activo IS NULL OR i.activo = :activo)
          AND (:enRotacion IS NULL OR
              (:enRotacion = TRUE  AND i.fechaInicioRotacion <= CURRENT_DATE
                                   AND i.fechaFinRotacion    >= CURRENT_DATE) OR
              (:enRotacion = FALSE AND (i.fechaInicioRotacion > CURRENT_DATE
                                   OR  i.fechaFinRotacion    < CURRENT_DATE
                                   OR  i.fechaInicioRotacion IS NULL)))
        """)
    Page<Interno> findConFiltros(
            @Param("q")           String  q,
            @Param("carrera")     String  carrera,
            @Param("universidad") String  universidad,
            @Param("enRotacion")  Boolean enRotacion,
            @Param("activo")      Boolean activo,
            Pageable pageable);


    @Query("SELECT i FROM Interno i WHERE i.activo = true")
    List<Interno> findActivosOrdenados();

    /** Lista simple de activos — para dropdown y responsable card. */
    List<Interno> findByActivoTrueOrderByPersonaApellidoAsc();
 
    /** Internos activos sin responsable asignado. */
    @Query("""
        SELECT i FROM Interno i
        WHERE i.activo = true
          AND NOT EXISTS (SELECT 1 FROM ResponsableInterno r WHERE r.activo = true)
        """)
    List<Interno> findSinResponsableActivo();
 
    @Query("SELECT DISTINCT i.carrera FROM Interno i " +
           "WHERE i.carrera IS NOT NULL AND i.carrera <> '' ORDER BY i.carrera")
    List<String> findDistinctCarreras();

 
        @Query("SELECT DISTINCT i.universidad FROM Interno i " +
           "WHERE i.universidad IS NOT NULL AND i.universidad <> '' ORDER BY i.universidad")
    List<String> findDistinctUniversidades();

    @Query("SELECT i FROM Interno i JOIN FETCH i.persona WHERE i.activo = true ORDER BY i.persona.apellido ASC")
    List<Interno> findActivosConPersona();
}
