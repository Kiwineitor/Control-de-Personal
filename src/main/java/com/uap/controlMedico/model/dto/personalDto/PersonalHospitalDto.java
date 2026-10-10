package com.uap.controlMedico.model.dto.personalDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Vista combinada de Titular + Persona + TipoPersonal para la grilla de personal. */
@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class PersonalHospitalDto {
    private Long    idTitular;
    private Long    idPersona;
    private String  nombreCompleto;
    private String  ci;
    private String  email;
    private String  telefono;
    private String  cargo;
    private String  especialidad;
    private Long    idTipoPersonal;
    private String  tipoPersonalNombre;
    private String  tipoPersonalColor;
    private boolean esPersonalMedico;
    private boolean puedeGestionarInternos;
    private Long    idLugar;
    private String  lugarNombre;
    private boolean activo;
    // Usuario vinculado (si existe)
    private Long    idUsuario;
    private String  username;
    private boolean tieneUsuario;
}
