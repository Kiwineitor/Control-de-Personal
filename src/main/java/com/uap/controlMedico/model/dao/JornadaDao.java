package com.uap.controlMedico.model.dao;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uap.controlMedico.model.entity.Jornada;
import com.uap.controlMedico.model.enums.EstadoJornada;
import com.uap.controlMedico.model.enums.TipoJornada;

public interface JornadaDao extends JpaRepository<Jornada, Long> {
 
    Page<Jornada> findByEstado(EstadoJornada estado, Pageable pageable);
 
    List<Jornada> findByTipoJornada(TipoJornada tipoJornada);
 
    long countByEstado(EstadoJornada estado);
 
    // ── CORREGIDO: enums como @Param, CURRENT_DATE sin paréntesis ─────
 
    @Query("""
        SELECT j FROM Jornada j
        WHERE j.estado = :estado
          AND j.fechaInicio <= :fecha
          AND j.fechaFin    >= :fecha
        ORDER BY j.horaInicio ASC
        """)
    List<Jornada> findActivasEnFecha(
            @Param("fecha")  LocalDate fecha,
            @Param("estado") EstadoJornada estado);
 
    @Query("""
        SELECT j FROM Jornada j
        WHERE j.estado = :estado
          AND j.fechaInicio <= :hoy
          AND j.fechaFin    >= :hoy
          AND j.horaInicio  <= :ahora
          AND j.horaFin     >= :ahora
        ORDER BY j.horaInicio ASC
        """)
    List<Jornada> findEnCursoAhora(
            @Param("hoy")    LocalDate hoy,
            @Param("ahora")  LocalTime ahora,
            @Param("estado") EstadoJornada estado);
 
    @Query("""
        SELECT j FROM Jornada j
        WHERE j.fechaInicio >= :desde
          AND j.fechaFin    <= :hasta
        ORDER BY j.fechaInicio ASC, j.horaInicio ASC
        """)
    List<Jornada> findEnRangoFechas(
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);
 
    @Query("""
        SELECT j FROM Jornada j
        WHERE j.estado != :estadoCancelado
          AND j.fechaInicio <= :fechaFin
          AND j.fechaFin    >= :fechaInicio
        """)
    List<Jornada> findSolapadasConPeriodo(
            @Param("fechaInicio")     LocalDate fechaInicio,
            @Param("fechaFin")        LocalDate fechaFin,
            @Param("estadoCancelado") EstadoJornada estadoCancelado);
 
    @Query("""
        SELECT COUNT(j) FROM Jornada j
        WHERE j.estado = :estado
          AND j.fechaInicio <= CURRENT_DATE
          AND j.fechaFin    >= CURRENT_DATE
        """)
    long countJornadasHoy(@Param("estado") EstadoJornada estado);

    /**
     * Filtrado paginado. Mismo patrón que TitularDao/InternoDao:
     * - q siempre String (nunca null), condición :q = '' para "sin filtro".
     * - enums y fechas pueden ser null → IS NULL desactiva el filtro.
     */
    @Query(value = """
        SELECT j FROM Jornada j
        WHERE (:q = '' OR LOWER(COALESCE(j.nombre, '')) LIKE LOWER(CONCAT('%', :q, '%')))
          AND (:estado    IS NULL OR j.estado     = :estado)
          AND (:tipo      IS NULL OR j.tipoJornada = :tipo)
          AND (:fechaIni  IS NULL OR j.fechaInicio >= :fechaIni)
          AND (:fechaFin  IS NULL OR j.fechaFin   <= :fechaFin)
        ORDER BY j.fechaInicio DESC, j.horaInicio DESC
        """,
        countQuery = """
        SELECT COUNT(j) FROM Jornada j
        WHERE (:q = '' OR LOWER(COALESCE(j.nombre, '')) LIKE LOWER(CONCAT('%', :q, '%')))
          AND (:estado    IS NULL OR j.estado     = :estado)
          AND (:tipo      IS NULL OR j.tipoJornada = :tipo)
          AND (:fechaIni  IS NULL OR j.fechaInicio >= :fechaIni)
          AND (:fechaFin  IS NULL OR j.fechaFin   <= :fechaFin)
        """
    )
    Page<Jornada> findConFiltros(
        @Param("q")        String q,
        @Param("estado")   EstadoJornada estado,
        @Param("tipo")     TipoJornada tipo,
        @Param("fechaIni") LocalDate fechaIni,
        @Param("fechaFin") LocalDate fechaFin,
        Pageable pageable
    );

    List<Jornada> findByEstadoOrderByFechaInicioDesc(EstadoJornada estado);
}
