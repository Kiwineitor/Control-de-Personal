package com.uap.controlMedico.model.dto.permanenciaDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.uap.controlMedico.model.dto.internosDto.InternoResumenResponse;
import com.uap.controlMedico.model.dto.titularDto.TitularResumenResponse;
import com.uap.controlMedico.model.enums.EstadoVerificacion;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VerificacionPresenciaResponse {
    
    private Long idVerificacionPresencia;
 
    // Personal verificado (uno de los dos vendrá con datos)
    private TitularResumenResponse titular;
    private InternoResumenResponse interno;
    private String tipoPersonal;     // "TITULAR" o "INTERNO"
    private String nombrePersonal;
 
    private EstadoVerificacion estadoVerificacion;
    private LocalDateTime fechaHoraEnvio;
    private LocalDateTime fechaHoraRespuesta;
    private Long tiempoRespuestaMinutos;
 
    private BigDecimal latitud;
    private BigDecimal longitud;
    private String fotoUrl;
    private String observaciones;

}
