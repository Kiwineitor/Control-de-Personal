package com.uap.controlMedico.model.dto.turnoDto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReemplazoTurnoDto {
    private Long idReemplazo;
    private Long idJornada;
    private LocalDate fechaReemplazo;
    private Long idOriginal;
    private String nombreOriginal;
    private Long idReemplazante;
    private String nombreReemplazo;
    private String motivo;
    private String estado;
    private String autorizadoPorNombre;
    private LocalDateTime fechaAutorizacion;
}
