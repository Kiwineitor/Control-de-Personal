package com.uap.controlMedico.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uap.controlMedico.model.entity.BitacoraEntrada;

public interface BitacoraEntradaDao extends JpaRepository<BitacoraEntrada, Long> {

    @Query("""
        SELECT b FROM BitacoraEntrada b
        JOIN FETCH b.titularJornada tj
        JOIN FETCH tj.titular t
        JOIN FETCH t.persona
        WHERE tj.titular.idTitular = :idTitular
        ORDER BY b.fechaHoraEvento DESC
        """)
    List<BitacoraEntrada> findByTitular(@Param("idTitular") Long idTitular);

    @Query("""
        SELECT b FROM BitacoraEntrada b
        JOIN FETCH b.titularJornada tj
        JOIN FETCH tj.titular t
        JOIN FETCH t.persona
        WHERE tj.titular.idTitular = :idTitular
        ORDER BY b.fechaHoraEvento DESC
        """)
    List<BitacoraEntrada> findRecientesByTitular(@Param("idTitular") Long idTitular,
                                                  org.springframework.data.domain.Pageable pageable);

    @Query("""
        SELECT b FROM BitacoraEntrada b
        JOIN FETCH b.titularJornada tj
        JOIN FETCH tj.titular t
        JOIN FETCH t.persona
        WHERE tj.idTitularJornada = :idTitularJornada
        ORDER BY b.fechaHoraEvento DESC
        """)
    List<BitacoraEntrada> findByTitularJornada(@Param("idTitularJornada") Long idTitularJornada);

    @Query("""
        SELECT b FROM BitacoraEntrada b
        JOIN FETCH b.titularJornada tj
        JOIN FETCH tj.titular t
        JOIN FETCH t.persona
        JOIN FETCH tj.jornada j
        WHERE j.idJornada = :idJornada
        ORDER BY b.fechaHoraEvento DESC
        """)
    List<BitacoraEntrada> findByJornadaId(@Param("idJornada") Long idJornada);
}
