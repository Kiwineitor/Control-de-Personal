package com.uap.controlMedico.model.dao;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uap.controlMedico.model.entity.RegistroAsistencia;
import com.uap.controlMedico.model.enums.EstadoEntrada;
import com.uap.controlMedico.model.enums.EstadoSalida;

public interface RegistroAsistenciaDao extends JpaRepository<RegistroAsistencia, Long> {

    Optional<RegistroAsistencia> findByInternoJornada_Interno_IdInternoAndInternoJornada_Jornada_FechaInicio(
            Long idInterno, LocalDate fecha);

    // ── Reporte de asistencia diaria ───────────────────────────────────

    @Query("""
            SELECT ra FROM RegistroAsistencia ra
            JOIN ra.internoJornada ij
            JOIN ij.jornada j
            WHERE j.fechaInicio <= :fecha
              AND j.fechaFin    >= :fecha
            ORDER BY j.horaInicio ASC, ij.interno.persona.apellido ASC
            """)
    List<RegistroAsistencia> findByFecha(@Param("fecha") LocalDate fecha);

    // ── CORREGIDO: enums como @Param ──────────────────────────────────

    @Query("""
            SELECT ra FROM RegistroAsistencia ra
            JOIN ra.internoJornada ij
            JOIN ij.jornada j
            WHERE ra.estadoEntrada = :estadoEntrada
              AND j.fechaInicio >= :desde
              AND j.fechaFin    <= :hasta
            ORDER BY j.fechaInicio DESC
            """)
    List<RegistroAsistencia> findTardanzasEnPeriodo(
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta,
            @Param("estadoEntrada") EstadoEntrada estadoEntrada);

    @Query("""
            SELECT ra FROM RegistroAsistencia ra
            JOIN ra.internoJornada ij
            JOIN ij.jornada j
            WHERE ra.estadoEntrada = :estadoEntrada
              AND j.fechaInicio >= :desde
              AND j.fechaFin    <= :hasta
            ORDER BY j.fechaInicio DESC
            """)
    List<RegistroAsistencia> findInasistenciasEnPeriodo(
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta,
            @Param("estadoEntrada") EstadoEntrada estadoEntrada);

    @Query("""
            SELECT ra FROM RegistroAsistencia ra
            JOIN ra.internoJornada ij
            JOIN ij.jornada j
            WHERE ij.interno.idInterno = :idInterno
              AND j.fechaInicio >= :desde
              AND j.fechaFin    <= :hasta
            ORDER BY j.fechaInicio DESC
            """)
    List<RegistroAsistencia> findHistorialDeInterno(
            @Param("idInterno") Long idInterno,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);

    // ── Sin salida registrada ──────────────────────────────────────────

    @Query("""
            SELECT ra FROM RegistroAsistencia ra
            JOIN ra.internoJornada ij
            JOIN ij.jornada j
            WHERE ra.horaEntradaReal IS NOT NULL
              AND ra.horaSalidaReal  IS NULL
              AND j.fechaFin <= :fecha
            ORDER BY j.fechaFin DESC
            """)
    List<RegistroAsistencia> findConEntradaSinSalida(@Param("fecha") LocalDate fecha);

    // ── Contadores para dashboard ──────────────────────────────────────

    @Query("""
            SELECT COUNT(ra) FROM RegistroAsistencia ra
            JOIN ra.internoJornada ij
            JOIN ij.jornada j
            WHERE ra.estadoEntrada = :estado
              AND j.fechaInicio <= :fecha
              AND j.fechaFin    >= :fecha
            """)
    long countByEstadoEntradaEnFecha(
            @Param("estado") EstadoEntrada estado,
            @Param("fecha") LocalDate fecha);

    @Query("""
            SELECT COUNT(ra) FROM RegistroAsistencia ra
            JOIN ra.internoJornada ij
            JOIN ij.jornada j
            WHERE ra.estadoSalida = :estado
              AND j.fechaInicio <= :fecha
              AND j.fechaFin    >= :fecha
            """)
    long countByEstadoSalidaEnFecha(
            @Param("estado") EstadoSalida estado,
            @Param("fecha") LocalDate fecha);

    Optional<RegistroAsistencia> findByInternoJornada_IdInternoJornada(Long id);

    Optional<RegistroAsistencia> findByTitularJornada_IdTitularJornada(Long id);

    @Query("""
            SELECT ra FROM RegistroAsistencia ra
            LEFT JOIN FETCH ra.internoJornada ij LEFT JOIN FETCH ij.interno i  LEFT JOIN FETCH i.persona
            LEFT JOIN FETCH ra.titularJornada  tj LEFT JOIN FETCH tj.titular t  LEFT JOIN FETCH t.persona
            WHERE (ra.internoJornada IS NOT NULL AND ij.jornada.idJornada = :idJornada)
               OR (ra.titularJornada IS NOT NULL AND tj.jornada.idJornada = :idJornada)
            ORDER BY ra.fechaCreacion
            """)
    List<RegistroAsistencia> findByJornada(@Param("idJornada") Long idJornada);
}