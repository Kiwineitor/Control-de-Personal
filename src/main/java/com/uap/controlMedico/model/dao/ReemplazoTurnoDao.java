package com.uap.controlMedico.model.dao;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uap.controlMedico.model.entity.ReemplazoTurno;

public interface ReemplazoTurnoDao extends JpaRepository<ReemplazoTurno, Long> {

    List<ReemplazoTurno> findByJornadaIdJornadaOrderByFechaReemplazoAsc(Long idJornada);

    List<ReemplazoTurno> findByJornadaIdJornadaAndFechaReemplazo(Long idJornada, LocalDate fecha);

    Optional<ReemplazoTurno> findByJornadaIdJornadaAndFechaReemplazoAndOriginalIdTitular(
        Long idJornada, LocalDate fecha, Long idOriginal);

    boolean existsByJornadaIdJornadaAndFechaReemplazoAndOriginalIdTitular(
        Long idJornada, LocalDate fecha, Long idOriginal);

    @Query("""
        SELECT r FROM ReemplazoTurno r
        JOIN FETCH r.original o
        JOIN FETCH o.persona
        JOIN FETCH r.reemplazante re
        JOIN FETCH re.persona
        LEFT JOIN FETCH r.autorizadoPor
        WHERE r.jornada.idJornada = :idJornada
        ORDER BY r.fechaReemplazo ASC
        """)
    List<ReemplazoTurno> findConDetalleByJornada(@Param("idJornada") Long idJornada);
}
