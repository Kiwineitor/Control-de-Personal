package com.uap.controlMedico.model.dto.jornadaDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class InternoSelectDto {
    private Long    idInterno;
    private String  nombreCompleto;
    private String  carrera;
    private String  universidad;
    private boolean estaEnRotacion;
}
