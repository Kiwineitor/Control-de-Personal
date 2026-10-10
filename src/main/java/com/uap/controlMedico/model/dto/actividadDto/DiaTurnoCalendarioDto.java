package com.uap.controlMedico.model.dto.actividadDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Representa un día de turno para mostrar en el calendario del funcionario. */
@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class DiaTurnoCalendarioDto {
    private String  fecha;              // yyyy-MM-dd
    private String  diaSemana;          // "Lunes", "Martes"...
    private String  horaInicio;         // "08:00"
    private String  horaFin;            // "20:00"
    private String  modalidad;          // "CONTINUA" | "PARTIDA"
    private String  horaInicioTarde;    // null si CONTINUA
    private String  horaFinTarde;       // null si CONTINUA
    private String  nombreTurno;        // nombre de la jornada
    private String  tipoTurno;          // GUARDIA_12H, REGULAR, ADMINISTRATIVA...
    private Long    idJornada;
    private String  nombreLugar;
    private boolean esHoy;
    private boolean esPasado;
}
