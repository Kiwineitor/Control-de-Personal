package com.uap.controlMedico.model.dto.monitoreo;

import java.time.LocalDateTime;

import com.uap.controlMedico.model.enums.TipoRonda;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data @NoArgsConstructor @AllArgsConstructor
public class RondaVerificacionRequest {
    private String        nombre;
    private TipoRonda     tipoRonda = TipoRonda.ALEATORIA;
    @NotNull(message = "La fecha/hora de inicio es obligatoria")
    private LocalDateTime fechaHoraInicio;
    private Integer       timeoutMinutos = 10;
    private Long          idJornada;  // null → toma todo el personal en jornadas activas
    private String        observaciones;
}
