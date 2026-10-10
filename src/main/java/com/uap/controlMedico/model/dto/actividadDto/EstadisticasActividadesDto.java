package com.uap.controlMedico.model.dto.actividadDto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class EstadisticasActividadesDto {
    
    // Totales globales
    private long totalRegistradas;
    private long totalCompletadas;
    private long totalPendientes;
    private long totalEnProgreso;
    private long totalCanceladas;

    // Distribución por tipo de actividad del catálogo
    private List<ConteoPorClave> porTipo;

    // Distribución por estado
    private List<ConteoPorClave> porEstado;

    // Top 5 actividades más usadas
    private List<ActividadPopular> masUtilizadas;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ConteoPorClave {
        private String clave;
        private long   cantidad;
        private double porcentaje;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ActividadPopular {
        private Long   idActividad;
        private String nombre;
        private String tipo;
        private long   vecesUsada;
    }
}
