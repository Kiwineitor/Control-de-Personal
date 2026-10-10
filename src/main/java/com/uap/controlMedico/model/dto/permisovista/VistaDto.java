package com.uap.controlMedico.model.dto.permisovista;

import lombok.Builder;
import lombok.Getter;

/** Una vista del catálogo (opción de menú asignable). */
@Getter
@Builder
public class VistaDto {
    private Long    idVista;
    private String  codigo;
    private String  nombre;
    private String  grupo;
    private Integer orden;
    private boolean activo;
}
