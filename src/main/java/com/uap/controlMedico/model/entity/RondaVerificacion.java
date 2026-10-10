package com.uap.controlMedico.model.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.uap.controlMedico.config.AuditoriaConfig;
import com.uap.controlMedico.model.enums.EstadoRonda;
import com.uap.controlMedico.model.enums.EstadoVerificacion;
import com.uap.controlMedico.model.enums.TipoRonda;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
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
    name = "ronda_verificacion",
    indexes = {
        @Index(name = "idx_ronda_fecha",  columnList = "fecha_hora_inicio"),
        @Index(name = "idx_ronda_estado", columnList = "estado")
    }
)
@Getter @Setter @AllArgsConstructor @NoArgsConstructor @SuperBuilder
public class RondaVerificacion extends AuditoriaConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idRondaVerificacion;
 
    @Column(length = 200)
    private String nombre;
 
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_ronda", nullable = false, length = 20)
    @Builder.Default
    private TipoRonda tipoRonda = TipoRonda.ALEATORIA;
 
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EstadoRonda estado = EstadoRonda.PENDIENTE;
 
    /** Momento en que se lanzó/inició la ronda. */
    @Column(name = "fecha_hora_inicio", nullable = false)
    private LocalDateTime fechaHoraInicio;
 
    /**
     * Momento en que se cerró la ronda (todas las verificaciones procesadas
     * o el timeout global venció).
     */
    @Column(name = "fecha_hora_fin")
    private LocalDateTime fechaHoraFin;
 
    /** Minutos que tiene el personal para responder (override del default de config). */
    @Column(name = "timeout_minutos")
    @Builder.Default
    private Integer timeoutMinutos = 10;
 
    /**
     * Usuario (supervisor/admin) que creó la ronda.
     * Null si fue generada automáticamente por el sistema.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_creada_por",
                foreignKey = @ForeignKey(name = "fk_ronda_usuario"))
    private Usuario creadaPor;
 
    @Column(length = 300)
    private String observaciones;
 
    /** Verificaciones individuales generadas para esta ronda. */
    @OneToMany(
        fetch = FetchType.LAZY,
        mappedBy = "rondaVerificacion",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    @Builder.Default
    private List<VerificacionPresencia> verificaciones = new ArrayList<>();
 
    // ──────────────────────────────────────────────────────────────────
    //  MÉTODOS DE DOMINIO
    // ──────────────────────────────────────────────────────────────────
 
    @Transient
    public long getTotalVerificaciones() {
        return verificaciones.size();
    }
 
    @Transient
    public long getTotalPresentes() {
        return verificaciones.stream()
            .filter(v -> EstadoVerificacion.PRESENTE
                .equals(v.getEstadoVerificacion()))
            .count();
    }
 
    @Transient
    public long getTotalSinRespuesta() {
        return verificaciones.stream()
            .filter(v -> EstadoVerificacion.SIN_RESPUESTA
                .equals(v.getEstadoVerificacion()))
            .count();
    }

}
