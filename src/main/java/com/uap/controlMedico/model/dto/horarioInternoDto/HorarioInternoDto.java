package com.uap.controlMedico.model.dto.horarioInternoDto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class HorarioInternoDto {
    private Long   idHorarioInterno;
    private Long   idInternoJornada;
    private Long   idJornada;
    private String nombreJornada;
    private Long   idInterno;
    private String nombreInterno;
    private String carrera;
    private Integer mes;
    private Integer anio;
    private String observaciones;
    private boolean activo;
    private List<DiaHorarioDto> dias;
    private String horaInicioJornada;   // referencia del horario base
    private String horaFinJornada;
}
