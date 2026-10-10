package com.uap.controlMedico.model.dao;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uap.controlMedico.model.entity.ActividadJornada;
import com.uap.controlMedico.model.entity.InternoJornada;
import com.uap.controlMedico.model.enums.EstadoActividad;

public interface ActividadJornadaDao extends JpaRepository<ActividadJornada, Long>{
    List<ActividadJornada> findByInternoJornada(InternoJornada internoJornada);
    List<ActividadJornada> findByInternoJornada_IdInternoJornada(Long idInternoJornada);
    List<ActividadJornada> findByEstadoActividad(EstadoActividad estadoActividad);
    boolean existsByInternoJornadaAndActividad_IdActividad(InternoJornada ij, Long idActividad);
 
    /** FIX: OR explícito */
    @Query("""
        SELECT aj FROM ActividadJornada aj
        JOIN aj.internoJornada ij
        JOIN ij.jornada j
        WHERE ij.interno.idInterno = :idInterno
          AND (aj.estadoActividad = :estadoPendiente
            OR aj.estadoActividad = :estadoEnProgreso)
          AND j.fechaInicio <= CURRENT_DATE
          AND j.fechaFin    >= CURRENT_DATE
        ORDER BY aj.horaInicio ASC NULLS LAST
        """)
    List<ActividadJornada> findPendientesDeInterno(
            @Param("idInterno")       Long idInterno,
            @Param("estadoPendiente") EstadoActividad estadoPendiente,
            @Param("estadoEnProgreso")EstadoActividad estadoEnProgreso);
 
    @Query("""
        SELECT aj FROM ActividadJornada aj
        JOIN aj.internoJornada ij
        JOIN ij.jornada j
        WHERE ij.interno.idInterno = :idInterno
          AND j.fechaInicio >= :desde
          AND j.fechaFin    <= :hasta
        ORDER BY j.fechaInicio DESC, aj.horaInicio ASC
        """)
    List<ActividadJornada> findReporteDeInterno(
            @Param("idInterno") Long idInterno,
            @Param("desde")     LocalDate desde,
            @Param("hasta")     LocalDate hasta);
 
    @Query("""
        SELECT COUNT(aj) FROM ActividadJornada aj
        WHERE aj.internoJornada.idInternoJornada = :idInternoJornada
          AND aj.estadoActividad = :estado
        """)
    long countCompletadasByInternoJornada(
            @Param("idInternoJornada") Long idInternoJornada,
            @Param("estado")           EstadoActividad estado);

  // ── Por InternoJornada ─────────────────────────────────────────────────

    @Query("""
        SELECT aj FROM ActividadJornada aj
        JOIN FETCH aj.actividad
        LEFT JOIN FETCH aj.validadoPor vp LEFT JOIN FETCH vp.persona
        WHERE aj.internoJornada.idInternoJornada = :id
        ORDER BY aj.fechaCreacion DESC
        """)
    List<ActividadJornada> findByInternoJornadaId(@Param("id") Long id);

    // ── Por TitularJornada ────────────────────────────────────────────────

    @Query("""
        SELECT aj FROM ActividadJornada aj
        JOIN FETCH aj.actividad
        LEFT JOIN FETCH aj.validadoPor vp LEFT JOIN FETCH vp.persona
        WHERE aj.titularJornada.idTitularJornada = :id
        ORDER BY aj.fechaCreacion DESC
        """)
    List<ActividadJornada> findByTitularJornadaId(@Param("id") Long id);

    // ── Todas las actividades de una jornada (internos + titulares) ────────

    @Query("""
        SELECT aj FROM ActividadJornada aj
        JOIN FETCH aj.actividad a
        LEFT JOIN FETCH aj.internoJornada ij LEFT JOIN FETCH ij.interno i  LEFT JOIN FETCH i.persona
        LEFT JOIN FETCH aj.titularJornada  tj LEFT JOIN FETCH tj.titular t  LEFT JOIN FETCH t.persona
        LEFT JOIN FETCH aj.validadoPor vp LEFT JOIN FETCH vp.persona
        WHERE (aj.internoJornada IS NOT NULL AND ij.jornada.idJornada = :idJornada)
           OR (aj.titularJornada IS NOT NULL AND tj.jornada.idJornada = :idJornada)
        ORDER BY aj.fechaCreacion DESC
        """)
    List<ActividadJornada> findByJornada(@Param("idJornada") Long idJornada);

    // ── Pendientes e In-Progress de un interno (todas sus jornadas) ────────

    @Query("""
        SELECT aj FROM ActividadJornada aj
        JOIN FETCH aj.actividad
        JOIN FETCH aj.internoJornada ij
        JOIN FETCH ij.jornada j
        WHERE ij.interno.idInterno = :idInterno
          AND aj.estadoActividad IN :estados
        ORDER BY j.fechaInicio DESC
        """)
    List<ActividadJornada> findPendientesInterno(
        @Param("idInterno") Long idInterno,
        @Param("estados")   List<EstadoActividad> estados
    );

    // ── Historial completo de un interno ──────────────────────────────────

