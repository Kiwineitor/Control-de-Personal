package com.uap.controlMedico.model.dto.monitoreo;

import java.time.LocalDateTime;
import java.util.List;

import com.uap.controlMedico.model.enums.EstadoRonda;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ResumenRondaDto {
    private Long          idRondaVerificacion;
    private String        nombre;
    private EstadoRonda   estado;
    private LocalDateTime fechaHoraInicio;
    private int           total;
    private int           presentes;
    private int           ausentes;
    private int           sinRespuesta;
    private int           pendientes;
    private double        porcentajePresencia;
    private List<VerificacionPresenciaDto> verificaciones;
}
