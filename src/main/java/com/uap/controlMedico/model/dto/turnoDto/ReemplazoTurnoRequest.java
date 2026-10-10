package com.uap.controlMedico.model.dto.turnoDto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReemplazoTurnoRequest {
    @NotNull(message = "La jornada es obligatoria")
    private Long idJornada;

    @NotNull(message = "La fecha es obligatoria")
    private LocalDate fechaReemplazo;

    @NotNull(message = "El titular original es obligatorio")
    private Long idOriginal;

    @NotNull(message = "El reemplazante es obligatorio")
    private Long idReemplazo;

    @NotBlank(message = "El motivo es obligatorio")
    private String motivo;
}
