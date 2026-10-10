package com.uap.controlMedico.model.entity;

import java.time.LocalDateTime;

import com.uap.controlMedico.config.AuditoriaConfig;

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
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
    name = "notificacion",
    indexes = {
        @Index(name = "idx_notif_dest",      columnList = "id_destinatario"),
        @Index(name = "idx_notif_timestamp", columnList = "timestamp"),
        @Index(name = "idx_notif_global",    columnList = "es_global"),
        @Index(name = "idx_notif_leida",     columnList = "leida")
    }
)
@Getter @Setter @NoArgsConstructor @SuperBuilder
public class Notificacion extends AuditoriaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idNotificacion;

    /**
     * null → notificación global (visible para todos los usuarios).
     * not null → notificación personal (solo el destinatario la ve).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_destinatario",
                foreignKey = @ForeignKey(name = "fk_notif_destinatario"))
    private Usuario destinatario;

    /** null → notificación automática del sistema. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_remitente",
                foreignKey = @ForeignKey(name = "fk_notif_remitente"))
    private Usuario remitente;

    /** ACTIVIDAD_ASIGNADA | JORNADA_ASIGNADA | PERMISO_APROBADO |
     *  PERMISO_RECHAZADO | CUSTOM | BROADCAST */
    @Column(nullable = false, length = 60)
    private String tipo;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String mensaje;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    /** Solo significativo para notificaciones personales. */
    @Column(nullable = false)
    @Builder.Default
    private boolean leida = false;

    @Column
    private LocalDateTime fechaLectura;

    @Column(name = "id_actividad_jornada")
    private Long idActividadJornada;

    @Column(name = "id_jornada")
    private Long idJornada;

    @Column(name = "id_solicitud")
    private Long idSolicitud;

    /** true → fue enviada a todos los usuarios vía broadcast. */
    @Column(name = "es_global", nullable = false)
    @Builder.Default
    private boolean esGlobal = false;
}