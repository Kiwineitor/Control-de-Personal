package com.uap.controlMedico.model.dto.actividadDto;

import java.time.LocalDateTime;

import com.uap.controlMedico.model.enums.EstadoCatalogo;
import com.uap.controlMedico.model.enums.TipoActividad;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ActividadDto {
    private Long          idActividad;
    private String        nombre;
    private String        descripcion;
    private TipoActividad tipoActividad;
    private EstadoCatalogo estado;
    private boolean       activo;
    private long          vecesUsada;    // cuántas veces aparece en ActividadJornada
    private LocalDateTime fechaCreacion;
}
