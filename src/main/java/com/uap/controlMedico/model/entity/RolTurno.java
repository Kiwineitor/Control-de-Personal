package com.uap.controlMedico.model.entity;

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
 * Planificación mensual de turnos para un titular en una jornada.
 * Permite asignar días específicos del mes a un médico (ROL TURNO).
 * Un RolTurno = un titular + una jornada + mes/año + lista de días trabajados.
 */
@Entity
@Table(
    name = "rol_turno",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_rt_titular_jornada_mes",
        columnNames = {"id_titular_jornada", "mes", "anio"}
    ),
    indexes = {
        @Index(name = "idx_rt_titular_jornada", columnList = "id_titular_jornada"),
        @Index(name = "idx_rt_mes_anio",        columnList = "mes, anio")
    }
)
@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
public class RolTurno {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idRolTurno;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_titular_jornada", nullable = false,
                foreignKey = @ForeignKey(name = "fk_rt_titular_jornada"))
    private TitularJornada titularJornada;

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
    private java.time.LocalDateTime fechaCreacion;

    @OneToMany(mappedBy = "rolTurno", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<DiaTurno> dias = new ArrayList<>();

    @PrePersist
    private void prePersist() {
        if (fechaCreacion == null) fechaCreacion = java.time.LocalDateTime.now();
    }
}
