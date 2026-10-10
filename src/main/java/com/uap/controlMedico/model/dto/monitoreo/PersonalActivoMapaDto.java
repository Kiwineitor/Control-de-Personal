package com.uap.controlMedico.model.dto.monitoreo;

import java.math.BigDecimal;

import com.uap.controlMedico.model.enums.EstadoZona;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class PersonalActivoMapaDto {
    private Long       idSesion;
    private String     nombrePersonal;
    private String     cargo;
    private String     tipoPersonal;
    private Long       idLugar;
    private String     nombreLugar;
    private BigDecimal latitudLugar;
    private BigDecimal longitudLugar;
    private Integer    radioMetrosLugar;
    private BigDecimal latitudPersonal;
    private BigDecimal longitudPersonal;
    private BigDecimal distanciaMetros;
    private EstadoZona estadoZona;
    private Long       tiempoFueraMinutos;
    private String     ultimaActualizacion;
}
