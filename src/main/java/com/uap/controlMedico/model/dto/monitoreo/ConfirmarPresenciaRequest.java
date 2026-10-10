package com.uap.controlMedico.model.dto.monitoreo;

import java.math.BigDecimal;

import com.uap.controlMedico.model.enums.MetodoVerificacion;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class ConfirmarPresenciaRequest {
    @NotNull private MetodoVerificacion metodo;
    private BigDecimal latitud;
    private BigDecimal longitud;
    private String     fotoUrl;
    private String     observaciones;
}
