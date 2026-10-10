package com.uap.controlMedico.model.entity;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.uap.controlMedico.config.AuditoriaConfig;
import com.uap.controlMedico.model.enums.EstadoAsignacion;
import com.uap.controlMedico.model.enums.ModalidadHorario;

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
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
    name = "titular_jornada",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_titular_jornada",
        columnNames = {"id_titular", "id_jornada"}
    ),
    indexes = {
        @Index(name = "idx_tj_titular", columnList = "id_titular"),
        @Index(name = "idx_tj_jornada", columnList = "id_jornada"),
        @Index(name = "idx_tj_estado",  columnList = "estado")
    }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class TitularJornada extends AuditoriaConfig{
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idTitularJornada;
 
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "id_titular",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_tj_titular")
    )
    private Titular titular;
 
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "id_jornada",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_tj_jornada")
    )
    private Jornada jornada;
 
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "id_lugar",
        nullable = true,
        foreignKey = @ForeignKey(name = "fk_tj_lugar")
    )
    private Lugar lugar;
 
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EstadoAsignacion estado = EstadoAsignacion.PROGRAMADO;
 
    @Column(length = 500)
    private String observaciones;

    /**
     * Días específicos en que este titular trabaja dentro de la jornada (ROL TURNO).
     * Almacenado como CSV de fechas ISO: "2025-05-01,2025-05-04,2025-05-07"
     * Si es null/vacío → trabaja todos los días dentro del rango fechaInicio-fechaFin de la jornada.
     */
    @Column(name = "dias_trabajo", length = 2000)
    private String diasTrabajoJson;

    /**
     * Override de horario individual para este titular dentro de la jornada.
     * Si es null → hereda de Jornada.horaInicio / Jornada.horaFin.
     * Si tiene valor → se usa este horario específico para esta persona.
     *
     * En una jornada PARTIDA este par corresponde a la franja de la MAÑANA.
     */
    @Column(name = "hora_inicio_override")
    private LocalTime horaInicioOverride;

    @Column(name = "hora_fin_override")
    private LocalTime horaFinOverride;

    /**
     * Override de la franja de TARDE. Solo tiene sentido si la jornada es PARTIDA.
     * Si es null → hereda de Jornada.horaInicioTarde / Jornada.horaFinTarde.
     */
    @Column(name = "hora_inicio_tarde_override")
    private LocalTime horaInicioTardeOverride;

    @Column(name = "hora_fin_tarde_override")
    private LocalTime horaFinTardeOverride;

    /**
     * Qué franjas cubre este titular en una jornada PARTIDA.
     *
     * Hacen falta como flag aparte porque las horas en null ya significan "hereda
     * de la jornada": sin esto no había forma de expresar "este titular no trabaja
     * la tarde". Por defecto ambas en true, que reproduce el comportamiento previo
     * (el titular sigue el horario completo de su jornada).
     *
     * En jornadas CONTINUAS se ignoran: solo existe una franja.
     */
    @Column(name = "trabaja_manana", columnDefinition = "boolean not null default true")
    @Builder.Default
    private boolean trabajaManana = true;

    @Column(name = "trabaja_tarde", columnDefinition = "boolean not null default true")
    @Builder.Default
    private boolean trabajaTarde = true;

    /** Obtiene los días de trabajo parseando el CSV. Retorna lista vacía si no hay. */
    @Transient
    public List<LocalDate> getDiasTrabajo() {
        if (diasTrabajoJson == null || diasTrabajoJson.isBlank()) return Collections.emptyList();
        try {
            return Arrays.stream(diasTrabajoJson.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(LocalDate::parse)
                .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** Persiste la lista de días como CSV. */
    @Transient
    public void setDiasTrabajo(List<LocalDate> dias) {
        if (dias == null || dias.isEmpty()) {
            this.diasTrabajoJson = null;
            return;
        }
        this.diasTrabajoJson = dias.stream()
            .map(LocalDate::toString)
            .collect(Collectors.joining(","));
    }

    /**
     * Determina si este titular tiene turno activo en el momento dado.
     * Considera: estado de asignación, rango de fechas, dias específicos (ROL TURNO)
     * y horario individual (overrides) o de la jornada.
     */
    @Transient
    public boolean estaEnTurnoAhora(LocalDate hoy, LocalTime ahora) {
        if (jornada == null) return false;
        // Turno con el monitoreo suspendido: nadie está "en turno" a efectos de
        // control, así que no se abre sesión ni se le exige ubicación a nadie.
        if (jornada.isMonitoreoPausado()) return false;
        if (estado == EstadoAsignacion.CANCELADO || estado == EstadoAsignacion.COMPLETADO) return false;
        if (hoy.isBefore(jornada.getFechaInicio()) || hoy.isAfter(jornada.getFechaFin())) return false;
        List<LocalDate> dias = getDiasTrabajo();
        if (!dias.isEmpty() && !dias.contains(hoy)) return false;

        // Jornada PARTIDA (administrativa: mañana + tarde): son DOS ventanas
        // independientes. Antes solo se evaluaba la primera, así que en horario de
        // tarde el titular constaba como fuera de turno — el GPS no lo vigilaba y la
        // vista lo daba por no presentado.
        if (esPartida()) {
            return (trabajaManana && estaEnHorario(inicioManana(), finManana(), ahora))
                || (trabajaTarde  && estaEnHorario(inicioTarde(),  finTarde(),  ahora));
        }

        return estaEnHorario(inicioManana(), finManana(), ahora);
    }

    /**
     * Franja horaria que aplica a este titular en el momento dado: la que está en
     * curso, y si ninguna lo está, la primera que trabaje ese día.
     *
     * Existe para que la UI y los DTOs muestren el tramo correcto. Sin esto se
     * reportaba siempre el horario de la mañana, incluso a media tarde.
     *
     * @return {inicio, fin}, o null si no trabaja ninguna franja
     */
    @Transient
    public LocalTime[] franjaVigente(LocalTime ahora) {
        if (!esPartida()) {
            return inicioManana() == null || finManana() == null
                ? null : new LocalTime[] { inicioManana(), finManana() };
        }

        boolean manana = trabajaManana && inicioManana() != null && finManana() != null;
        boolean tarde  = trabajaTarde  && inicioTarde()  != null && finTarde()  != null;

        if (manana && estaEnHorario(inicioManana(), finManana(), ahora))
            return new LocalTime[] { inicioManana(), finManana() };
        if (tarde && estaEnHorario(inicioTarde(), finTarde(), ahora))
            return new LocalTime[] { inicioTarde(), finTarde() };

        // Ninguna en curso: se anuncia la próxima del día, o la única que trabaje.
        if (manana && ahora.isBefore(inicioManana()))
            return new LocalTime[] { inicioManana(), finManana() };
        if (tarde)  return new LocalTime[] { inicioTarde(), finTarde() };
        if (manana) return new LocalTime[] { inicioManana(), finManana() };
        return null;
    }

    @Transient
    public boolean esPartida() {
        return jornada != null
            && jornada.getModalidad() == ModalidadHorario.PARTIDA
            && inicioTarde() != null && finTarde() != null;
    }

    @Transient
    public LocalTime inicioManana() {
        return horaInicioOverride != null ? horaInicioOverride
             : (jornada != null ? jornada.getHoraInicio() : null);
    }

    @Transient
    public LocalTime finManana() {
        return horaFinOverride != null ? horaFinOverride
             : (jornada != null ? jornada.getHoraFin() : null);
    }

    @Transient
    public LocalTime inicioTarde() {
        return horaInicioTardeOverride != null ? horaInicioTardeOverride
             : (jornada != null ? jornada.getHoraInicioTarde() : null);
    }

    @Transient
    public LocalTime finTarde() {
        return horaFinTardeOverride != null ? horaFinTardeOverride
             : (jornada != null ? jornada.getHoraFinTarde() : null);
    }

    /** Verifica si 'ahora' cae dentro del rango [inicio, fin], soportando turnos nocturnos. */
    @Transient
    private boolean estaEnHorario(LocalTime inicio, LocalTime fin, LocalTime ahora) {
        if (inicio == null || fin == null) return false;
        if (fin.isBefore(inicio)) {
            // Turno nocturno: cruza medianoche
            return !ahora.isBefore(inicio) || !ahora.isAfter(fin);
        }
        return !ahora.isBefore(inicio) && !ahora.isAfter(fin);
    }

    // ──────────────────────────────────────────────────────────────────
    //  RELACIONES DERIVADAS
    // ──────────────────────────────────────────────────────────────────
 
    /**
     * Delegaciones iniciadas desde esta asignación.
     * Un titular puede delegar responsabilidad a un interno
     * cuando necesita ausentarse temporalmente de su puesto.
     */
    @OneToMany(
        fetch = FetchType.LAZY,
        mappedBy = "titularJornada",
        cascade = CascadeType.ALL
    )
    @Builder.Default
    private List<Delegacion> delegaciones = new ArrayList<>();

}
