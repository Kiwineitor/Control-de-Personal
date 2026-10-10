package com.uap.controlMedico.model.dto.monitoreo;

import com.uap.controlMedico.model.enums.EstadoEvento;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Payload enviado por STOMP al topic /topic/admin/alertas-monitoreo. */
@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class AlertaMonitoreoDto {
    private String       tipo;          // "SALIDA_ZONA" | "ABANDONO" | "RETORNO"
    private Long         idEvento;
    private Long         idSesion;
    private String       nombrePersonal;
    private String       nombreLugar;
    private EstadoEvento estado;
    private Long         tiempoFueraMinutos;
    private String       timestamp;
}
