package com.uap.controlMedico.model.dto.rol;

import java.time.LocalDateTime;

public record RolResponse(

    Long          idRol,
    String        nombre,
    String        estado,
    long          cantidadUsuarios,   // COUNT desde query, nunca lazy
    LocalDateTime fechaCreacion
) {}
