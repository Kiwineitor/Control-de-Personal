package com.uap.controlMedico.model.dto.rolturnoDto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class RolTurnoDto {
    private Long            idRolTurno;
    private Long            idTitularJornada;
    private Long            idJornada;
    private String          nombreJornada;
    private Long            idTitular;
    private String          nombreTitular;
    private String          cargo;
    private Integer         mes;
    private Integer         anio;
    private String          observaciones;
    private boolean         activo;
    private List<DiaTurnoDto> dias;
    // Horario base de la jornada (para referencia en UI)
    private String          horaInicioJornada;
    private String          horaFinJornada;
}
