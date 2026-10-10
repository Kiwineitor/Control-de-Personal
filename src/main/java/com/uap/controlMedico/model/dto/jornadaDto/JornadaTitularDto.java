package com.uap.controlMedico.model.dto.jornadaDto;

import java.time.LocalDate;

import com.uap.controlMedico.model.enums.EstadoAsignacion;
import com.uap.controlMedico.model.enums.EstadoJornada;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class JornadaTitularDto {
    private Long             idTitularJornada;
    private Long             idJornada;
    private String           nombre;
    private LocalDate        fechaInicio;
    private LocalDate        fechaFin;
    private EstadoJornada    estadoJornada;
    private EstadoAsignacion estadoAsignacion;
    private String           lugarNombre;
}
