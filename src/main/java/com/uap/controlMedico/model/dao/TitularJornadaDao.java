package com.uap.controlMedico.model.dao;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uap.controlMedico.model.entity.Jornada;
import com.uap.controlMedico.model.entity.Lugar;
import com.uap.controlMedico.model.entity.Titular;
import com.uap.controlMedico.model.entity.TitularJornada;
import com.uap.controlMedico.model.enums.EstadoAsignacion;
import com.uap.controlMedico.model.enums.EstadoJornada;

public interface TitularJornadaDao extends JpaRepository<TitularJornada, Long> {
      List<TitularJornada> findByTitular(Titular titular);
    List<TitularJornada> findByJornada(Jornada jornada);
    List<TitularJornada> findByLugar(Lugar lugar);
    List<TitularJornada> findByEstado(EstadoAsignacion estado);

    @Query("""
    SELECT tj FROM TitularJornada tj
    WHERE tj.titular.idTitular  = :idTitular
      AND tj.jornada.idJornada  = :idJornada
    ORDER BY tj.idTitularJornada DESC
    """)
    Optional<TitularJornada> findByTitularAndJornada(@Param("idTitular") Long titular, @Param("idJornada") Long jornada);

    boolean existsByTitularAndJornada(Titular titular, Jornada jornada);
 
    /** FIX: OR explícito */
    @Query("""
        SELECT tj FROM TitularJornada tj
        JOIN tj.jornada j
        WHERE tj.titular.idTitular = :idTitular
          AND (tj.estado = :estadoEnCurso OR tj.estado = :estadoConfirmado)
          AND j.estado   = :estadoJornada
          AND j.fechaInicio <= CURRENT_DATE
          AND j.fechaFin    >= CURRENT_DATE
        ORDER BY j.horaInicio ASC
        """)
    List<TitularJornada> findJornadasActivasDeTitular(
            @Param("idTitular")          Long idTitular,
            @Param("estadoEnCurso")      EstadoAsignacion estadoEnCurso,
            @Param("estadoConfirmado")   EstadoAsignacion estadoConfirmado,
            @Param("estadoJornada")      EstadoJornada estadoJornada);
 
    @Query("""
        SELECT tj FROM TitularJornada tj
        JOIN tj.jornada j
        WHERE j.fechaInicio <= :fecha
          AND j.fechaFin    >= :fecha
          AND tj.estado     != :estadoCancelado
        ORDER BY j.horaInicio ASC, tj.titular.persona.apellido ASC
        """)
    List<TitularJornada> findByFecha(
            @Param("fecha")           LocalDate fecha,
            @Param("estadoCancelado") EstadoAsignacion estadoCancelado);
 
    /** FIX: OR explícito */
    @Query("""
        SELECT tj FROM TitularJornada tj
        JOIN tj.jornada j
        WHERE tj.lugar.idLugar = :idLugar
          AND (tj.estado = :estadoEnCurso OR tj.estado = :estadoConfirmado)
          AND j.fechaInicio <= CURRENT_DATE
          AND j.fechaFin    >= CURRENT_DATE
        """)
    List<TitularJornada> findActivosEnLugar(
            @Param("idLugar")          Long idLugar,
            @Param("estadoEnCurso")    EstadoAsignacion estadoEnCurso,
            @Param("estadoConfirmado") EstadoAsignacion estadoConfirmado);
 
    @Query("""
        SELECT COUNT(tj) > 0 FROM TitularJornada tj
        JOIN tj.jornada j
        WHERE tj.titular.idTitular = :idTitular
          AND tj.estado            != :estadoCancelado
          AND j.fechaInicio        <= :fechaFin
          AND j.fechaFin           >= :fechaInicio
          AND (:idExcluir IS NULL OR tj.idTitularJornada != :idExcluir)
        """)
    boolean existeConflictoHorario(
            @Param("idTitular")       Long idTitular,
            @Param("fechaInicio")     LocalDate fechaInicio,
            @Param("fechaFin")        LocalDate fechaFin,
            @Param("estadoCancelado") EstadoAsignacion estadoCancelado,
            @Param("idExcluir")       Long idExcluir);
 
    @Query("""
        SELECT tj FROM TitularJornada tj
        JOIN tj.jornada j
        WHERE tj.titular.idTitular = :idTitular
          AND j.fechaInicio >= :desde
          AND j.fechaFin    <= :hasta
        ORDER BY j.fechaInicio DESC
        """)
    List<TitularJornada> findHistorialDeTitular(
            @Param("idTitular") Long idTitular,
            @Param("desde")     LocalDate desde,
            @Param("hasta")     LocalDate hasta);
 
    @Modifying
    @Query("""
        UPDATE TitularJornada tj
        SET tj.estado = :nuevoEstado
        WHERE tj.jornada.idJornada = :idJornada
          AND tj.estado = :estadoActual
        """)
    int actualizarEstadoPorJornada(
            @Param("idJornada")    Long idJornada,
            @Param("estadoActual") EstadoAsignacion estadoActual,
            @Param("nuevoEstado")  EstadoAsignacion nuevoEstado);

  boolean existsByTitular_IdTitularAndJornada_IdJornada(Long idTitular, Long idJornada);

    long countByJornada_IdJornada(Long idJornada);

    /**
     * Carga titulares + persona en una sola query para evitar N+1.
     */
    @Query("""
        SELECT tj FROM TitularJornada tj
        JOIN FETCH tj.titular t
        JOIN FETCH t.persona
        LEFT JOIN FETCH tj.lugar
        WHERE tj.jornada.idJornada = :idJornada
        ORDER BY t.persona.apellido ASC
        """)
    List<TitularJornada> findConDetalleByJornada(@Param("idJornada") Long idJornada);

    List<TitularJornada> findByJornada_IdJornada(Long idJornada);

    @Query("""
        SELECT tj FROM TitularJornada tj
        JOIN FETCH tj.jornada j
        JOIN FETCH tj.titular t
        JOIN FETCH t.persona p
        LEFT JOIN FETCH tj.lugar
        WHERE p.idPersona = :perId
          AND j.estado    = 'ACTIVA'
        ORDER BY j.fechaInicio DESC
        """)
    List<TitularJornada> findActivasByPersona(@Param("perId") Long perId);

    @Query("""
        SELECT tj FROM TitularJornada tj
        JOIN FETCH tj.jornada j
        JOIN FETCH tj.titular t
        JOIN FETCH t.persona p
        LEFT JOIN FETCH tj.lugar
        WHERE j.estado = 'ACTIVA'
        """)
    List<TitularJornada> findAllInActiveJornadas();

    /**
     * Todas las jornadas de una persona cuyo rango se solapa con [fechaDesde, fechaHasta].
     * Usada para el calendario de turnos mensual.
     */
    @Query("""
        SELECT tj FROM TitularJornada tj
        JOIN FETCH tj.jornada j
        JOIN FETCH tj.titular t
        JOIN FETCH t.persona p
        LEFT JOIN FETCH tj.lugar
        WHERE p.idPersona = :idPersona
          AND j.fechaInicio <= :fechaHasta
          AND j.fechaFin    >= :fechaDesde
          AND tj.estado != 'CANCELADO'
        ORDER BY j.fechaInicio ASC
        """)
    List<TitularJornada> findByPersonaAndRango(
        @Param("idPersona")   Long      idPersona,
        @Param("fechaDesde")  LocalDate fechaDesde,
        @Param("fechaHasta")  LocalDate fechaHasta);
}
