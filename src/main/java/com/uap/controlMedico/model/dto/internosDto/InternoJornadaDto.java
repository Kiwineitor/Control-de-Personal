package com.uap.controlMedico.model.dto.internosDto;

import java.time.LocalTime;

import com.uap.controlMedico.model.enums.EstadoAsignacion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class InternoJornadaDto {
    private Long    idInternoJornada;
    private Long    idInterno;
    private String  nombreCompleto;
    private String  carrera;
    private String  universidad;
    private boolean estaEnRotacion;
    private Long    idLugar;
    private String  nombreLugar;
    private Long    idResponsableInterno;
    private String  nombreResponsable;
    private EstadoAsignacion estado;
    private String  adjuntoUrl;
    private String  observaciones;
    private LocalTime horaInicioOverride;
    private LocalTime horaFinOverride;
    private LocalTime horaInicioEfectiva;
    private LocalTime horaFinEfectiva;
}
