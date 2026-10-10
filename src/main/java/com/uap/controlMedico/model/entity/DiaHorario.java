package com.uap.controlMedico.model.entity;

import java.time.LocalDate;
import java.time.LocalTime;

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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Día específico de asistencia de un interno dentro de un HorarioInterno.
 * Permite sobreescribir horas si el interno asiste menos tiempo que la jornada base.
 */
@Entity
@Table(
    name = "dia_horario",
    indexes = {
        @Index(name = "idx_dh_horario_interno", columnList = "id_horario_interno"),
        @Index(name = "idx_dh_fecha",           columnList = "fecha")
    }
)
@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
public class DiaHorario {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idDiaHorario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_horario_interno", nullable = false,
                foreignKey = @ForeignKey(name = "fk_dh_horario_interno"))
    private HorarioInterno horarioInterno;

    @Column(nullable = false)
    private LocalDate fecha;

    /** null → usa la hora de inicio de la Jornada padre */
    @Column(name = "hora_inicio")
    private LocalTime horaInicio;

    /** null → usa la hora de fin de la Jornada padre */
    @Column(name = "hora_fin")
    private LocalTime horaFin;

    @Column(length = 200)
    private String observaciones;
}
