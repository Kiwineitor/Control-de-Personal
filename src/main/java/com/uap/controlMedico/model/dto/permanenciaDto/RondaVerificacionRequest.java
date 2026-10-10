package com.uap.controlMedico.model.dto.permanenciaDto;

import com.uap.controlMedico.model.enums.TipoRonda;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RondaVerificacionRequest {
    
    private String nombre;
    private TipoRonda tipoRonda = TipoRonda.PROGRAMADA;
    private Integer timeoutMinutos = 10;
    private String observaciones;

}
