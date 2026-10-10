package com.uap.controlMedico.model.dto.monitoreo;

import java.math.BigDecimal;

import com.uap.controlMedico.model.enums.MetodoRegistro;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class RegistrarMovimientoRequest {
    private Long           idInternoJornada;   // uno de los dos
    private Long           idTitularJornada;
    private MetodoRegistro metodo;
    private BigDecimal     latitud;
    private BigDecimal     longitud;
    private String         observaciones;
}
