package com.uap.controlMedico.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uap.controlMedico.model.entity.HorarioInterno;

public interface HorarioInternoDao extends JpaRepository<HorarioInterno, Long> {

    Optional<HorarioInterno> findByInternoJornada_IdInternoJornadaAndMesAndAnio(
        Long idInternoJornada, Integer mes, Integer anio);

    List<HorarioInterno> findByInternoJornada_IdInternoJornadaAndActivoTrue(Long idInternoJornada);

    @Query("""
        SELECT h FROM HorarioInterno h
        JOIN FETCH h.internoJornada ij
        JOIN FETCH ij.interno i
        JOIN FETCH i.persona
        JOIN FETCH ij.jornada j
        LEFT JOIN FETCH h.dias
        WHERE ij.jornada.idJornada = :idJornada
          AND h.activo = true
        """)
    List<HorarioInterno> findByJornadaConDetalle(@Param("idJornada") Long idJornada);
}
