package com.uap.controlMedico.model.dto.actividadDto;

import com.uap.controlMedico.model.enums.TipoActividad;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ActividadCatalogoDto {
    private Long          idActividad;
    private String        nombre;
    private String        descripcion;
    private TipoActividad tipoActividad;
}
