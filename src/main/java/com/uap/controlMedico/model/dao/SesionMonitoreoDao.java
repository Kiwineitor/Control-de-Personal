package com.uap.controlMedico.model.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uap.controlMedico.model.entity.SesionMonitoreo;

public interface SesionMonitoreoDao extends JpaRepository<SesionMonitoreo, Long> {

    List<SesionMonitoreo> findByActivaTrue();

    /**
     * Sesión activa de un titular — la más reciente. findFirst...OrderBy tolera
     * duplicados (creados por pings concurrentes) sin lanzar excepción.
     */
    Optional<SesionMonitoreo> findFirstByTitular_IdTitularAndActivaTrueOrderByTimestampInicioDesc(Long idTitular);

    Optional<SesionMonitoreo> findFirstByInterno_IdInternoAndActivaTrueOrderByTimestampInicioDesc(Long idInterno);

    /** Todas las sesiones activas de un titular (para cerrar duplicados). */
    List<SesionMonitoreo> findByTitular_IdTitularAndActivaTrue(Long idTitular);

    List<SesionMonitoreo> findByInterno_IdInternoAndActivaTrue(Long idInterno);

    boolean existsByTitular_IdTitularAndActivaTrue(Long idTitular);

    boolean existsByInterno_IdInternoAndActivaTrue(Long idInterno);

    /**
     * ¿Ya se abrió alguna sesión hoy para esta persona en esta jornada?
     *
     * Una sesión cerrada por falta o inactividad se vuelve a crear en la pasada
     * siguiente del scheduler mientras el turno siga corriendo. Sin esta consulta
     * cada recreación mandaba otro "HOY TIENES TURNO": en un turno de 6 h eran
     * cinco o seis avisos idénticos al mismo funcionario.
     */
    boolean existsByTitular_IdTitularAndJornada_IdJornadaAndTimestampInicioBetween(
        Long idTitular, Long idJornada, LocalDateTime desde, LocalDateTime hasta);

    boolean existsByInterno_IdInternoAndJornada_IdJornadaAndTimestampInicioBetween(
        Long idInterno, Long idJornada, LocalDateTime desde, LocalDateTime hasta);

    /**
     * ¿Esta persona ya registró su llegada (primer fix DENTRO) hoy en esta jornada?
     *
     * La llegada es un hecho del TURNO, no de la sesión. Una sesión cerrada por
     * inactividad se recrea en la pasada siguiente del scheduler con
     * timestampPrimeraPresencia en null, así que el atraso se recalculaba desde la
     * hora de inicio del turno hasta "ahora": alguien que llegó puntual, se quedó
     * sin señal y volvió tres horas después recibía un "atraso de 208 min", y otro
     * más grande en cada reconexión. Con esta consulta el atraso se evalúa una sola
     * vez al día: en la llegada de verdad.
     */
    @Query("""
        SELECT COUNT(s) > 0 FROM SesionMonitoreo s
        LEFT JOIN s.titular t
        LEFT JOIN s.interno i
        WHERE s.jornada.idJornada = :idJornada
          AND s.timestampPrimeraPresencia IS NOT NULL
          AND s.timestampPrimeraPresencia >= :desde
          AND s.timestampPrimeraPresencia <  :hasta
          AND (t.idTitular = :idTitular OR i.idInterno = :idInterno)
        """)
    boolean yaRegistroLlegadaHoy(@Param("idJornada") Long idJornada,
                                 @Param("idTitular") Long idTitular,
                                 @Param("idInterno") Long idInterno,
                                 @Param("desde") LocalDateTime desde,
                                 @Param("hasta") LocalDateTime hasta);

    /** Todas las sesiones de una jornada (históricas + activas), más recientes primero. */
    List<SesionMonitoreo> findByJornadaIdJornadaOrderByTimestampInicioDesc(Long idJornada);

    /** Sesiones activas con datos del personal para el mapa. */
    @Query("""
        SELECT s FROM SesionMonitoreo s
        LEFT JOIN FETCH s.titular t
        LEFT JOIN FETCH t.persona
        LEFT JOIN FETCH s.interno i
        LEFT JOIN FETCH i.persona
        LEFT JOIN FETCH s.lugar
        WHERE s.activa = true
        ORDER BY s.timestampInicio DESC
        """)
    List<SesionMonitoreo> findActivasConDetalle();
}
