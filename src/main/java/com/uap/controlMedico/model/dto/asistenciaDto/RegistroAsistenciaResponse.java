package com.uap.controlMedico.model.dto.asistenciaDto;

import java.math.BigDecimal;
import java.time.LocalTime;

import com.uap.controlMedico.model.dto.internosDto.InternoResumenResponse;
import com.uap.controlMedico.model.enums.EstadoEntrada;
import com.uap.controlMedico.model.enums.EstadoSalida;
import com.uap.controlMedico.model.enums.MetodoRegistro;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RegistroAsistenciaResponse {
        private Long idRegistroAsistencia;
    private InternoResumenResponse interno;
 
    // Tiempos esperados
    private LocalTime horaEntradaEsperada;
    private LocalTime horaSalidaEsperada;
 
    // Tiempos reales
    private LocalTime horaEntradaReal;
    private LocalTime horaSalidaReal;
 
    // Estados calculados
    private EstadoEntrada estadoEntrada;
    private EstadoSalida  estadoSalida;
 
    // Métodos de registro
    private MetodoRegistro metodoRegistroEntrada;
    private MetodoRegistro metodoRegistroSalida;
 
    // GPS
    private BigDecimal latitudEntrada;
    private BigDecimal longitudEntrada;
    private BigDecimal latitudSalida;
    private BigDecimal longitudSalida;
 
    // Métricas calculadas
    private long minutosTardanza;
    private boolean tieneEntrada;
    private boolean tieneSalida;
 
    private String observaciones;

}
