package com.uap.controlMedico.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "app")
public class ServerFileConfig {
    private String ipServidor;
    private String usuarioServidor;
    private String contrasenaServidor;
    private String carpetaCompartida;
}
