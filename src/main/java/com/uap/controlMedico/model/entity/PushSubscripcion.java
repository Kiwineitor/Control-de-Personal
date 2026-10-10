package com.uap.controlMedico.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Suscripción Web Push de un usuario.
 * Un usuario puede tener varias (distintos navegadores/dispositivos).
 */
@Entity
@Table(
    name = "push_subscripcion",
    indexes = {
        @Index(name = "idx_push_usuario",   columnList = "id_usuario"),
        @Index(name = "idx_push_endpoint",  columnList = "endpoint", unique = true),
    }
)
@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
public class PushSubscripcion {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false,
                foreignKey = @ForeignKey(name = "fk_push_usuario"))
    private Usuario usuario;

    /**
     * Canal de entrega:
     *  - "WEBPUSH" → suscripción Web Push (navegador, requiere p256dh/auth + VAPID).
     *  - "FCM"     → token de dispositivo nativo Android/iOS (Firebase Cloud Messaging).
     */
    @Column(name = "platform", length = 20)
    private String platform;

    /**
     * Para WEBPUSH: URL de entrega del proveedor (Mozilla, etc.).
     * Para FCM: token de registro del dispositivo.
     */
    @Column(nullable = false, length = 512)
    private String endpoint;

    /** Clave pública del cliente — base64url, 65 bytes (ECDH P-256). Vacío en FCM. */
    @Column(name = "p256dh", nullable = false, length = 256)
    private String p256dh;

    /** Secreto de autenticación — base64url, 16 bytes. Vacío en FCM. */
    @Column(name = "auth_key", nullable = false, length = 64)
    private String authKey;

    /** User-Agent del navegador/dispositivo (informativo). */
    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @PrePersist
    private void prePersist() {
        if (fechaCreacion == null) fechaCreacion = LocalDateTime.now();
    }
}
