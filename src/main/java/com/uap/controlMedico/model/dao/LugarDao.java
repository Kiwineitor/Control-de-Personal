package com.uap.controlMedico.model.dao;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uap.controlMedico.model.entity.Lugar;
import com.uap.controlMedico.model.enums.EstadoAsignacion;
import com.uap.controlMedico.model.enums.TipoLugar;

public interface LugarDao extends JpaRepository<Lugar, Long> {
    List<Lugar> findByActivoTrue();
 
    List<Lugar> findByTipoLugarAndActivoTrue(TipoLugar tipoLugar);
 
    List<Lugar> findByNombreContainingIgnoreCaseAndActivoTrue(String nombre);
 
    @Query("""
        SELECT l FROM Lugar l
        WHERE l.activo = true
          AND l.idLugar NOT IN (
              SELECT ij.lugar.idLugar
              FROM InternoJornada ij
              JOIN ij.jornada j
              WHERE j.fechaInicio <= :fecha
                AND j.fechaFin    >= :fecha
                AND ij.estado     != :estadoCancelado
          )
          AND l.idLugar NOT IN (
              SELECT tj.lugar.idLugar
              FROM TitularJornada tj
              JOIN tj.jornada j
              WHERE j.fechaInicio <= :fecha
                AND j.fechaFin    >= :fecha
                AND tj.estado     != :estadoCancelado
          )
        ORDER BY l.nombre
        """)
    List<Lugar> findDisponiblesEnFecha(
            @Param("fecha")           LocalDate fecha,
            @Param("estadoCancelado") EstadoAsignacion estadoCancelado);

    List<Lugar> findByActivoTrueOrderByNombreAsc();

    boolean existsByNombreIgnoreCase(String nombre);

    /** Para select de formularios — solo activos ordenados */
    List<Lugar> findByActivoTrueOrderByNombre();

    /** Grid paginado con filtros (Decision #4: String nunca null) */
    @Query(value = """
        SELECT l FROM Lugar l
        WHERE (:nombre = '' OR LOWER(l.nombre) LIKE LOWER(CONCAT('%', :nombre, '%')))
          AND (:activo IS NULL OR l.activo = :activo)
        """,
        countQuery = """
        SELECT COUNT(l) FROM Lugar l
        WHERE (:nombre = '' OR LOWER(l.nombre) LIKE LOWER(CONCAT('%', :nombre, '%')))
          AND (:activo IS NULL OR l.activo = :activo)
        """)
    Page<Lugar> findWithFilters(
        @Param("nombre") String  nombre,
        @Param("activo") Boolean activo,
        Pageable pageable
    );

    /** Cuenta cuántos lugares tienen geofencing configurado */
    @Query("""
        SELECT COUNT(l) FROM Lugar l
        WHERE l.activo = true
          AND l.latitudCentro  IS NOT NULL
          AND l.longitudCentro IS NOT NULL
          AND l.radioMetros    IS NOT NULL
        """)
    long countConGeofencing();
}
