package com.uap.controlMedico.model.dto.actividadDto;

import java.time.LocalDateTime;
import java.time.LocalTime;

import com.uap.controlMedico.model.enums.EstadoActividad;
import com.uap.controlMedico.model.enums.TipoActividad;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ActividadJornadaResponse {
    private Long            idActividadJornada;
    private Long            idActividad;
    private String          nombreActividad;
    private TipoActividad   tipoActividad;
    // Jornada
    private Long            idJornada;
    private String          nombreJornada;
    // Asignación
    private Long            idInternoJornada;
    private String          nombreInterno;
    private Long            idTitularJornada;
    private String          nombreTitular;
    // Estado v2
    private EstadoActividad estadoActividad;
    private boolean         culminada;
    private LocalDateTime   fechaCulminacion;
    private String          observaciones;
    // Evidencia
    private String          evidenciaUrl;
    private String          evidenciaDownloadUrl;
    // Validación (Paso 4)
    private String          validadoPor;
    private LocalDateTime   fechaValidacion;
    // Carry-over
    private Long            carryOverDeId;
    private boolean         esCarryOver;
    // Tiempos
    private LocalTime       horaInicio;
    private LocalTime       horaFin;
    private LocalDateTime   fechaCreacion;
}
