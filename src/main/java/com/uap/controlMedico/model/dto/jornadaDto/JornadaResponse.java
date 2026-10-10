package com.uap.controlMedico.model.dto.jornadaDto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.uap.controlMedico.model.enums.EstadoJornada;
import com.uap.controlMedico.model.enums.TipoJornada;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class JornadaResponse {
    
    private Long idJornada;
    private String nombre;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private TipoJornada tipoJornada;
    private EstadoJornada estado;
    private String descripcion;
    private long duracionHoras;
    private boolean enCurso;
    private int totalInternos;
    private int totalTitulares;
    private LocalDateTime fechaCreacion;
    private String creadoPor;
}
