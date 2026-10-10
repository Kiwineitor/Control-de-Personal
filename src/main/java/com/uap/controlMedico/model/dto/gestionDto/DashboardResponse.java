package com.uap.controlMedico.model.dto.gestionDto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DashboardResponse {
    
    private LocalDate fecha;
    private LocalDateTime generadoEn;
 
    // Personal en turno hoy
    private long titularesEnTurnoHoy;
    private long internosEnTurnoHoy;
 
    // Asistencia de internos (hoy)
    private long internosATiempo;
    private long internosConTardanza;
    private long internosNoPresentes;
    private long internosSinSalida;
 
    // Permanencia (última ronda)
    private long verificacionesPresentes;
    private long verificacionesAusentes;
    private long verificacionesSinRespuesta;
 
    // Cola de aprobaciones pendientes
    private long permisosPendientes;
    private long delegacionesPendientes;
 
    // Alertas activas
    private long rondasEnCurso;
    private long jornadasHoy;

}
