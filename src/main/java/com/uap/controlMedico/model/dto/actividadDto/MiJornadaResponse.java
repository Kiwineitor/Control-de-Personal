package com.uap.controlMedico.model.dto.actividadDto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.uap.controlMedico.model.enums.TipoAsignacion;

import lombok.Builder;
import lombok.Data;


@Data
@Builder
public class MiJornadaResponse {
    private Long           idJornada;
    private String         nombre;
    private LocalDate      fechaInicio;
    private LocalDate      fechaFin;
    private LocalTime      horaInicio;
    private LocalTime      horaFin;
    private String         nombreLugar;
    private String         estadoJornada;
    // Tipo de asignación del usuario actual
    private TipoAsignacion tipoAsignacion;
    private Long           idAsignacion;
    // Resumen de actividades
    private int            totalActividades;
    private int            actividadesPendientes;
    private int            actividadesCompletadas;
    private int            actividadesValidadas;
    private Long idTitular;
}
