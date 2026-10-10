package com.uap.controlMedico.model.dto.asistenciaDto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QrEscaneoRequest {
    @NotBlank(message = "El código QR es obligatorio")
    private String codigo;
 
    @NotNull(message = "Debe indicar si es entrada o salida")
    private Boolean esEntrada;
 
    private BigDecimal latitud;
    private BigDecimal longitud;

}
