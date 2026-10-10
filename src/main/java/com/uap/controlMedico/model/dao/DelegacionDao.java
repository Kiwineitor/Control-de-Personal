package com.uap.controlMedico.model.dao;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uap.controlMedico.model.entity.Delegacion;
import com.uap.controlMedico.model.enums.EstadoDelegacion;

public interface DelegacionDao extends JpaRepository<Delegacion, Long> {
    List<Delegacion> findByEstadoOrderByFechaCreacionDesc(EstadoDelegacion estado);
 
    @Query("""
        SELECT d FROM Delegacion d
        WHERE d.estado = :estado
        ORDER BY d.fechaCreacion ASC
        """)
    List<Delegacion> findPendientesDeAprobacion(@Param("estado") EstadoDelegacion estado);
 
    @Query("""
        SELECT d FROM Delegacion d
        WHERE d.titularJornada.idTitularJornada = :idTitularJornada
          AND d.estado = :estado
        """)
    Optional<Delegacion> findActivaByTitularJornada(
            @Param("idTitularJornada") Long idTitularJornada,
            @Param("estado")           EstadoDelegacion estado);
 
    /**
     * FIX: OR explícito por cada estado en lugar de IN (:list).
     */
    @Query("""
        SELECT COUNT(d) > 0 FROM Delegacion d
        JOIN d.titularJornada tj
        JOIN tj.jornada j
        WHERE tj.titular.idTitular = :idTitular
          AND (d.estado = :estadoAprobada OR d.estado = :estadoEnCurso)
          AND j.fechaInicio <= CURRENT_DATE
          AND j.fechaFin    >= CURRENT_DATE
        """)
    boolean titularTieneDelegacionActivaHoy(
            @Param("idTitular")      Long idTitular,
            @Param("estadoAprobada") EstadoDelegacion estadoAprobada,
            @Param("estadoEnCurso")  EstadoDelegacion estadoEnCurso);
 
    @Query("""
        SELECT d FROM Delegacion d
        WHERE d.titularJornada.titular.idTitular = :idTitular
        ORDER BY d.fechaCreacion DESC
        """)
    List<Delegacion> findByTitular(@Param("idTitular") Long idTitular);
 
    @Query("""
        SELECT d FROM Delegacion d
        WHERE d.internoResponsable.idInterno = :idInterno
        ORDER BY d.fechaCreacion DESC
        """)
    List<Delegacion> findByInterno(@Param("idInterno") Long idInterno);
 
    @Query("""
        SELECT d FROM Delegacion d
        JOIN d.titularJornada tj
        JOIN tj.jornada j
        WHERE j.fechaInicio >= :desde
          AND j.fechaFin    <= :hasta
          AND (:estado IS NULL OR d.estado = :estado)
        ORDER BY j.fechaInicio DESC
        """)
    List<Delegacion> findReporte(
            @Param("desde")  LocalDate desde,
            @Param("hasta")  LocalDate hasta,
            @Param("estado") EstadoDelegacion estado);

    /* PASO 66666666 WUUWHIWDILUDFISJD */

    @Query("""
        SELECT d FROM Delegacion d
        WHERE (:idJornada IS NULL OR d.titularJornada.jornada.idJornada = :idJornada)
          AND (:estado    IS NULL OR d.estado = :estado)
        ORDER BY d.fechaCreacion DESC
        """)
    Page<Delegacion> findWithFilters(
        @Param("idJornada") Long idJornada,
        @Param("estado")    EstadoDelegacion estado,
        Pageable pageable
    );

    @Query("SELECT d FROM Delegacion d WHERE d.estado IN :estados ORDER BY d.fechaCreacion DESC")
    List<Delegacion> findByEstadoIn(@Param("estados") List<EstadoDelegacion> estados);

    @Query("SELECT d FROM Delegacion d WHERE d.titularJornada.jornada.idJornada = :idJornada ORDER BY d.fechaCreacion DESC")
    List<Delegacion> findByJornada(@Param("idJornada") Long idJornada);

    @Query("SELECT COUNT(d) FROM Delegacion d WHERE d.estado = :estado")
    Long countByEstado(@Param("estado") EstadoDelegacion estado);
}
