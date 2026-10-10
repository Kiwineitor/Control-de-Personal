package com.uap.controlMedico.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uap.controlMedico.model.entity.RolTurno;

public interface RolTurnoDao extends JpaRepository<RolTurno, Long> {

    Optional<RolTurno> findByTitularJornada_IdTitularJornadaAndMesAndAnio(
        Long idTitularJornada, Integer mes, Integer anio);

    List<RolTurno> findByTitularJornada_IdTitularJornadaAndActivoTrue(Long idTitularJornada);

    @Query("""
        SELECT r FROM RolTurno r
        JOIN FETCH r.titularJornada tj
        JOIN FETCH tj.titular t
        JOIN FETCH t.persona
        JOIN FETCH tj.jornada j
        LEFT JOIN FETCH r.dias
        WHERE tj.jornada.idJornada = :idJornada
          AND r.activo = true
        """)
    List<RolTurno> findByJornadaConDetalle(@Param("idJornada") Long idJornada);
}
