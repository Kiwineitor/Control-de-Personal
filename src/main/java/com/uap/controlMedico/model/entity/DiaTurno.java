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
 * Representa un día específico de turno dentro de un RolTurno.
 * Permite sobreescribir horas de inicio/fin si difieren del horario base de la jornada.
 */
@Entity
@Table(
    name = "dia_turno",
    indexes = {
        @Index(name = "idx_dt_rol_turno", columnList = "id_rol_turno"),
        @Index(name = "idx_dt_fecha",     columnList = "fecha")
    }
)
@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
public class DiaTurno {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idDiaTurno;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_rol_turno", nullable = false,
                foreignKey = @ForeignKey(name = "fk_dt_rol_turno"))
    private RolTurno rolTurno;

    @Column(nullable = false)
    private LocalDate fecha;

    /**
     * Hora de inicio específica para este día.
     * Si null → se usa la hora de inicio de la Jornada padre.
     */
    @Column(name = "hora_inicio")
    private LocalTime horaInicio;

    /**
     * Hora de fin específica para este día.
     * Si null → se usa la hora de fin de la Jornada padre.
     */
    @Column(name = "hora_fin")
    private LocalTime horaFin;

    @Column(length = 200)
    private String observaciones;
}
