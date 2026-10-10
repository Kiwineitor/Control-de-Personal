package com.uap.controlMedico.model.dto.monitoreo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class PersonalEnTurnoDto {
    private Long   idPersonal;
    private String tipoPersonal;   // "TITULAR" | "INTERNO"
    private String nombrePersonal;
    private String cargo;
    private Long   idJornada;
    private String nombreJornada;
    private Long   idLugar;
    private String nombreLugar;
    private String horaInicio;
    private String horaFin;
    private boolean tieneSesionActiva;
}
