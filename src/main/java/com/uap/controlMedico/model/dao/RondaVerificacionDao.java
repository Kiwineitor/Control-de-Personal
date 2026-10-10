package com.uap.controlMedico.model.dao;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uap.controlMedico.model.entity.RondaVerificacion;
import com.uap.controlMedico.model.enums.EstadoRonda;
import com.uap.controlMedico.model.enums.TipoRonda;

public interface RondaVerificacionDao extends JpaRepository<RondaVerificacion, Long> {
    
    List<RondaVerificacion> findByTipoRonda(TipoRonda tipoRonda);
 
    long countByEstado(EstadoRonda estado);
 
    // ── CORREGIDO: enums como @Param ──────────────────────────────────
 
    @Query("""
        SELECT r FROM RondaVerificacion r
        WHERE r.estado = :estado
        ORDER BY r.fechaHoraInicio ASC
        """)
    List<RondaVerificacion> findEnCurso(@Param("estado") EstadoRonda estado);
 
    @Query("""
        SELECT r FROM RondaVerificacion r
        WHERE CAST(r.fechaHoraInicio AS date) = :fecha
        ORDER BY r.fechaHoraInicio DESC
        """)
    List<RondaVerificacion> findByFecha(@Param("fecha") LocalDate fecha);
 
    @Query("""
        SELECT r FROM RondaVerificacion r
        WHERE r.fechaHoraInicio >= :desde
          AND r.fechaHoraInicio <= :hasta
        ORDER BY r.fechaHoraInicio DESC
        """)
    List<RondaVerificacion> findEnRango(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);
 
    @Query("""
        SELECT r FROM RondaVerificacion r
        WHERE r.estado = :estado
          AND r.fechaHoraInicio <= :limite
        """)
    List<RondaVerificacion> findVencidasAntesDe(
            @Param("limite") LocalDateTime limite,
            @Param("estado") EstadoRonda estado);


    @Query(value = """
        SELECT r FROM RondaVerificacion r
        WHERE (:q = '' OR LOWER(COALESCE(r.nombre,'')) LIKE LOWER(CONCAT('%',:q,'%')))
          AND (:estado IS NULL OR r.estado = :estado)
          AND (:fecha  IS NULL OR CAST(r.fechaHoraInicio AS date) = :fecha)
        ORDER BY r.fechaHoraInicio DESC
        """,
        countQuery = """
        SELECT COUNT(r) FROM RondaVerificacion r
        WHERE (:q = '' OR LOWER(COALESCE(r.nombre,'')) LIKE LOWER(CONCAT('%',:q,'%')))
          AND (:estado IS NULL OR r.estado = :estado)
          AND (:fecha  IS NULL OR CAST(r.fechaHoraInicio AS date) = :fecha)
        """
    )
    Page<RondaVerificacion> findConFiltros(
        @Param("q")      String q,
        @Param("estado") EstadoRonda estado,
        @Param("fecha")  LocalDate fecha,
        Pageable pageable
    );

    List<RondaVerificacion> findByEstadoOrderByFechaHoraInicioDesc(EstadoRonda estado);
}
