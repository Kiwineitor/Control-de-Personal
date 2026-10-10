package com.uap.controlMedico.model.dto.jornadaDto;

import java.time.LocalTime;

import com.uap.controlMedico.model.dto.catalagoDto.ActividadResponse;
import com.uap.controlMedico.model.enums.EstadoActividad;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ActividadJornadaResponse {
    private Long idActividadJornada;
    private ActividadResponse actividad;
    private EstadoActividad estadoActividad;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private String evidenciaUrl;
    private String observaciones;

}
