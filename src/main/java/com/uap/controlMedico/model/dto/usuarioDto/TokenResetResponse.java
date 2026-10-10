package com.uap.controlMedico.model.dto.usuarioDto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Setter;

@Setter @Builder
public class TokenResetResponse {
    private String        token;
    private LocalDateTime expiry;
    private String        mensaje;
}
