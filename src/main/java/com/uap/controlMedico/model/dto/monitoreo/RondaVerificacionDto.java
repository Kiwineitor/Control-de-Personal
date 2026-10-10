package com.uap.controlMedico.model.dto.monitoreo;

import java.time.LocalDateTime;

import com.uap.controlMedico.model.enums.EstadoRonda;
import com.uap.controlMedico.model.enums.TipoRonda;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RondaVerificacionDto {
    private Long          idRondaVerificacion;
    private String        nombre;
    private TipoRonda     tipoRonda;
    private EstadoRonda   estado;
    private LocalDateTime fechaHoraInicio;
    private LocalDateTime fechaHoraFin;
    private Integer       timeoutMinutos;
    private String        observaciones;
    // Conteos resumen
    private int           totalVerificaciones;
    private int           totalPresentes;
    private int           totalAusentes;
    private int           totalSinRespuesta;
    private int           totalPendientes;
    private double        porcentajePresencia;
    private LocalDateTime fechaCreacion;
}
