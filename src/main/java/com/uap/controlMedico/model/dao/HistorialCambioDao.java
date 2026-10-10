package com.uap.controlMedico.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uap.controlMedico.model.entity.HistorialCambio;

public interface HistorialCambioDao extends JpaRepository<HistorialCambio, Long> {

    List<HistorialCambio> findByEntidadAndIdEntidadOrderByFechaCambioDesc(
        String entidad, Long idEntidad);

    @Query("""
        SELECT h FROM HistorialCambio h
        WHERE h.entidad IN ('TURNO', 'TITULAR_JORNADA', 'INTERNO_JORNADA')
          AND h.idEntidad IN :ids
        ORDER BY h.fechaCambio DESC
        """)
    List<HistorialCambio> findByJornadaIds(@Param("ids") List<Long> ids);
}
