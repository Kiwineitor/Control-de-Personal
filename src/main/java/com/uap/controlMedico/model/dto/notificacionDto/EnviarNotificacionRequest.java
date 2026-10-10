package com.uap.controlMedico.model.dto.notificacionDto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EnviarNotificacionRequest {

    /**
     * Username del destinatario.
     * Si es null o vacío → notificación broadcast a todos los usuarios.
     */
    private String usernameDestinatario;

    @NotBlank(message = "El tipo es obligatorio")
    private String tipo;

    @NotBlank(message = "El título es obligatorio")
    @Size(max = 200, message = "El título no puede superar 200 caracteres")
    private String titulo;

    @NotBlank(message = "El mensaje es obligatorio")
    private String mensaje;

    private Long idJornada;
    private Long idActividadJornada;
    private Long idSolicitud;
}