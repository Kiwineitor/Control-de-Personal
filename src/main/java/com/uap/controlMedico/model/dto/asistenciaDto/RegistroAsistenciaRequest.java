package com.uap.controlMedico.model.dto.asistenciaDto;

import java.math.BigDecimal;

import com.uap.controlMedico.model.enums.MetodoRegistro;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegistroAsistenciaRequest {
    
    /** UUID del código QR escaneado. Obligatorio si metodo = QR. */
    private String codigoQr;
 
    /** Obligatorio si metodo = MANUAL. */
    private Long idInternoJornada;
 
    @NotNull(message = "El método de registro es obligatorio")
    private MetodoRegistro metodoRegistro;
 
    /**
     * true  → registrar ENTRADA
     * false → registrar SALIDA
     */
    @NotNull(message = "Debe indicar si es entrada o salida")
    private Boolean esEntrada;
 
    // GPS capturado desde el dispositivo
    private BigDecimal latitud;
    private BigDecimal longitud;
 
    private String observaciones;

}
