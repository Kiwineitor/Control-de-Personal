package com.uap.controlMedico.model.dto.actividadDto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ResumenActividadesDto {
    
    private Long   idJornada;
    private String nombreJornada;
    private String fechaJornada;

    // Totales globales de la jornada
    private int    total;
    private int    pendientes;
    private int    enProgreso;
    private int    completadas;
    private int    canceladas;
    private double porcentajeCompletado;

    // Desglose por persona
    private List<DesglosePorPersona> porPersona;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DesglosePorPersona {
        private Long   idPersona;
        private String nombreCompleto;
        private String tipo;            // "INTERNO" | "TITULAR"
        private int    total;
        private int    completadas;
        private int    pendientes;
        private int    enProgreso;
    }
}
