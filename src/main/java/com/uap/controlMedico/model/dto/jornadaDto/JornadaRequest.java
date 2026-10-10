package com.uap.controlMedico.model.dto.jornadaDto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.uap.controlMedico.model.enums.ModalidadHorario;
import com.uap.controlMedico.model.enums.TipoJornada;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JornadaRequest {
    
    @Size(max = 200)
    private String nombre;
 
    @NotNull(message = "La fecha de inicio es obligatoria")
    private LocalDate fechaInicio;
 
    @NotNull(message = "La fecha de fin es obligatoria")
    private LocalDate fechaFin;
 
    @NotNull(message = "La hora de inicio es obligatoria")
    private LocalTime horaInicio;
 
    @NotNull(message = "La hora de fin es obligatoria")
    private LocalTime horaFin;
 
    private TipoJornada tipoJornada = TipoJornada.REGULAR;

    private ModalidadHorario modalidad = ModalidadHorario.CONTINUA;

    /** Solo obligatorio si modalidad = PARTIDA. */
    private LocalTime horaInicioTarde;

    /** Solo obligatorio si modalidad = PARTIDA. */
    private LocalTime horaFinTarde;

    /**
     * Días específicos del turno (CSV ISO: "2026-05-01,2026-05-04,...").
     * Al asignar staff, estos días se copian automáticamente a cada TitularJornada.
     */
    private String diasDefault;

    @Size(max = 500)
    private String descripcion;

    private Long idLugar;

}
