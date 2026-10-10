package com.uap.controlMedico.model.dto.turnoDto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HistorialCambioDto {
    private Long idHistorial;
    private String entidad;
    private Long idEntidad;
    private String campo;
    private String valorAnterior;
    private String valorNuevo;
    private String accion;
    private String usuario;
    private LocalDateTime fechaCambio;
}
