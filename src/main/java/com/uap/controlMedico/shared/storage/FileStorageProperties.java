package com.uap.controlMedico.shared.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@ConfigurationProperties(prefix = "sisu.storage")
@Validated
@Getter
@Setter
public class FileStorageProperties {
    
    @NotBlank
    private String path;

    @NotBlank
    private String baseUrl;
}
