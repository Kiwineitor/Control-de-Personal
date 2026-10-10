package com.uap.controlMedico.model.dto.gestionDto;

import java.time.LocalDateTime;
import java.time.LocalTime;

import com.uap.controlMedico.model.dto.catalagoDto.LugarResponse;
import com.uap.controlMedico.model.dto.internosDto.InternoResumenResponse;
import com.uap.controlMedico.model.dto.titularDto.TitularResumenResponse;
import com.uap.controlMedico.model.enums.EstadoDelegacion;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DelegacionResponse {
    
    private Long idDelegacion;
 
    private TitularResumenResponse titular;
    private InternoResumenResponse internoResponsable;
    private LugarResponse lugar;
 
    private LocalTime horaInicioPlanificada;
    private LocalTime horaFinPlanificada;
    private LocalTime horaInicioReal;
    private LocalTime horaFinReal;
 
    private String motivo;
    private EstadoDelegacion estado;
 
    private String aprobadoPor;
    private LocalDateTime fechaAprobacion;
    private String observacionAprobacion;
 
    private LocalDateTime fechaCreacion;

}
