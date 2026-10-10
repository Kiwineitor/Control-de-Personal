package com.uap.controlMedico.model.dto.rolturnoDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class DiaTurnoDto {
    private Long   idDiaTurno;
    private String fecha;        // ISO: yyyy-MM-dd
    private String horaInicio;   // HH:mm (null = usa hora de la jornada)
    private String horaFin;      // HH:mm (null = usa hora de la jornada)
    private String observaciones;
}
