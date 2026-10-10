package com.uap.controlMedico.model.entity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.uap.controlMedico.config.AuditoriaConfig;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
    name = "interno",
    indexes = {
        @Index(name = "idx_interno_rotacion", columnList = "fecha_inicio_rotacion, fecha_fin_rotacion"),
        @Index(name = "idx_interno_persona", columnList = "id_persona")
    }
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Interno extends AuditoriaConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idInterno;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_persona", nullable = false, foreignKey = @ForeignKey(name = "fk_interno_persona"))
    private Persona persona;
 
    // ──────────────────────────────────────────────────────────────────
    //  DATOS PROPIOS DEL INTERNO (no vienen de Londra)
    // ──────────────────────────────────────────────────────────────────
 
    /** Universidad de procedencia del interno. */
    @Column(length = 150)
    private String universidad;
 
    /** Carrera que cursa (ej: "MEDICINA", "ENFERMERÍA"). */
    @Column(length = 150)
    private String carrera;
 
    /**
     * Año de formación / semestre actual.
     * Ejemplo: 5 (quinto año de medicina).
     */
    @Column(name = "ano_formacion")
    private Integer anoFormacion;
 
    /**
     * Número de matrícula universitaria.
     * Identificador único del estudiante en su universidad.
     */
    @Column(name = "nro_matricula", length = 50)
    private String nroMatricula;
 
    /** Inicio del período de rotación en el hospital. */
    @Column(name = "fecha_inicio_rotacion")
    private LocalDate fechaInicioRotacion;
 
    /** Fin del período de rotación. Al llegar esta fecha → activo pasa a false automáticamente. */
    @Column(name = "fecha_fin_rotacion")
    private LocalDate fechaFinRotacion;
 
    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
 
    // ──────────────────────────────────────────────────────────────────
    //  RELACIONES
    // ──────────────────────────────────────────────────────────────────
 
    /** Titulares que supervisan a este interno. */
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "interno")
    @Builder.Default
    private List<ResponsableInterno> responsablesInterno = new ArrayList<>();
 
    /** Jornadas asignadas a este interno. */
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "interno")
    @Builder.Default
    private List<InternoJornada> internoJornadas = new ArrayList<>();
 
    /** Verifica si el interno está dentro de su período de rotación activo. */
    @Transient
    public boolean estaEnRotacion() {
        LocalDate hoy = LocalDate.now();
        if (fechaInicioRotacion == null || fechaFinRotacion == null) return false;
        return !hoy.isBefore(fechaInicioRotacion) && !hoy.isAfter(fechaFinRotacion);
    }
}