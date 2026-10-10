package com.uap.controlMedico.model.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.uap.controlMedico.config.AuditoriaConfig;
import com.uap.controlMedico.model.enums.EstadoPermiso;
import com.uap.controlMedico.model.enums.EstadoSolicitud;
import com.uap.controlMedico.model.enums.TipoPermiso;

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
import jakarta.persistence.PrePersist;
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
    name = "solicitud_permiso",
    indexes = {
        @Index(name = "idx_sp_titular", columnList = "id_titular"),
        @Index(name = "idx_sp_jornada", columnList = "id_jornada"),
        @Index(name = "idx_sp_estado",  columnList = "estado_solicitud"),
        @Index(name = "idx_sp_fecha",   columnList = "fecha_permiso"),
        @Index(name = "idx_sp_tj",      columnList = "id_titular_jornada")
    }
)
@Getter @Setter
@AllArgsConstructor @NoArgsConstructor
@SuperBuilder
public class SolicitudPermiso extends AuditoriaConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idSolicitudPermiso;

    // ── QUIÉN SOLICITA ─────────────────────────────────────────────
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_titular", nullable = false,
                foreignKey = @ForeignKey(name = "fk_sp_titular"))
    private Titular titular;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_titular_jornada",
                foreignKey = @ForeignKey(name = "fk_sp_titular_jornada"))
    private TitularJornada titularJornada;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_jornada",
                foreignKey = @ForeignKey(name = "fk_sp_jornada"))
    private Jornada jornada;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_permiso", nullable = false, length = 30)
    private TipoPermiso tipoPermiso;

    @NotBlank(message = "El motivo de la solicitud es obligatorio")
    @Column(name = "motivo_solicitud", nullable = false, length = 500)
    private String motivoSolicitud;

    @Column(name = "fecha_permiso")
    private LocalDate fechaPermiso;

    @Column(name = "hora_inicio_solicitada")
    private LocalTime horaInicioSolicitada;

    @Column(name = "hora_fin_solicitada")
    private LocalTime horaFinSolicitada;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_solicitud", nullable = false, length = 20)
    @Builder.Default
    private EstadoSolicitud estadoSolicitud = EstadoSolicitud.PENDIENTE;

    @Column(name = "fecha_hora_solicitud", nullable = false)
    private LocalDateTime fechaHoraSolicitud;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_revisado_por",
                foreignKey = @ForeignKey(name = "fk_sp_revisado_por"))
    private Usuario revisadoPor;

    @Column(name = "fecha_hora_revision")
    private LocalDateTime fechaHoraRevision;

    @Column(name = "observacion_revision", length = 500)
    private String observacionRevision;

    @Column(name = "justificante_url", length = 500)
    private String justificanteUrl;

    @OneToOne(mappedBy = "solicitudPermiso", fetch = FetchType.LAZY)
    private Delegacion delegacion;

    @PrePersist
    private void prePersist() {
        if (fechaHoraSolicitud == null) {
            fechaHoraSolicitud = LocalDateTime.now();
        }
    }

    @Transient
    public boolean isPendiente() {
        return EstadoSolicitud.PENDIENTE.equals(estadoSolicitud)
            || EstadoSolicitud.EN_OBSERVACION.equals(estadoSolicitud);
    }

    @Transient
    public boolean tieneDelegacion() {
        return delegacion != null;
    }
}