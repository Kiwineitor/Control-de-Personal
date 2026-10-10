package com.uap.controlMedico.model.entity;

import java.time.LocalDateTime;
import java.time.LocalTime;

import com.uap.controlMedico.config.AuditoriaConfig;
import com.uap.controlMedico.model.enums.EstadoActividad;

import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
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
    name = "actividad_jornada",
    indexes = {
        @Index(name = "idx_aj_interno_jornada",     columnList = "id_interno_jornada"),
        @Index(name = "idx_aj_titular_jornada",     columnList = "id_titular_jornada"),
        @Index(name = "idx_act_jornada_actividad",  columnList = "id_actividad"),
        @Index(name = "idx_act_jornada_estado",     columnList = "estado_actividad")
    }
)
@Getter @Setter @AllArgsConstructor @NoArgsConstructor @SuperBuilder
public class ActividadJornada extends AuditoriaConfig{
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idActividadJornada;
 
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "id_interno_jornada",
        nullable = true,
        foreignKey = @ForeignKey(name = "fk_aj_interno_jornada")
    )
    private InternoJornada internoJornada;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "id_titular_jornada",
        nullable = true,
        foreignKey = @ForeignKey(name = "fk_aj_titular_jornada")
    )
    private TitularJornada titularJornada;
 
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "id_actividad",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_aj_actividad")
    )
    private Actividad actividad;
 
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_actividad", nullable = false, length = 20)
    @Builder.Default
    private EstadoActividad estadoActividad = EstadoActividad.PENDIENTE;
 
    @Column(name = "hora_inicio")
    private LocalTime horaInicio;
 
    @Column(name = "hora_fin")
    private LocalTime horaFin;
 
    @Column(name = "evidencia_url", length = 500)
    private String evidenciaUrl;

    @Column(length = 500)
    private String observaciones;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "id_validado_por",
        nullable = true,
        foreignKey = @ForeignKey(name = "fk_aj_validado_por")
    )
    private Titular validadoPor;

    @Column(name = "fecha_validacion")
    private LocalDateTime fechaValidacion;

    @Column(name = "culminada", nullable = false)
    @Builder.Default
    private boolean culminada = false;

    @Column(name = "fecha_culminacion")
    private LocalDateTime fechaCulminacion;

    /**
     * Auto-referencia: si esta actividad fue creada por carry-over,
     * apunta a la actividad original de la jornada anterior.
     * FK gestionada en SQL (fk_aj_carry_over_de). Hibernate no la recrea.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name          = "carry_over_de",
        nullable      = true,
        foreignKey    = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    private ActividadJornada carryOverDe;

    @Transient
    public boolean esCarryOver() { return carryOverDe != null; }

    // Helpers

    @Transient
    public boolean esDeInterno()  { return internoJornada  != null; }

    @Transient
    public boolean esDeTitular()  { return titularJornada  != null; }

    @Transient
    public boolean estaValidada() { return validadoPor != null && fechaValidacion != null; }

}