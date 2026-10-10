package com.uap.controlMedico.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uap.controlMedico.model.entity.AusenciaNoJustificada;

public interface AusenciaNoJustificadaDao extends JpaRepository<AusenciaNoJustificada, Long>{

    Optional<AusenciaNoJustificada> findByUsuario_IdUsuarioAndJornada_IdJornadaAndResueltaFalse(
        Long idUsuario, Long idJornada);

    /** Ausencias de una jornada con datos del usuario, más recientes primero. */
    @Query("""
        SELECT a FROM AusenciaNoJustificada a
        LEFT JOIN FETCH a.usuario u
        LEFT JOIN FETCH u.persona
        WHERE a.jornada.idJornada = :idJornada
        ORDER BY a.inicioAusencia DESC
        """)
    List<AusenciaNoJustificada> findByJornadaId(@Param("idJornada") Long idJornada);
}
