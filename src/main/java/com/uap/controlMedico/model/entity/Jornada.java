package com.uap.controlMedico.model.entity;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.List;

import com.uap.controlMedico.config.AuditoriaConfig;
import com.uap.controlMedico.model.enums.EstadoJornada;
import com.uap.controlMedico.model.enums.ModalidadHorario;
import com.uap.controlMedico.model.enums.TipoJornada;

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
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
    name = "jornada",
    indexes = {
        @Index(name = "idx_jornada_fechas", columnList = "fecha_inicio, fecha_fin"),
        @Index(name = "idx_jornada_estado", columnList = "estado")
    }
)
@Setter @Getter @AllArgsConstructor @NoArgsConstructor @SuperBuilder
public class Jornada extends AuditoriaConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idJornada;
 
    /**
     * Nombre descriptivo de la jornada.
     * Ejemplos: "Turno Mañana - Emergencias 01/05",
     *           "Guardia 24H - UCI Mayo"
     */
    @Column(length = 200)
    private String nombre;
 
    // ──────────────────────────────────────────────────────────────────
    //  PERÍODO Y HORARIO
    // ──────────────────────────────────────────────────────────────────
 
    @NotNull(message = "La fecha de inicio es obligatoria")
    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;
 
    @NotNull(message = "La fecha de fin es obligatoria")
    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;
 
    @NotNull(message = "La hora de inicio es obligatoria")
    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;
 
    @NotNull(message = "La hora de fin es obligatoria")
    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;
 
    // ──────────────────────────────────────────────────────────────────
    //  CLASIFICACIÓN Y ESTADO
    // ──────────────────────────────────────────────────────────────────
 
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_jornada", length = 20)
    @Builder.Default
    private TipoJornada tipoJornada = TipoJornada.REGULAR;

    /**
     * CONTINUA (una franja) o PARTIDA (mañana + tarde).
     * Usado principalmente por jornadas ADMINISTRATIVA.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "modalidad", length = 15)
    @Builder.Default
    private ModalidadHorario modalidad = ModalidadHorario.CONTINUA;

    /** Hora de inicio del turno de tarde (solo si modalidad = PARTIDA). */
    @Column(name = "hora_inicio_tarde")
    private LocalTime horaInicioTarde;

    /** Hora de fin del turno de tarde (solo si modalidad = PARTIDA). */
    @Column(name = "hora_fin_tarde")
    private LocalTime horaFinTarde;
 
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EstadoJornada estado = EstadoJornada.ACTIVA;
 
    @Column(length = 500)
    private String descripcion;

    /**
     * Monitoreo GPS suspendido para este turno.
     *
     * Es un interruptor operativo, no un cambio de programación: el turno sigue
     * existiendo con su horario y su gente, pero el sistema deja de abrir sesiones,
     * de exigir ubicación y de registrar atrasos, faltas o abandonos. Sirve para
     * los días en que el control no debe correr (pruebas, feriados, cortes de red)
     * sin tener que cancelar el turno ni borrar asignaciones.
     */
    @Column(name = "monitoreo_pausado", columnDefinition = "boolean not null default false")
    @Builder.Default
    private boolean monitoreoPausado = false;

    /**
     * Días específicos del turno (nivel de grupo), CSV ISO: "2026-05-01,2026-05-04,...".
     * Si null → el turno abarca todos los días dentro de fechaInicio-fechaFin.
     * Al asignar un titular, este CSV se copia automáticamente a TitularJornada.diasTrabajoJson
     * si este no tiene ya días definidos.
     */
    @Column(name = "dias_default", length = 3000)
    private String diasDefaultJson;

    /** Obtiene la lista de días del turno. */
    @Transient
    public List<LocalDate> getDiasDefault() {
        if (diasDefaultJson == null || diasDefaultJson.isBlank()) return Collections.emptyList();
        try {
            return Arrays.stream(diasDefaultJson.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(LocalDate::parse).collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** Persiste la lista de días del turno. */
    @Transient
    public void setDiasDefault(List<LocalDate> dias) {
        if (dias == null || dias.isEmpty()) { this.diasDefaultJson = null; return; }
        this.diasDefaultJson = dias.stream().map(LocalDate::toString)
            .collect(Collectors.joining(","));
    }

    /**
     * Lugar general de la jornada (opcional).
     * nullable=true hasta el Paso 7; en ese paso se puede restricción a nullable=false
     * si el negocio lo requiere.
     * todo Paso 7: evaluar hacer nullable=false.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "id_lugar",
        nullable = true,
        foreignKey = @ForeignKey(name = "fk_jornada_lugar")
    )
    private Lugar lugar;
 
    // ──────────────────────────────────────────────────────────────────
    //  RELACIONES
    // ──────────────────────────────────────────────────────────────────
 
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "jornada")
    @Builder.Default
    private List<InternoJornada> internoJornadas = new ArrayList<>();
 
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "jornada")
    @Builder.Default
    private List<TitularJornada> titularJornadas = new ArrayList<>();
 
    // ──────────────────────────────────────────────────────────────────
    //  MÉTODOS DE DOMINIO
    // ──────────────────────────────────────────────────────────────────
 
    /**
     * Calcula la duración en horas de la jornada.
     * Maneja correctamente jornadas nocturnas que cruzan la medianoche.
     */
    @Transient
    public long getDuracionHoras() {
        if (horaInicio == null || horaFin == null) return 0;
        long minutos = java.time.Duration.between(horaInicio, horaFin).toMinutes();
        if (minutos < 0) minutos += 24 * 60; // cruza medianoche
        return minutos / 60;
    }
 
    /**
     * ¿Debe correr el control GPS para este turno?
     *
     * Punto único que consultan el scheduler, el ingreso de ubicaciones y la
     * detección de ausencias antes de exigirle algo a alguien.
     */
    @Transient
    public boolean monitoreoHabilitado() {
        return estado == EstadoJornada.ACTIVA && !monitoreoPausado;
    }

    /** True si la jornada cubre el momento actual (considera horario partido). */
    @Transient
    public boolean estaEnCurso() {
        LocalDate hoy   = LocalDate.now();
        LocalTime ahora = LocalTime.now();
        if (estado != EstadoJornada.ACTIVA) return false;
        if (hoy.isBefore(fechaInicio) || hoy.isAfter(fechaFin)) return false;
        return estaEnHorarioAhora(ahora);
    }

    /** Verifica si la hora dada cae dentro del horario de esta jornada (continua o partida). */
    @Transient
    public boolean estaEnHorarioAhora(LocalTime ahora) {
        if (horaInicio == null || horaFin == null) return false;
        if (modalidad == ModalidadHorario.PARTIDA
                && horaInicioTarde != null && horaFinTarde != null) {
            // Franja mañana
            boolean enManana = !ahora.isBefore(horaInicio) && !ahora.isAfter(horaFin);
            // Franja tarde
            boolean enTarde  = !ahora.isBefore(horaInicioTarde) && !ahora.isAfter(horaFinTarde);
            return enManana || enTarde;
        }
        // Horario continuo (puede cruzar medianoche)
        if (horaFin.isAfter(horaInicio)) {
            return !ahora.isBefore(horaInicio) && !ahora.isAfter(horaFin);
        }
        // Nocturno: ej. 20:00 → 08:00
        return !ahora.isBefore(horaInicio) || !ahora.isAfter(horaFin);
    }

}
