package com.uap.controlMedico.model.dto.jornadaDto;

import java.time.LocalTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Override de horario de un titular dentro de una jornada.
 *
 * Las horas en null significan "hereda de la jornada". Por eso los flags
 * {@code trabajaManana} / {@code trabajaTarde} son campos aparte: sin ellos no
 * había forma de decir "este titular no cubre la tarde" en una jornada partida —
 * dejar las horas vacías se interpretaba como heredar, no como no trabajar.
 *
 * En jornadas CONTINUAS solo se usan {@code horaInicio} / {@code horaFin}; el
 * resto se ignora.
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class HorarioIndividual {

    /** Franja de la mañana (o franja única si la jornada es CONTINUA). */
    private LocalTime horaInicio;
    private LocalTime horaFin;

    /** Franja de la tarde. Solo aplica en jornadas PARTIDAS. */
    private LocalTime horaInicioTarde;
    private LocalTime horaFinTarde;

    /** Null se trata como true, para no romper los clientes que no los envían. */
    private Boolean trabajaManana;
    private Boolean trabajaTarde;

    public boolean cubreManana() {
        return trabajaManana == null || trabajaManana;
    }

    public boolean cubreTarde() {
        return trabajaTarde == null || trabajaTarde;
    }
}
