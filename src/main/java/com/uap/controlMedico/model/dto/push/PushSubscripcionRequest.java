package com.uap.controlMedico.model.dto.push;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PushSubscripcionRequest {
    @NotBlank String endpoint;
    /** Web Push: clave pública del cliente. FCM: se envía vacío. */
    String p256dh;
    /** Web Push: secreto de autenticación. FCM: se envía vacío. */
    String auth;
    String userAgent;
    /** "WEBPUSH" (navegador) o "FCM" (app nativa). Si viene vacío se infiere. */
    String platform;
}
