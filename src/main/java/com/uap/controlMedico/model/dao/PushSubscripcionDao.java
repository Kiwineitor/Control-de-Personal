package com.uap.controlMedico.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uap.controlMedico.model.entity.PushSubscripcion;

public interface PushSubscripcionDao extends JpaRepository<PushSubscripcion, Long> {

    List<PushSubscripcion> findByUsuario_Username(String username);

    List<PushSubscripcion> findByUsuario_IdUsuario(Long idUsuario);

    Optional<PushSubscripcion> findByEndpoint(String endpoint);

    boolean existsByEndpoint(String endpoint);

    void deleteByEndpoint(String endpoint);

    /**
     * Alta/actualización atómica de la suscripción de UN dispositivo.
     *
     * El endpoint (token FCM o URL Web Push) identifica al dispositivo, no al usuario,
     * así que la fila es única por dispositivo y debe **reasignarse** al usuario que
     * inicia sesión — de lo contrario el dispositivo seguiría recibiendo los push del
     * usuario anterior tras un cambio de cuenta.
     *
     * Se resuelve en una sola sentencia (ON CONFLICT) y no con leer-luego-guardar:
     * la app dispara varios `PushNotifications.register()` en paralelo (arranque,
     * concesión de permiso, vuelta a primer plano) y cada uno reenvía el MISMO token.
     * Con SELECT + INSERT ambas peticiones veían la tabla vacía e insertaban, y una
     * moría con `duplicate key value violates unique constraint "idx_push_endpoint"`.
     *
     * `fecha_creacion` no se toca en el UPDATE: es la fecha de alta del dispositivo.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO push_subscripcion
            (id_usuario, platform, endpoint, p256dh, auth_key, user_agent, fecha_creacion)
        VALUES
            (:idUsuario, :platform, :endpoint, :p256dh, :authKey, :userAgent, NOW())
        ON CONFLICT (endpoint) DO UPDATE SET
            id_usuario = EXCLUDED.id_usuario,
            platform   = EXCLUDED.platform,
            p256dh     = EXCLUDED.p256dh,
            auth_key   = EXCLUDED.auth_key,
            user_agent = EXCLUDED.user_agent
        """, nativeQuery = true)
    void upsertSubscripcion(@Param("idUsuario") Long   idUsuario,
                            @Param("platform")  String platform,
                            @Param("endpoint")  String endpoint,
                            @Param("p256dh")    String p256dh,
                            @Param("authKey")   String authKey,
                            @Param("userAgent") String userAgent);
}
