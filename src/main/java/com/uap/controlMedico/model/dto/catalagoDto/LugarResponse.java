package com.uap.controlMedico.model.dto.catalagoDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.uap.controlMedico.model.enums.TipoLugar;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LugarResponse {
    
    private Long idLugar;
    private String nombre;
    private String descripcion;
    private TipoLugar tipoLugar;
    private boolean activo;

    private BigDecimal    latitudCentro;
    private BigDecimal    longitudCentro;
    private Integer       radioMetros;
    private boolean       tieneGeofencing;
    private LocalDateTime fechaCreacion;

}
