package com.uap.controlMedico.model.dto.rolturnoDto;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RolTurnoRequest {
    @NotNull
    private Long idTitularJornada;
    @NotNull @Min(1) @Max(12)
    private Integer mes;
    @NotNull @Min(2024)
    private Integer anio;
    private String observaciones;
    /** Lista de fechas ISO (yyyy-MM-dd) con horas opcionales. */
    @NotNull
    private List<DiaTurnoRequest> dias;

    @Data
    public static class DiaTurnoRequest {
        @NotNull
        private String fecha;       // yyyy-MM-dd
        private String horaInicio;  // HH:mm (opcional)
        private String horaFin;     // HH:mm (opcional)
        private String observaciones;
    }
}
