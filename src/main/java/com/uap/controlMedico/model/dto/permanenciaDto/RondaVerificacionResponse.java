package com.uap.controlMedico.model.dto.permanenciaDto;

import java.time.LocalDateTime;
import java.util.List;

import com.uap.controlMedico.model.enums.EstadoRonda;
import com.uap.controlMedico.model.enums.TipoRonda;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RondaVerificacionResponse {
    
    private Long idRondaVerificacion;
    private String nombre;
    private TipoRonda tipoRonda;
    private EstadoRonda estadoRonda;
    private LocalDateTime fechaHoraInicio;
    private LocalDateTime fechaHoraFin;
    private Integer timeoutMinutos;
    private String creadaPor;
 
    // Estadísticas de la ronda
    private long totalVerificaciones;
    private long totalPresentes;
    private long totalAusentes;
    private long totalSinRespuesta;
    private long totalPendientes;
 
    private List<VerificacionPresenciaResponse> verificaciones;

}
