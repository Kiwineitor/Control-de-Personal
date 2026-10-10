package com.uap.controlMedico.model.dto.jornadaDto;

import com.uap.controlMedico.model.dto.asistenciaDto.RegistroAsistenciaResponse;
import com.uap.controlMedico.model.dto.catalagoDto.LugarResponse;
import com.uap.controlMedico.model.dto.internosDto.InternoResumenResponse;
import com.uap.controlMedico.model.enums.EstadoAsignacion;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InternoJornadaResponse {
    
    private Long idInternoJornada;
    private InternoResumenResponse interno;
    private JornadaResponse jornada;
    private LugarResponse lugar;
    private EstadoAsignacion estado;
    private String adjuntoUrl;
    private String observaciones;
    private RegistroAsistenciaResponse registroAsistencia;

}
