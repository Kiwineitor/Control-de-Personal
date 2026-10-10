package com.uap.controlMedico.model.dto.gestionDto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.uap.controlMedico.model.dto.internosDto.InternoResumenResponse;
import com.uap.controlMedico.model.dto.titularDto.TitularResumenResponse;
import com.uap.controlMedico.model.enums.EstadoPermiso;
import com.uap.controlMedico.model.enums.TipoPermiso;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SolicitudPermisoResponse {
    private Long idSolicitudPermiso;
 
    // Solicitante (uno de los dos vendrá con datos)
    private TitularResumenResponse titular;
    private InternoResumenResponse interno;
    private String nombreSolicitante;
 
    private TipoPermiso tipoPermiso;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private String motivo;
    private String documentoUrl;
 
    private EstadoPermiso estadoPermiso;
    private String aprobadoPor;
    private LocalDateTime fechaAprobacion;
    private String observacionAprobacion;
 
    private LocalDateTime fechaCreacion;

}
