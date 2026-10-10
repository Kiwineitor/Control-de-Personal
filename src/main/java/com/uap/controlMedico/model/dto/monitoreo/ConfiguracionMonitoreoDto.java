package com.uap.controlMedico.model.dto.monitoreo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class ConfiguracionMonitoreoDto {
    private Integer timeoutAbandonoMinutos;
    private Integer timeoutSinSenalMinutos;
    private Integer toleranciaAtrasoMin;
    private Integer umbralFaltaMin;
    private Integer intervaloGpsActivoSeg;
    private Integer intervaloGpsFueraZonaSeg;
    private Integer deltaMinMovimientoMetros;
    private Integer radioAlertaExtraMetros;
    private Integer confirmacionFueraSeg;
    private Integer precisionMaxConfiableMetros;
}
