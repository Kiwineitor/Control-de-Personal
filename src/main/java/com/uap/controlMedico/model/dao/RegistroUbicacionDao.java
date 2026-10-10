package com.uap.controlMedico.model.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uap.controlMedico.model.entity.RegistroUbicacion;
import com.uap.controlMedico.model.enums.TipoRegistroUbicacion;

public interface RegistroUbicacionDao extends JpaRepository<RegistroUbicacion, Long> {

    Optional<RegistroUbicacion> findTopByUsuario_IdUsuarioOrderByTimestampRegistroDesc(Long idUsuario);

    /** Idempotencia de lotes: ¿ya se guardó un registro con este id de cliente? */
    boolean existsByClientEventId(String clientEventId);

    /** Último registro del usuario, usado para calcular delta de movimiento y snapshots. */
    Optional<RegistroUbicacion> findTopByUsuario_IdUsuarioAndTipoRegistroOrderByTimestampRegistroDesc(
        Long idUsuario, TipoRegistroUbicacion tipoRegistro);

    /** Historial de ubicaciones de un usuario en un rango de tiempo. */
    @Query("""
        SELECT r FROM RegistroUbicacion r
        WHERE r.usuario.idUsuario = :idUsuario
          AND r.timestampRegistro BETWEEN :desde AND :hasta
        ORDER BY r.timestampRegistro ASC
        """)
    List<RegistroUbicacion> findHistorialUsuario(
        @Param("idUsuario") Long idUsuario,
        @Param("desde")     LocalDateTime desde,
        @Param("hasta")     LocalDateTime hasta);
}
