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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
    name = "codigo_qr",
    indexes = {
        @Index(name = "idx_qr_codigo", columnList = "codigo"),
        @Index(name = "idx_qr_lugar", columnList = "id_lugar")
    }
)
@Getter @Setter @AllArgsConstructor @NoArgsConstructor @SuperBuilder
public class CodigoQr extends AuditoriaConfig{
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idCodigoQr;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "id_lugar",
        nullable = false,
        unique = true,
        foreignKey = @ForeignKey(name = "fk_codigo_qr_lugar")
    )
    private Lugar lugar;
 
    /**
     * UUID único que identifica el QR.
     * Es el contenido que se codifica en la imagen del QR.
     * Se genera con UUID.randomUUID() en el servicio.
     */
    @Column(nullable = false, unique = true, length = 36)
    private String codigo;
 
    /**
     * Si el QR puede ser usado actualmente.
     * Se desactiva al revocar o al dar de baja al interno.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
 
    /**
     * Fecha de expiración opcional.
     * Si es null → no expira (vigente mientras activo=true).
     * Si tiene valor → el sistema valida que la fecha actual sea anterior.
     * Útil para internos con rotaciones de duración definida.
     */
    @Column(name = "fecha_expiracion")
    private LocalDateTime fechaExpiracion;
 
    /**
     * Ruta a la imagen PNG del QR en el servidor SMB.
     * Ejemplo: "uploads/personal/qr/interno_123_qr.png"
     * Generada y almacenada por UtilidadesService al crear el CodigoQr.
     */
    @Column(name = "imagen_url", length = 500)
    private String imagenUrl;
 
    // ──────────────────────────────────────────────────────────────────
    //  MÉTODOS DE DOMINIO
    // ──────────────────────────────────────────────────────────────────
 
    /** Valida si el QR puede ser usado en este momento. */
    @Transient
    public boolean esValido() {
        if (!activo) return false;
        if (fechaExpiracion == null) return true;
        return LocalDateTime.now().isBefore(fechaExpiracion);
    }

}
