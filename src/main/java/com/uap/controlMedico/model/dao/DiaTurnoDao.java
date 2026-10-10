package com.uap.controlMedico.model.dao;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uap.controlMedico.model.entity.DiaTurno;

public interface DiaTurnoDao extends JpaRepository<DiaTurno, Long> {

    List<DiaTurno> findByRolTurno_IdRolTurnoOrderByFechaAsc(Long idRolTurno);

    void deleteByRolTurno_IdRolTurno(Long idRolTurno);

    /** Busca los DiaTurno de la fecha dada para todos los titulares activos en jornadas activas. */
    @Query("""
        SELECT d FROM DiaTurno d
        JOIN FETCH d.rolTurno r
        JOIN FETCH r.titularJornada tj
        JOIN FETCH tj.titular t
        JOIN FETCH t.persona
        JOIN FETCH tj.jornada j
        LEFT JOIN FETCH tj.lugar
        WHERE d.fecha = :fecha
          AND j.estado = 'ACTIVA'
          AND r.activo = true
        """)
    List<DiaTurno> findActivosByFecha(@Param("fecha") LocalDate fecha);
}
