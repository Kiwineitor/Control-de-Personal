package com.uap.controlMedico.model.dto.monitoreo;

import java.math.BigDecimal;

import com.uap.controlMedico.model.enums.EstadoEvento;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class EventoAbandonoDto {
    private Long       idEvento;
    private Long       idSesion;
    private String     nombrePersonal;
    private String     cargo;
    private String     tipoPersonal;
    private Long       idLugar;
    private String     nombreLugar;
    private String     timestampSalida;
    private String     timestampAbandono;
    private String     timestampRetorno;
    private Long       tiempoFueraMinutos;
    private BigDecimal distanciaAlSalirMetros;
    private EstadoEvento estado;
    private boolean    notificadoAdmin;
    private boolean    resuelto;
    private String     observaciones;
}
