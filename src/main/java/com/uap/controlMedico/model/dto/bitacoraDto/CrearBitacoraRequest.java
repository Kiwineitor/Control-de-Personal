package com.uap.controlMedico.model.dto.bitacoraDto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CrearBitacoraRequest {

    @NotNull(message = "La fecha y hora del evento es obligatoria")
    private LocalDateTime fechaHoraEvento;

    @NotBlank(message = "La descripción es obligatoria")
    private String descripcion;
}
