package com.uap.controlMedico.model.dao;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uap.controlMedico.model.entity.SolicitudPermiso;
import com.uap.controlMedico.model.enums.EstadoPermiso;
import com.uap.controlMedico.model.enums.EstadoSolicitud;
import com.uap.controlMedico.model.enums.TipoPermiso;

public interface SolicitudPermisoDao extends JpaRepository<SolicitudPermiso, Long> {
    List<SolicitudPermiso> findByTitular_IdTitularOrderByFechaCreacionDesc(Long idTitular);
 
    @Query(
    value = """
            SELECT DISTINCT s FROM SolicitudPermiso s
            LEFT JOIN s.jornada           j
            LEFT JOIN s.titularJornada    tj
            LEFT JOIN tj.jornada          tjj
            WHERE (:idTitular   IS NULL OR s.titular.idTitular = :idTitular)
            AND (:idJornada   IS NULL OR j.idJornada = :idJornada
                                        OR tjj.idJornada = :idJornada)
            AND (:estado      IS NULL OR s.estadoSolicitud   = :estado)
            AND (:tipoPermiso IS NULL OR s.tipoPermiso       = :tipoPermiso)
            ORDER BY s.fechaHoraSolicitud DESC
            """,
        countQuery = """
            SELECT COUNT(DISTINCT s) FROM SolicitudPermiso s
            LEFT JOIN s.jornada           j
            LEFT JOIN s.titularJornada    tj
            LEFT JOIN tj.jornada          tjj
            WHERE (:idTitular   IS NULL OR s.titular.idTitular = :idTitular)
            AND (:idJornada   IS NULL OR j.idJornada = :idJornada
                                        OR tjj.idJornada = :idJornada)
            AND (:estado      IS NULL OR s.estadoSolicitud   = :estado)
            AND (:tipoPermiso IS NULL OR s.tipoPermiso       = :tipoPermiso)
            """
    )
    Page<SolicitudPermiso> findWithFilters(
        @Param("idTitular")   Long idTitular,
        @Param("idJornada")   Long idJornada,
        @Param("estado")      EstadoSolicitud estado,
        @Param("tipoPermiso") TipoPermiso tipoPermiso,
        Pageable pageable
    );

    @Query("SELECT s FROM SolicitudPermiso s WHERE s.estadoSolicitud = :estado ORDER BY s.fechaHoraSolicitud DESC")
    List<SolicitudPermiso> findByEstadoOrderByFecha(@Param("estado") EstadoSolicitud estado);

    @Query("SELECT s FROM SolicitudPermiso s WHERE s.titular.idTitular = :idTitular ORDER BY s.fechaHoraSolicitud DESC")
    List<SolicitudPermiso> findByTitular(@Param("idTitular") Long idTitular);

    @Query("SELECT COUNT(s) FROM SolicitudPermiso s WHERE s.estadoSolicitud = :estado")
    Long countByEstado(@Param("estado") EstadoSolicitud estado);

    boolean existsByTitular_IdTitularAndEstadoSolicitudAndFechaPermiso(
    Long idTitular, EstadoSolicitud estadoSolicitud, LocalDate fechaPermiso);
}
