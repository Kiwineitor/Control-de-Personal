package com.uap.controlMedico.model.dto.jornadaDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TitularSelectDto {
    private Long   idTitular;
    private String nombreCompleto;
    private String cargo;
}
