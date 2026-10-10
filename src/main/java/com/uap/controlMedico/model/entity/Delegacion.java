package com.uap.controlMedico.model.entity;

import java.time.LocalDateTime;
import java.time.LocalTime;

import com.uap.controlMedico.config.AuditoriaConfig;
import com.uap.controlMedico.model.enums.EstadoDelegacion;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
    name = "delegacion",
    indexes = {
        @Index(name = "idx_delegacion_titular_jornada",  columnList = "id_titular_jornada"),
        @Index(name = "idx_delegacion_interno",          columnList = "id_interno_responsable"),
        @Index(name = "idx_delegacion_estado",           columnList = "estado"),
        @Index(name = "idx_delegacion_solicitud",       columnList = "id_solicitud_permiso")
    }
)
@Getter @Setter
@AllArgsConstructor @NoArgsConstructor
@SuperBuilder
public class Delegacion extends AuditoriaConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idDelegacion;
 
    // ── QUIÉN DELEGA Y EN QUIÉN ────────────────────────────────────
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_titular_jornada", nullable = false,
                foreignKey = @ForeignKey(name = "fk_delegacion_titular_jornada"))
    private TitularJornada titularJornada;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_interno_responsable", nullable = false,
                foreignKey = @ForeignKey(name = "fk_delegacion_interno"))
    private Interno internoResponsable;

    // ── PERÍODO ────────────────────────────────────────────────────
    @Column(name = "hora_inicio_planificada")
    private LocalTime horaInicioPlanificada;

    @Column(name = "hora_fin_planificada")
    private LocalTime horaFinPlanificada;

    @Column(name = "hora_inicio_real")
    private LocalTime horaInicioReal;

    @Column(name = "hora_fin_real")
    private LocalTime horaFinReal;

    // ── MOTIVO Y ESTADO ────────────────────────────────────────────
    @NotBlank(message = "El motivo de la delegación es obligatorio")
    @Column(nullable = false, length = 500)
    private String motivo;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private EstadoDelegacion estado = EstadoDelegacion.PENDIENTE_APROBACION;

    // ── APROBACIÓN ─────────────────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_aprobado_por",
                foreignKey = @ForeignKey(name = "fk_delegacion_aprobado_por"))
    private Usuario aprobadoPor;

    @Column(name = "fecha_aprobacion")
    private LocalDateTime fechaAprobacion;

    @Column(name = "observacion_aprobacion", length = 500)
    private String observacionAprobacion;

    // ── VÍNCULO CON SOLICITUD (NUEVO) ──────────────────────────────
    /**
     * Solicitud de permiso que originó esta delegación.
     * Null si la delegación fue creada manualmente por un supervisor.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "id_solicitud_permiso",
        unique = true,
        foreignKey = @ForeignKey(name = "fk_delegacion_solicitud")
    )
    private SolicitudPermiso solicitudPermiso;

    // ── MÉTODOS DE DOMINIO ─────────────────────────────────────────
    @Transient
    public boolean estaActiva() {
        return EstadoDelegacion.EN_CURSO.equals(estado);
    }

    @Transient
    public boolean requiereAprobacion() {
        return EstadoDelegacion.PENDIENTE_APROBACION.equals(estado);
    }

    @Transient
    public String getDescripcionCompleta() {
        String titular = titularJornada != null && titularJornada.getTitular() != null
            ? titularJornada.getTitular().getPersona().getNombreCompleto() : "N/A";
        String interno = internoResponsable != null
            ? internoResponsable.getPersona().getNombreCompleto() : "N/A";
        return String.format("Delegación: %s → %s | %s", titular, interno, estado.name());
    }
}
