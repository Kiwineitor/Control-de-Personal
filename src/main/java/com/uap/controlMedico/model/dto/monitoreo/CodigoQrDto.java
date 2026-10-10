package com.uap.controlMedico.model.dto.monitoreo;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CodigoQrDto {
    private Long          idCodigoQr;
    private Long          idLugar;
    private String        nombreLugar;
    private String        descripcionLugar;
    private String        codigo;
    private boolean       activo;
    private boolean       esValido;
    private LocalDateTime fechaExpiracion;
    private String        imagenUrl;
    private LocalDateTime fechaCreacion;
}
