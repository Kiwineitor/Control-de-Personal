package com.uap.controlMedico.model.entity.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContratoDto {
    private Long contratoId;
    private String puesto;
    private String unidad;
    private int gestion;
}
