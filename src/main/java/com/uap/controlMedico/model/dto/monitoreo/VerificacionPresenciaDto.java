package com.uap.controlMedico.model.dto.monitoreo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.uap.controlMedico.model.enums.EstadoVerificacion;
import com.uap.controlMedico.model.enums.MetodoVerificacion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class VerificacionPresenciaDto {
    private Long               idVerificacionPresencia;
    private Long               idRondaVerificacion;
    private String             tipoPersonal;   // INTERNO | TITULAR
    private Long               idPersona;
    private String             nombrePersonal;
    private EstadoVerificacion estadoVerificacion;
    private MetodoVerificacion metodoVerificacion;
    private LocalDateTime      fechaHoraEnvio;
    private LocalDateTime      fechaHoraRespuesta;
    private Long               tiempoRespuestaMinutos;
    private BigDecimal         latitud;
    private BigDecimal         longitud;
    private String             fotoUrl;
    private String             observaciones;
}
