package com.uap.controlMedico.model.entity;

import java.time.LocalDate;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
    name = "reemplazo_turno",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_reemplazo_jornada_fecha_original",
        columnNames = {"id_jornada", "fecha_reemplazo", "id_original"}
    ),
    indexes = {
        @Index(name = "idx_reemp_jornada", columnList = "id_jornada"),
        @Index(name = "idx_reemp_fecha",   columnList = "fecha_reemplazo"),
        @Index(name = "idx_reemp_estado",  columnList = "estado")
    }
)
@Getter @Setter @AllArgsConstructor @NoArgsConstructor @SuperBuilder
public class ReemplazoTurno extends AuditoriaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idReemplazo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "id_jornada",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_reemp_jornada")
    )
    private Jornada jornada;

    @Column(name = "fecha_reemplazo", nullable = false)
    private LocalDate fechaReemplazo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "id_original",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_reemp_original")
    )
    private Titular original;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "id_reemplazante",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_reemp_reemplazo")
    )
    private Titular reemplazante;

    @Column(name = "motivo", nullable = false, length = 500)
    private String motivo;

    @Column(name = "estado", nullable = false, length = 20)
    @Builder.Default
    private String estado = "PENDIENTE";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "autorizado_por",
        nullable = true,
        foreignKey = @ForeignKey(name = "fk_reemp_usuario")
    )
    private Usuario autorizadoPor;

    @Column(name = "fecha_autorizacion")
    private LocalDateTime fechaAutorizacion;

    public boolean isPendiente()   { return "PENDIENTE".equals(estado); }
    public boolean isAprobado()    { return "APROBADO".equals(estado); }
    public boolean isRechazado()   { return "RECHAZADO".equals(estado); }
}
