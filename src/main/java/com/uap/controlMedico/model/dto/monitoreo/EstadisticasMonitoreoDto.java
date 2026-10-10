package com.uap.controlMedico.model.dto.monitoreo;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class EstadisticasMonitoreoDto {
    private long totalRondas;
    private long rondasHoy;
    private long verificacionesTotales;
    private long presenciaConfirmada;
    private long ausencias;
    private long sinRespuesta;
    private double porcentajePresenciaGlobal;
    private long registrosAsistenciaHoy;
    private long tardanzasHoy;
    private List<ResumenPorEstado> distribucionVerificaciones;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ResumenPorEstado {
        private String clave;
        private long   cantidad;
        private double porcentaje;
    }
}
