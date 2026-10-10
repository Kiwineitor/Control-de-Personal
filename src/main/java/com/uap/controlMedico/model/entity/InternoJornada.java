package com.uap.controlMedico.model.entity;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import com.uap.controlMedico.config.AuditoriaConfig;
import com.uap.controlMedico.model.enums.EstadoAsignacion;

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
import jakarta.persistence.OneToOne;
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
    name = "interno_jornada",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_interno_jornada",
        columnNames = {"id_interno", "id_jornada"}
    ),
    indexes = {
        @Index(name = "idx_ij_interno",  columnList = "id_interno"),
        @Index(name = "idx_ij_jornada",  columnList = "id_jornada"),
        @Index(name = "idx_ij_estado",   columnList = "estado")
    }
)
@Getter @Setter @AllArgsConstructor @NoArgsConstructor @SuperBuilder
public class InternoJornada extends AuditoriaConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idInternoJornada;
 
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "id_interno",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_ij_interno")
    )
    private Interno interno;
 
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "id_jornada",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_ij_jornada")
    )
    private Jornada jornada;
 
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "id_lugar",
        nullable = true,
        foreignKey = @ForeignKey(name = "fk_ij_lugar")
    )
    private Lugar lugar;

    /**
     * Snapshot del ResponsableInterno activo al momento de asignar.
     * Garantiza auditoría histórica: si el responsable cambia después,
     * este registro preserva quién supervisaba al interno en esta jornada.
     * nullable: un interno podría asignarse sin responsable activo (casos excepcionales).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "id_responsable_interno",
        nullable = true,
        foreignKey = @ForeignKey(name = "fk_ij_responsable_interno")
    )
    private ResponsableInterno responsableInterno;
 
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EstadoAsignacion estado = EstadoAsignacion.PROGRAMADO;
 
    /**
     * Ruta al archivo adjunto en el servidor SMB.
     * Ejemplo: "uploads/jornadas/adjuntos/2024/05/interno_123_jornada_456.pdf"
     * Almacenado y recuperado mediante ServerFileConfig / UtilidadesService.
     */
    @Column(name = "adjunto_url", length = 500)
    private String adjuntoUrl;
 
    @Column(length = 500)
    private String observaciones;

    /**
     * Override de horario individual para este interno dentro de la jornada.
     * Si es null → hereda de Jornada.horaInicio / Jornada.horaFin.
     * Si tiene valor → se usa este horario específico para esta persona.
     */
    @Column(name = "hora_inicio_override")
    private LocalTime horaInicioOverride;

    @Column(name = "hora_fin_override")
    private LocalTime horaFinOverride;
 
    // ──────────────────────────────────────────────────────────────────
    //  RELACIONES DERIVADAS
    // ──────────────────────────────────────────────────────────────────
 
    /** Actividades que realiza el interno durante esta jornada. */
    @OneToMany(
        fetch = FetchType.LAZY,
        mappedBy = "internoJornada",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    @Builder.Default
    private List<ActividadJornada> actividadJornadas = new ArrayList<>();
 
    /**
     * Registro de asistencia (entrada/salida) de este interno en esta jornada.
     * OneToOne: una jornada tiene un único registro de asistencia por interno.
     */
    @OneToOne(
        fetch = FetchType.LAZY,
        mappedBy = "internoJornada",
        cascade = CascadeType.ALL
    )
    private RegistroAsistencia registroAsistencia;

}
