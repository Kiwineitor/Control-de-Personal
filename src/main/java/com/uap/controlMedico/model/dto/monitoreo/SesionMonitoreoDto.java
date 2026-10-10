package com.uap.controlMedico.model.dto.monitoreo;

import java.math.BigDecimal;

import com.uap.controlMedico.model.enums.EstadoZona;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class SesionMonitoreoDto {
    private Long   idSesion;
    private String nombrePersonal;
    private String cargo;
    private String tipoPersonal;        // "TITULAR" | "INTERNO"
    private Long   idPersonal;
    private Long   idJornada;
    private String nombreJornada;
    private Long   idLugar;
    private String nombreLugar;
    private EstadoZona estadoZona;
    private String timestampInicio;
    private String ultimaActualizacion;
    private Long   tiempoFueraMinutos;  // 0 si está dentro
    private BigDecimal ultimaLatitud;
    private BigDecimal ultimaLongitud;
    private BigDecimal ultimaDistanciaMetros;
    private boolean tieneEventoAbierto;
}
