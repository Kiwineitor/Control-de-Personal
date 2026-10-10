package com.uap.controlMedico.model.dto.responsableInterno;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponsableInternoDto {
    private Long idResponsableInterno;
    private Long idTitular;
    private Long idInterno;
    private String nombreTitular;
    private String cargoTitular;
    private LocalDate fechaAsignacion;
    private LocalDate fechaFinAsignacion;
    private String observaciones;
    private boolean activo;
}
