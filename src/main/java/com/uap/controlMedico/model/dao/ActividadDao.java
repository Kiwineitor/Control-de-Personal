package com.uap.controlMedico.model.dao;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uap.controlMedico.model.entity.Actividad;
import com.uap.controlMedico.model.enums.EstadoCatalogo;
import com.uap.controlMedico.model.enums.TipoActividad;

public interface ActividadDao extends JpaRepository<Actividad, Long>{
    @Query("SELECT a FROM Actividad a WHERE a.estado != 'ELIMINADO'")
    List<Actividad> listarActividades();

    @Query("SELECT a FROM Actividad a WHERE a.estado != 'ELIMINADO' ORDER BY a.fechaCreacion DESC")
    List<Actividad> listarActividadesPorOrdenRegistroDESC();

    List<Actividad> findByActivoTrue();
 
    List<Actividad> findByNombreContainingIgnoreCaseAndActivoTrue(String nombre);
 
    /**
     * Actividades más utilizadas en jornadas (para sugerencias al crear jornadas).
     */
    @Query("""
        SELECT a FROM Actividad a
        WHERE a.activo = true
        ORDER BY (
            SELECT COUNT(aj) FROM ActividadJornada aj
            WHERE aj.actividad = a
        ) DESC
        """)
    List<Actividad> findMasUsadas();

    /** Lista paginada con filtros. q='' desactiva el filtro por nombre. */
    @Query(value = """
        SELECT a FROM Actividad a
        WHERE a.estado != com.uap.controlMedico.model.enums.EstadoCatalogo.ELIMINADO
          AND (:q      = '' OR LOWER(a.nombre) LIKE LOWER(CONCAT('%', :q, '%')))
          AND (:tipo   IS NULL OR a.tipoActividad = :tipo)
          AND (:estado IS NULL OR a.estado = :estado)
        ORDER BY a.nombre ASC
        """,
        countQuery = """
        SELECT COUNT(a) FROM Actividad a
        WHERE a.estado != com.uap.controlMedico.model.enums.EstadoCatalogo.ELIMINADO
          AND (:q      = '' OR LOWER(a.nombre) LIKE LOWER(CONCAT('%', :q, '%')))
          AND (:tipo   IS NULL OR a.tipoActividad = :tipo)
          AND (:estado IS NULL OR a.estado = :estado)
        """
    )
    Page<Actividad> findConFiltros(
        @Param("q")      String q,
        @Param("tipo")   TipoActividad tipo,
        @Param("estado") EstadoCatalogo estado,
        Pageable pageable
    );

    List<Actividad> findByActivoTrueOrderByNombreAsc();

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdActividadNot(String nombre, Long id);

    /** Top N actividades más usadas en jornadas — para sugerencias. */
    @Query("""
        SELECT a FROM Actividad a
        WHERE a.activo = true
        ORDER BY (
            SELECT COUNT(aj) FROM ActividadJornada aj WHERE aj.actividad = a
        ) DESC
        """)
    List<Actividad> findMasUsadas(Pageable pageable);

}
