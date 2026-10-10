package com.uap.controlMedico.model.dto.monitoreo;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class EscanearQrRequest {
    @NotBlank private String     codigoQr;
    @NotNull  private Long       idPersona;
    @NotBlank private String     tipoPersona;  // "INTERNO" | "TITULAR"
    private BigDecimal latitud;
    private BigDecimal longitud;
}
