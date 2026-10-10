package com.uap.controlMedico.model.dto.bitacoraDto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BitacoraEntradaResponse {
    private Long      idBitacora;
    private LocalDateTime fechaHoraEvento;
    private String    descripcion;
    private String    evidenciaUrl;
    private String    evidenciaDownloadUrl;
    private String    nombreJornada;
    private String    nombreLugar;
    private String    nombrePersonal;
    private LocalDateTime createdAt;
}
