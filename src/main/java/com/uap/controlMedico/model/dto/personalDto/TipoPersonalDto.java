package com.uap.controlMedico.model.dto.personalDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class TipoPersonalDto {
    private Long    idTipoPersonal;
    private String  nombre;
    private String  descripcion;
    private String  color;
    private boolean esPersonalMedico;
    private boolean puedeGestionarInternos;
    private boolean activo;
    private int     totalPersonal;   // cuántos titulares tienen este tipo
}
