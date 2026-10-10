package com.uap.controlMedico.model.dto.permanenciaDto;

import java.math.BigDecimal;

import com.uap.controlMedico.model.enums.EstadoVerificacion;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerificacionPresenciaRequest {
    
    @NotNull(message = "El ID de verificación es obligatorio")
    private Long idVerificacionPresencia;
 
    @NotNull(message = "El estado de verificación es obligatorio")
    private EstadoVerificacion estadoVerificacion;
 
    // GPS desde el dispositivo del personal
    private BigDecimal latitud;
    private BigDecimal longitud;
 
    // URL de la foto tomada (sube al servidor SMB previamente)
    private String fotoUrl;
 
    private String observaciones;

}
