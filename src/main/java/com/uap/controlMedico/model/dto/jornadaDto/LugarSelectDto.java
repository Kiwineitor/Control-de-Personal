package com.uap.controlMedico.model.dto.jornadaDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class LugarSelectDto {
    private Long   idLugar;
    private String nombre;
    private String descripcion;
}
