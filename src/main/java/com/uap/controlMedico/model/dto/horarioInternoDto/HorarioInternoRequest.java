package com.uap.controlMedico.model.dto.horarioInternoDto;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class HorarioInternoRequest {

    @NotNull
    private Long idInternoJornada;

    @NotNull @Min(1) @Max(12)
    private Integer mes;

    @NotNull @Min(2024)
    private Integer anio;

    private String observaciones;

    @NotNull
    private List<DiaHorarioRequest> dias;

    @Data
    public static class DiaHorarioRequest {
        @NotNull
        private String fecha;        // yyyy-MM-dd
        private String horaInicio;   // HH:mm (opcional, sobreescribe hora de jornada)
        private String horaFin;      // HH:mm (opcional)
        private String observaciones;
    }
}