    @Query("""
        SELECT aj FROM ActividadJornada aj
        JOIN FETCH aj.actividad
        JOIN FETCH aj.internoJornada ij
        JOIN FETCH ij.jornada
        WHERE ij.interno.idInterno = :idInterno
        ORDER BY aj.fechaCreacion DESC
        """)
    List<ActividadJornada> findHistorialInterno(@Param("idInterno") Long idInterno);

    // ── Historial completo de un titular ──────────────────────────────────

    @Query("""
        SELECT aj FROM ActividadJornada aj
        JOIN FETCH aj.actividad
        JOIN FETCH aj.titularJornada tj
        JOIN FETCH tj.jornada
        WHERE tj.titular.idTitular = :idTitular
        ORDER BY aj.fechaCreacion DESC
        """)
    List<ActividadJornada> findHistorialTitular(@Param("idTitular") Long idTitular);

    // ── Conteos para reportes ─────────────────────────────────────────────

    @Query("""
        SELECT aj.estadoActividad, COUNT(aj) FROM ActividadJornada aj
        WHERE (aj.internoJornada IS NOT NULL AND aj.internoJornada.jornada.idJornada = :id)
           OR (aj.titularJornada IS NOT NULL AND aj.titularJornada.jornada.idJornada = :id)
        GROUP BY aj.estadoActividad
        """)
    List<Object[]> countByEstadoForJornada(@Param("id") Long id);

    @Query("SELECT aj.estadoActividad, COUNT(aj) FROM ActividadJornada aj GROUP BY aj.estadoActividad")
    List<Object[]> countGlobalByEstado();

    @Query("SELECT a.tipoActividad, COUNT(aj) FROM ActividadJornada aj JOIN aj.actividad a GROUP BY a.tipoActividad")
    List<Object[]> countGlobalByTipo();

    // ── PASO 8: Carry-over + listados v2 ─────────────────────────

    /** Para carry-over: actividades no culminadas y no validadas de una jornada.
     *  FETCH JOIN explícito — query de lista, no paginada → sin HHH90003004. */
    @Query("""
        SELECT aj FROM ActividadJornada aj
        JOIN FETCH aj.actividad
        JOIN FETCH aj.internoJornada ij
        JOIN FETCH ij.interno i
        JOIN FETCH i.persona
        JOIN FETCH ij.jornada
        WHERE ij.jornada.idJornada = :idJornada
        AND aj.culminada = false
        AND aj.estadoActividad <> 'VALIDADA'
        """)
    List<ActividadJornada> findPendientesByJornada(@Param("idJornada") Long idJornada);

    /** Listado paginado por jornada (lazy load optimizado con batch_fetch_size). */
    @Query(value = """
        SELECT aj FROM ActividadJornada aj
        LEFT JOIN aj.internoJornada ij
        LEFT JOIN aj.titularJornada tj
        WHERE ij.jornada.idJornada = :idJornada
        OR tj.jornada.idJornada = :idJornada
        """,
        countQuery = """
        SELECT COUNT(aj) FROM ActividadJornada aj
        LEFT JOIN aj.internoJornada ij
        LEFT JOIN aj.titularJornada tj
        WHERE ij.jornada.idJornada = :idJornada
        OR tj.jornada.idJornada = :idJornada
        """)
    Page<ActividadJornada> findByJornada(               // reemplaza la versión anterior
        @Param("idJornada") Long idJornada, Pageable pageable);

    /** Listado paginado por jornada + filtro culminada.
     *  Dos métodos separados para evitar params null (regla #4). */
    
    @Query(value = """
        SELECT aj FROM ActividadJornada aj
        LEFT JOIN aj.internoJornada ij
        LEFT JOIN aj.titularJornada tj
        WHERE (ij.jornada.idJornada = :idJornada OR tj.jornada.idJornada = :idJornada)
        AND aj.culminada = :culminada
        """,
        countQuery = """
        SELECT COUNT(aj) FROM ActividadJornada aj
        LEFT JOIN aj.internoJornada ij
        LEFT JOIN aj.titularJornada tj
        WHERE (ij.jornada.idJornada = :idJornada OR tj.jornada.idJornada = :idJornada)
        AND aj.culminada = :culminada
        """)
    Page<ActividadJornada> findByJornadaAndCulminada(   // reemplaza la versión anterior
        @Param("idJornada") Long idJornada,
        @Param("culminada") boolean culminada,
        Pageable pageable);

    /** Guard anti-duplicado para carry-over. */
    boolean existsByCarryOverDeAndInternoJornada(
        ActividadJornada carryOverDe, InternoJornada internoJornada);


    @Query("""
        SELECT aj FROM ActividadJornada aj
        JOIN FETCH aj.actividad
        WHERE aj.titularJornada.idTitularJornada = :idTJ
        ORDER BY aj.fechaCreacion DESC
        """)
    List<ActividadJornada> findByTitularJornada(@Param("idTJ") Long idTJ);

    @Query("""
        SELECT aj FROM ActividadJornada aj
        JOIN FETCH aj.actividad
        WHERE aj.internoJornada.idInternoJornada = :idIJ
        ORDER BY aj.fechaCreacion DESC
        """)
    List<ActividadJornada> findByInternoJornada(@Param("idIJ") Long idIJ);
}
