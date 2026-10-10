package com.uap.controlMedico.model.entity;

import java.util.ArrayList;
import java.util.List;

import com.uap.controlMedico.config.AuditoriaConfig;

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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
    name = "titular",
    indexes = {
        @Index(name = "idx_titular_per_id",     columnList = "per_id"),
        @Index(name = "idx_titular_contrato_id", columnList = "contrato_id"),
        @Index(name = "idx_titular_persona", columnList = "id_persona")
    }
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Titular extends AuditoriaConfig{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idTitular;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_persona", nullable = false, foreignKey = @ForeignKey(name = "fk_titular_persona"))
    private Persona persona;
 
    // ──────────────────────────────────────────────────────────────────
    //  TIPO DE PERSONAL
    // ──────────────────────────────────────────────────────────────────

    /**
     * Tipo de personal: MEDICO, ENFERMERO, CAJERO, FARMACIA, etc.
     * Nullable por compatibilidad con registros previos.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tipo_personal", foreignKey = @ForeignKey(name = "fk_titular_tipo_personal"))
    private TipoPersonal tipoPersonal;

    // ──────────────────────────────────────────────────────────────────
    //  REFERENCIAS A LONDRA (opcionales para registros internos)
    // ──────────────────────────────────────────────────────────────────

    /**
     * ID de la persona en Londra (o = idPersona para registros internos SISU).
     * Nullable: no todos los titulares provienen de Londra.
     */
    @Column(name = "per_id")
    private Long perId;

    /**
     * ID del contrato en Londra.
     * Nullable: no todos los titulares tienen contrato en Londra.
     */
    @Column(name = "contrato_id")
    private Long contratoId;
 
    /**
     * Cargo/puesto del titular. Obtenido de Londra → AsignacionL.
     * Ejemplos: "MÉDICO GENERAL", "ENFERMERA JEFE", "CAJERO".
     */
    @Column(length = 150)
    private String cargo;
 
    /**
     * Especialidad médica. Obtenido de Londra → AsignacionL.
     * Ejemplos: "CARDIOLOGÍA", "PEDIATRÍA", "MEDICINA GENERAL".
     * Puede ser null si el titular no es médico especialista.
     */
    @Column(length = 150)
    private String especialidad;
 
    // ──────────────────────────────────────────────────────────────────
    //  DATOS PROPIOS DEL SISTEMA SISU
    // ──────────────────────────────────────────────────────────────────
 
    /**
     * Estado activo en el sistema SISU.
     * Independiente del estado en Londra: un titular puede estar activo
     * en Londra pero dado de baja en SISU (ej: licencia prolongada).
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
 
    // ──────────────────────────────────────────────────────────────────
    //  RELACIONES
    // ──────────────────────────────────────────────────────────────────
 
    /** Internos bajo su supervisión directa. */
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "titular")
    @Builder.Default
    private List<ResponsableInterno> responsablesInterno = new ArrayList<>();
 
    /** Jornadas/turnos asignados a este titular. */
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "titular")
    @Builder.Default
    private List<TitularJornada> titularJornadas = new ArrayList<>();
 
    /** Solicitudes de permiso o ausencia realizadas por este titular. */
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "titular")
    @Builder.Default
    private List<SolicitudPermiso> solicitudesPermiso = new ArrayList<>();
 
}
