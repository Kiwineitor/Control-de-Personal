package com.uap.controlMedico.model.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uap.controlMedico.model.entity.RondaVerificacion;
import com.uap.controlMedico.model.entity.VerificacionPresencia;
import com.uap.controlMedico.model.enums.EstadoVerificacion;

public interface VerificacionDao extends JpaRepository<VerificacionPresencia, Long> {
    
        List<VerificacionPresencia> findByRondaVerificacion(RondaVerificacion ronda);
    List<VerificacionPresencia> findByRondaVerificacion_IdRondaVerificacion(Long idRonda);
    List<VerificacionPresencia> findByTitular_IdTitular(Long idTitular);
    List<VerificacionPresencia> findByInterno_IdInterno(Long idInterno);
 
    Optional<VerificacionPresencia>
    findByRondaVerificacionAndTitular_IdTitularAndEstadoVerificacion(
            RondaVerificacion ronda, Long idTitular, EstadoVerificacion estado);
 
    Optional<VerificacionPresencia>
    findByRondaVerificacionAndInterno_IdInternoAndEstadoVerificacion(
            RondaVerificacion ronda, Long idInterno, EstadoVerificacion estado);
 
    @Query("""
        SELECT v FROM VerificacionPresencia v
        WHERE v.estadoVerificacion = :estado
          AND v.fechaHoraEnvio    <= :limite
        """)
    List<VerificacionPresencia> findPendientesVencidas(
            @Param("limite") LocalDateTime limite,
            @Param("estado") EstadoVerificacion estado);
 
    @Modifying
    @Query("""
        UPDATE VerificacionPresencia v
        SET v.estadoVerificacion = :nuevoEstado
        WHERE v.rondaVerificacion.idRondaVerificacion = :idRonda
          AND v.estadoVerificacion = :estadoActual
        """)
    int marcarSinRespuestaByRonda(
            @Param("idRonda")      Long idRonda,
            @Param("estadoActual") EstadoVerificacion estadoActual,
            @Param("nuevoEstado")  EstadoVerificacion nuevoEstado);
 
    @Query("""
        SELECT COUNT(v) FROM VerificacionPresencia v
        WHERE v.rondaVerificacion.idRondaVerificacion = :idRonda
          AND v.estadoVerificacion = :estado
        """)
    long countByRondaAndEstado(
            @Param("idRonda") Long idRonda,
            @Param("estado")  EstadoVerificacion estado);
 
    /** FIX: OR explícito */
    @Query("""
        SELECT v FROM VerificacionPresencia v
        WHERE v.titular.idTitular = :idTitular
          AND (v.estadoVerificacion = :estadoAusente
            OR v.estadoVerificacion = :estadoSinRespuesta)
          AND v.fechaHoraEnvio >= :desde
        ORDER BY v.fechaHoraEnvio DESC
        """)
    List<VerificacionPresencia> findIncidenciasDeTitular(
            @Param("idTitular")          Long idTitular,
            @Param("desde")              LocalDateTime desde,
            @Param("estadoAusente")      EstadoVerificacion estadoAusente,
            @Param("estadoSinRespuesta") EstadoVerificacion estadoSinRespuesta);

}
