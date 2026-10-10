package com.uap.controlMedico.model.dto.jornadaDto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.uap.controlMedico.model.enums.EstadoJornada;
import com.uap.controlMedico.model.enums.ModalidadHorario;
import com.uap.controlMedico.model.enums.TipoJornada;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class JornadaDto {
    private Long    idJornada;
    private String  nombre;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private TipoJornada  tipoJornada;
    private EstadoJornada estado;
    private String  descripcion;
    private long    duracionHoras;
    private boolean estaEnCurso;

    /** Control GPS suspendido: el turno corre pero no se penaliza a nadie. */
    private boolean monitoreoPausado;
    private int     totalTitulares;
    private int     totalInternos;
    // Lugar general (Paso 7: se poblará cuando Lugar esté completo)
    private Long    idLugar;
    private String  nombreLugar;
    private ModalidadHorario modalidad;
    private LocalTime horaInicioTarde;
    private LocalTime horaFinTarde;
    /** Días del turno como CSV ISO: "2026-05-01,2026-05-04,..." */
    private String diasDefault;
}
