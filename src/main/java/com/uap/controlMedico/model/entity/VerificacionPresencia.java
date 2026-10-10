package com.uap.controlMedico.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.uap.controlMedico.config.AuditoriaConfig;
import com.uap.controlMedico.model.enums.EstadoVerificacion;
import com.uap.controlMedico.model.enums.MetodoVerificacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
    name = "verificacion_presencia",
    indexes = {
        @Index(name = "idx_verif_ronda",   columnList = "id_ronda_verificacion"),
        @Index(name = "idx_verif_titular", columnList = "id_titular"),
        @Index(name = "idx_verif_interno", columnList = "id_interno"),
        @Index(name = "idx_verif_estado",  columnList = "estado_verificacion")
    }
)
@Getter @Setter @AllArgsConstructor @NoArgsConstructor @SuperBuilder
public class VerificacionPresencia extends AuditoriaConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idVerificacionPresencia;
 
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "id_ronda_verificacion",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_verif_ronda")
    )
    private RondaVerificacion rondaVerificacion;
 
    // ──────────────────────────────────────────────────────────────────
    //  SUJETO DE LA VERIFICACIÓN (uno de los dos, nunca ambos)
    // ──────────────────────────────────────────────────────────────────
 
    /** Titular verificado. Null si la verificación es para un Interno. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_titular",
                foreignKey = @ForeignKey(name = "fk_verif_titular"))
    private Titular titular;
 
    /** Interno verificado. Null si la verificación es para un Titular. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_interno",
                foreignKey = @ForeignKey(name = "fk_verif_interno"))
    private Interno interno;
 
    // ──────────────────────────────────────────────────────────────────
    //  ESTADO Y TIEMPOS
    // ──────────────────────────────────────────────────────────────────
 
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_verificacion", nullable = false, length = 20)
    @Builder.Default
    private EstadoVerificacion estadoVerificacion = EstadoVerificacion.PENDIENTE;
 
    /**
     * Cómo confirmó su presencia: GPS automático, QR del lugar,
     * foto con token, o confirmación manual del admin.
     * Null hasta que responda.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_verificacion", length = 20)
    private MetodoVerificacion metodoVerificacion;

    @Column(name = "fecha_hora_envio", nullable = false)
    private LocalDateTime fechaHoraEnvio;

    @Column(name = "fecha_hora_respuesta")
    private LocalDateTime fechaHoraRespuesta;

    @Column(name = "latitud", precision = 10, scale = 7)
    private BigDecimal latitud;
 
    @Column(name = "longitud", precision = 10, scale = 7)
    private BigDecimal longitud;
 
    /**
     * URL de la foto tomada al confirmar presencia (en servidor SMB).
     * Ejemplo: "uploads/permanencia/fotos/2024/05/verif_123.jpg"
     * La foto es opcional pero recomendada para evidencia.
     */
    @Column(name = "foto_url", length = 500)
    private String fotoUrl;
 
    @Column(length = 300)
    private String observaciones;
 
    // ──────────────────────────────────────────────────────────────────
    //  MÉTODOS DE DOMINIO
    // ──────────────────────────────────────────────────────────────────
 
    /**
     * Tiempo de respuesta en minutos desde el envío.
     * Útil para medir qué tan rápido responde el personal.
     * Retorna null si aún no hay respuesta.
     */
    @Transient
    public Long getTiempoRespuestaMinutos() {
        if (fechaHoraEnvio == null || fechaHoraRespuesta == null) return null;
        return java.time.Duration.between(fechaHoraEnvio, fechaHoraRespuesta).toMinutes();
    }
 
    /** Nombre para mostrar de quien fue verificado. */
    @Transient
    public String getNombrePersonal() {
        if (titular != null) return titular.getPersona().getNombreCompleto();
        if (interno  != null) return interno.getPersona().getNombreCompleto();
        return "Desconocido";
    }
 
    /** Tipo de personal para mostrar en reportes. */
    @Transient
    public String getTipoPersonal() {
        if (titular != null) return "TITULAR";
        if (interno  != null) return "INTERNO";
        return "N/A";
    }

}
