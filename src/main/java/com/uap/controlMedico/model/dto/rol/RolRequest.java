package com.uap.controlMedico.model.dto.rol;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RolRequest (

    @NotBlank(message = "El nombre del rol es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
    @Pattern(
        regexp = "^[A-ZÁÉÍÓÚÑ_]+$",
        message = "El nombre solo puede contener letras mayúsculas y guión bajo (ej: SUPER_ADMIN)"
    )
    String nombre

) {}