package com.uap.controlMedico.model.dto.monitoreo;

import java.time.LocalTime;

import com.uap.controlMedico.model.enums.EstadoEntrada;
import com.uap.controlMedico.model.enums.EstadoSalida;
import com.uap.controlMedico.model.enums.MetodoRegistro;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RegistroAsistenciaDto {
    private Long           idRegistroAsistencia;
    private String         tipoPersonal;       // INTERNO | TITULAR
    private Long           idPersona;
    private String         nombrePersonal;
    private Long           idJornada;
    private String         nombreJornada;
    private LocalTime      horaEntradaEsperada;
    private LocalTime      horaSalidaEsperada;
    private LocalTime      horaEntradaReal;
    private LocalTime      horaSalidaReal;
    private EstadoEntrada  estadoEntrada;
    private EstadoSalida   estadoSalida;
    private MetodoRegistro metodoEntrada;
    private MetodoRegistro metodoSalida;
    private long           minutosTardanza;
    private boolean        tieneEntrada;
    private boolean        tieneSalida;
    private String         observaciones;
}
