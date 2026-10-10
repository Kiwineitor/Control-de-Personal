package com.uap.controlMedico.model.dao;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uap.controlMedico.model.entity.Interno;
import com.uap.controlMedico.model.entity.InternoJornada;
import com.uap.controlMedico.model.entity.Jornada;
import com.uap.controlMedico.model.entity.Lugar;
import com.uap.controlMedico.model.enums.EstadoAsignacion;
import com.uap.controlMedico.model.enums.EstadoJornada;

public interface InternoJornadaDao extends JpaRepository<InternoJornada, Long> {
        List<InternoJornada> findByInterno(Interno interno);
    List<InternoJornada> findByJornada(Jornada jornada);
    List<InternoJornada> findByLugar(Lugar lugar);
    List<InternoJornada> findByEstado(EstadoAsignacion estado);
    Optional<InternoJornada> findByInternoAndJornada(Interno interno, Jornada jornada);
    boolean existsByInternoAndJornada(Interno interno, Jornada jornada);
 
    /** FIX: OR explícito por cada estado en lugar de IN (:list) */
    @Query("""
        SELECT ij FROM InternoJornada ij
        JOIN ij.jornada j
        WHERE ij.interno.idInterno = :idInterno
          AND (ij.estado = :estadoEnCurso OR ij.estado = :estadoConfirmado
               OR ij.estado = :estadoProgramado)
          AND j.estado   = :estadoJornada
          AND j.fechaInicio <= CURRENT_DATE
          AND j.fechaFin    >= CURRENT_DATE
        ORDER BY j.horaInicio ASC
        """)
    List<InternoJornada> findJornadasActivasDeInterno(
            @Param("idInterno")          Long idInterno,
            @Param("estadoEnCurso")      EstadoAsignacion estadoEnCurso,
            @Param("estadoConfirmado")   EstadoAsignacion estadoConfirmado,
            @Param("estadoProgramado")   EstadoAsignacion estadoProgramado,
            @Param("estadoJornada")      EstadoJornada estadoJornada);
 
    @Query("""
        SELECT ij FROM InternoJornada ij
        JOIN ij.jornada j
        WHERE j.fechaInicio <= :fecha
          AND j.fechaFin    >= :fecha
          AND ij.estado     != :estadoCancelado
        ORDER BY j.horaInicio ASC, ij.interno.persona.apellido ASC
        """)
    List<InternoJornada> findByFecha(
            @Param("fecha")           LocalDate fecha,
            @Param("estadoCancelado") EstadoAsignacion estadoCancelado);
 
    /** FIX: OR explícito */
    @Query("""
        SELECT ij FROM InternoJornada ij
        JOIN ij.jornada j
        WHERE ij.lugar.idLugar = :idLugar
          AND (ij.estado = :estadoEnCurso OR ij.estado = :estadoConfirmado)
          AND j.fechaInicio <= CURRENT_DATE
          AND j.fechaFin    >= CURRENT_DATE
        """)
    List<InternoJornada> findActivosEnLugar(
            @Param("idLugar")          Long idLugar,
            @Param("estadoEnCurso")    EstadoAsignacion estadoEnCurso,
            @Param("estadoConfirmado") EstadoAsignacion estadoConfirmado);
 
    @Query("""
        SELECT ij FROM InternoJornada ij
        JOIN ij.jornada j
        WHERE ij.interno.idInterno = :idInterno
          AND j.fechaInicio >= :desde
          AND j.fechaFin    <= :hasta
        ORDER BY j.fechaInicio DESC
        """)
    List<InternoJornada> findHistorialDeInterno(
            @Param("idInterno") Long idInterno,
            @Param("desde")     LocalDate desde,
            @Param("hasta")     LocalDate hasta);
 
    @Modifying
    @Query("""
        UPDATE InternoJornada ij
        SET ij.estado = :nuevoEstado
        WHERE ij.jornada.idJornada = :idJornada
          AND ij.estado = :estadoActual
        """)
    int actualizarEstadoPorJornada(
            @Param("idJornada")    Long idJornada,
            @Param("estadoActual") EstadoAsignacion estadoActual,
            @Param("nuevoEstado")  EstadoAsignacion nuevoEstado);

  boolean existsByInterno_IdInternoAndJornada_IdJornada(Long idInterno, Long idJornada);

    long countByJornada_IdJornada(Long idJornada);

    /**
     * Carga internos + persona + responsable en una sola query.
     */
    @Query("""
        SELECT ij FROM InternoJornada ij
        JOIN FETCH ij.interno i
        JOIN FETCH i.persona
        LEFT JOIN FETCH ij.lugar
        LEFT JOIN FETCH ij.responsableInterno ri
        LEFT JOIN FETCH ri.titular rt
        LEFT JOIN FETCH rt.persona
        WHERE ij.jornada.idJornada = :idJornada
        ORDER BY i.persona.apellido ASC
        """)
    List<InternoJornada> findConDetalleByJornada(@Param("idJornada") Long idJornada);

    List<InternoJornada> findByJornada_IdJornada(Long idJornada);

    /** Próximas jornadas ACTIVAS del mismo interno (excluye la que se está cerrando).
     *  ORDER BY fechaInicio ASC → toma la más próxima con .findFirst(). */
    @Query("""
        SELECT ij FROM InternoJornada ij
        JOIN FETCH ij.jornada j
        JOIN FETCH ij.interno i
        WHERE i.idInterno = :idInterno
          AND j.idJornada <> :excluirJornada
          AND j.estado   = 'ACTIVA'
        ORDER BY j.fechaInicio ASC
        """)
    List<InternoJornada> findProximasActivasParaInterno(
        @Param("idInterno")      Long idInterno,
        @Param("excluirJornada") Long excluirJornada);

    @Query("""
        SELECT ij FROM InternoJornada ij
        JOIN FETCH ij.jornada j
        JOIN FETCH ij.interno i
        JOIN FETCH i.persona p
        LEFT JOIN FETCH ij.lugar
        WHERE p.idPersona = :perId
          AND j.estado    = 'ACTIVA'
        ORDER BY j.fechaInicio DESC
        """)
    List<InternoJornada> findActivasByPersona(@Param("perId") Long perId);

    @Query("""
        SELECT ij FROM InternoJornada ij
        JOIN FETCH ij.jornada j
        JOIN FETCH ij.interno i
        JOIN FETCH i.persona p
        LEFT JOIN FETCH ij.lugar
        WHERE j.estado = 'ACTIVA'
        """)
    List<InternoJornada> findAllInActiveJornadas();
}
