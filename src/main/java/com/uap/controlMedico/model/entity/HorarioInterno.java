package com.uap.controlMedico.model.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Planificación de días de asistencia para un interno en una jornada.
 * Permite asignar días específicos y sobreescribir horas si el interno
 * asiste menos horas o en días alternados respecto a la jornada base.
 */
@Entity
@Table(
    name = "horario_interno",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_hi_interno_jornada_mes",
        columnNames = {"id_interno_jornada", "mes", "anio"}
    ),
    indexes = {
        @Index(name = "idx_hi_interno_jornada", columnList = "id_interno_jornada"),
        @Index(name = "idx_hi_mes_anio",        columnList = "mes, anio")
    }
)
@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
public class HorarioInterno {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idHorarioInterno;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_interno_jornada", nullable = false,
                foreignKey = @ForeignKey(name = "fk_hi_interno_jornada"))
    private InternoJornada internoJornada;

    @Column(nullable = false)
    private Integer mes;

    @Column(nullable = false)
    private Integer anio;

    @Column(length = 300)
    private String observaciones;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    @OneToMany(mappedBy = "horarioInterno", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<DiaHorario> dias = new ArrayList<>();

    @PrePersist
    private void prePersist() {
        if (fechaCreacion == null) fechaCreacion = LocalDateTime.now();
    }
}
