package com.uap.controlMedico.model.dto.lugarDto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// public record LugarRequest(

//     @NotBlank(message = "El nombre del lugar es obligatorio")
//     @Size(max = 150, message = "El nombre no puede superar 150 caracteres")
//     String nombre,

//     @Size(max = 500, message = "La descripción no puede superar 500 caracteres")
//     String descripcion,

//     /** null en creación → default true en entidad */
//     Boolean activo,

//     @DecimalMin(value = "-90.0",  message = "Latitud inválida (rango: -90 a 90)")
//     @DecimalMax(value =  "90.0",  message = "Latitud inválida (rango: -90 a 90)")
//     Double latitudCentro,

//     @DecimalMin(value = "-180.0", message = "Longitud inválida (rango: -180 a 180)")
//     @DecimalMax(value =  "180.0", message = "Longitud inválida (rango: -180 a 180)")
//     Double longitudCentro,

//     @Positive(message = "El radio debe ser un valor positivo en metros")
//     Double radioMetros
// ) {
    
// }
