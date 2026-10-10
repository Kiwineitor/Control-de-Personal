package com.uap.controlMedico.model.entity.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class UsuarioDto {
    private int perId;
    private int userId;
    private String nombre;
    private String password;
}