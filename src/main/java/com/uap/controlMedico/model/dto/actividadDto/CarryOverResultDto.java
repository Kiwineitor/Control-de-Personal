package com.uap.controlMedico.model.dto.actividadDto;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CarryOverResultDto {
    private int          totalPendientes;
    private int          traspasados;
    private int          sinJornadaDestino;
    private List<String> detalles;
}
