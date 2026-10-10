package com.uap.controlMedico.model.dao;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.uap.controlMedico.model.entity.Interno;
import com.uap.controlMedico.model.entity.ResponsableInterno;
import com.uap.controlMedico.model.entity.Titular;

@Repository
public interface ResponsableInternoDao extends JpaRepository<ResponsableInterno, Long> {

 
    Optional<ResponsableInterno> findByTitularAndInternoAndActivoTrue(
            Titular titular, Interno interno);
 
    boolean existsByTitularAndInternoAndActivoTrue(Titular titular, Interno interno);
 
    // ── Gestión del responsable activo ─────────────────────────────────
 
    /**
     * Responsable activo actual de un interno.
     * Un interno debería tener solo uno activo a la vez.
     */
    @Query("""
        SELECT ri FROM ResponsableInterno ri
        WHERE ri.interno.idInterno = :idInterno
          AND ri.activo = true
        ORDER BY ri.fechaAsignacion DESC
        """)
    List<ResponsableInterno> findActivosByInterno(@Param("idInterno") Long idInterno);
 
    /**
     * Historial completo de responsables de un interno
     * (activos e inactivos, ordenados por fecha).
     */
    @Query("""
        SELECT ri FROM ResponsableInterno ri
        WHERE ri.interno.idInterno = :idInterno
        ORDER BY ri.fechaAsignacion DESC
        """)
    List<ResponsableInterno> findHistorialByInterno(@Param("idInterno") Long idInterno);
 
    /** Cuenta internos actualmente bajo supervisión de un titular. */
    @Query("""
        SELECT COUNT(ri) FROM ResponsableInterno ri
        WHERE ri.titular.idTitular = :idTitular
          AND ri.activo = true
        """)
    long countInternosActivosByTitular(@Param("idTitular") Long idTitular);
 
    /** Desactiva todas las asignaciones activas de un interno (al reasignar). */
    @Modifying
    @Query("""
        UPDATE ResponsableInterno ri
        SET ri.activo = false,
            ri.fechaFinAsignacion = :fechaFin
        WHERE ri.interno.idInterno = :idInterno
          AND ri.activo = true
        """)
    void desactivarAsignacionesDeInterno(
            @Param("idInterno") Long idInterno,
            @Param("fechaFin")  LocalDate fechaFin);

    /* PARA EL VUE PAPI */

    Optional<ResponsableInterno> findFirstByActivoTrue();

        /** Todas las asignaciones activas ordenadas por fecha más reciente. */
    List<ResponsableInterno> findByActivoTrueOrderByFechaAsignacionDesc();
 
    /** Historial completo ordenado por fecha creación DESC. */
    List<ResponsableInterno> findAllByOrderByFechaCreacionDesc();
 
    /** Asignación activa de un interno específico. */
    Optional<ResponsableInterno> findByInternoAndActivoTrue(Interno interno);
 
    /** Todas las asignaciones activas de un titular. */
    List<ResponsableInterno> findByTitularAndActivoTrue(Titular titular);


}
