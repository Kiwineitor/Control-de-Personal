package com.uap.controlMedico.model.dao;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.uap.controlMedico.model.entity.Notificacion;

@Repository
public interface NotificacionDao extends JpaRepository<Notificacion, Long> {

    /** Notificaciones visibles para el usuario: las suyas + las globales. */
    @Query("""
        SELECT n FROM Notificacion n
        WHERE n.destinatario.idUsuario = :idUsuario OR n.esGlobal = true
        ORDER BY n.timestamp DESC
        """)
    Page<Notificacion> findMisNotificaciones(
        @Param("idUsuario") Long idUsuario,
        Pageable pageable
    );

    /** Cuenta no leídas personales del usuario (globales no cuentan). */
    @Query("""
        SELECT COUNT(n) FROM Notificacion n
        WHERE n.destinatario.idUsuario = :idUsuario AND n.leida = false
        """)
    long countNoLeidasPersonales(@Param("idUsuario") Long idUsuario);

    /** Marca todas las notificaciones personales del usuario como leídas. */
    @Modifying
    @Query("""
        UPDATE Notificacion n
        SET n.leida = true, n.fechaLectura = CURRENT_TIMESTAMP
        WHERE n.destinatario.idUsuario = :idUsuario AND n.leida = false
        """)
    int marcarTodasLeidasUsuario(@Param("idUsuario") Long idUsuario);

    /**
     * Notificaciones PERSONALES enviadas a un usuario, recientes primero.
     *
     * A diferencia de {@link #findMisNotificaciones}, excluye las globales: acá el
     * admin quiere ver qué se le mandó a esa persona en concreto, y mezclar los
     * broadcast a todo el hospital solo escondería lo puntual.
     */
    @Query("""
        SELECT n FROM Notificacion n
        WHERE n.destinatario.idUsuario = :idUsuario AND n.esGlobal = false
        ORDER BY n.timestamp DESC
        """)
    List<Notificacion> findEnviadasAUsuario(
        @Param("idUsuario") Long idUsuario,
        Pageable pageable
    );
}