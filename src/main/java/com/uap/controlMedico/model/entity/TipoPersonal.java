package com.uap.controlMedico.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tipo de personal del hospital: MEDICO, ENFERMERO, CAJERO, FARMACIA, LIMPIEZA, etc.
 * Configurable por el admin — permite CRUD sin tocar código.
 */
@Entity
@Table(name = "tipo_personal")
@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
public class TipoPersonal {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idTipoPersonal;

    @NotBlank
    @Size(max = 80)
    @Column(nullable = false, unique = true, length = 80)
    private String nombre;

    @Size(max = 200)
    @Column(length = 200)
    private String descripcion;

    /** Color hex para la UI (ej: "#0369a1"). */
    @Column(length = 20)
    @Builder.Default
    private String color = "#64748b";

    /**
     * True = personal médico/asistencial (médico, enfermero).
     * Accede a: Turnos, Actividades, Permisos, Alertas.
     * False = personal de apoyo/admin.
     * Accede a: Permisos, Alertas únicamente.
     */
    @Column(name = "es_personal_medico", nullable = false)
    @Builder.Default
    private boolean esPersonalMedico = false;

    /**
     * True = puede registrar internos (usualmente solo MEDICO).
     */
    @Column(name = "puede_gestionar_internos", nullable = false)
    @Builder.Default
    private boolean puedeGestionarInternos = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    @Column(name = "fecha_creacion")
    private java.time.LocalDateTime fechaCreacion;

    @PrePersist
    private void prePersist() {
        if (fechaCreacion == null) fechaCreacion = java.time.LocalDateTime.now();
    }
}
