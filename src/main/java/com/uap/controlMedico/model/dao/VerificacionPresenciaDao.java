package com.uap.controlMedico.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uap.controlMedico.model.entity.VerificacionPresencia;
import com.uap.controlMedico.model.enums.EstadoVerificacion;

public interface VerificacionPresenciaDao extends JpaRepository<VerificacionPresencia, Long> {
   
    @Query("""
        SELECT v FROM VerificacionPresencia v
        LEFT JOIN FETCH v.titular t LEFT JOIN FETCH t.persona
        LEFT JOIN FETCH v.interno  i LEFT JOIN FETCH i.persona
        WHERE v.rondaVerificacion.idRondaVerificacion = :idRonda
        ORDER BY v.estadoVerificacion, v.fechaHoraEnvio
        """)
    List<VerificacionPresencia> findByRondaConDetalle(@Param("idRonda") Long idRonda);

    Optional<VerificacionPresencia> findByRondaVerificacion_IdRondaVerificacionAndInterno_IdInterno(
        Long idRonda, Long idInterno);

    Optional<VerificacionPresencia> findByRondaVerificacion_IdRondaVerificacionAndTitular_IdTitular(
        Long idRonda, Long idTitular);

    List<VerificacionPresencia> findByRondaVerificacion_IdRondaVerificacionAndEstadoVerificacion(
        Long idRonda, EstadoVerificacion estado);

    /** Verifications PENDIENTE de un interno en TODAS las rondas EN_CURSO. */
    @Query("""
        SELECT v FROM VerificacionPresencia v
        WHERE v.interno.idInterno = :idInterno
          AND v.estadoVerificacion = 'PENDIENTE'
          AND v.rondaVerificacion.estado = 'EN_CURSO'
        """)
    List<VerificacionPresencia> findPendientesDeInterno(@Param("idInterno") Long idInterno);

    @Query("""
        SELECT v FROM VerificacionPresencia v
        WHERE v.titular.idTitular = :idTitular
          AND v.estadoVerificacion = 'PENDIENTE'
          AND v.rondaVerificacion.estado = 'EN_CURSO'
        """)
    List<VerificacionPresencia> findPendientesDeTitular(@Param("idTitular") Long idTitular);

    /** Count por estado para una ronda. */
    @Query("""
        SELECT v.estadoVerificacion, COUNT(v) FROM VerificacionPresencia v
        WHERE v.rondaVerificacion.idRondaVerificacion = :idRonda
        GROUP BY v.estadoVerificacion
        """)
    List<Object[]> countByEstadoForRonda(@Param("idRonda") Long idRonda);
}
