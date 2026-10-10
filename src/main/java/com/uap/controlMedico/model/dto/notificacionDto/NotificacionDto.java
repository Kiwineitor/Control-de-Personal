package com.uap.controlMedico.model.dto.notificacionDto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NotificacionDto {

    /** ID de BD — se llena después de persistir, antes de enviar por WS. */
    private Long   idNotificacion;

    private String tipo;
    private String titulo;
    private String mensaje;
    private String timestamp;
    private Long   idSolicitud;
    private Long   idJornada;
    private Long   idActividadJornada;
    private String nombreTitular;
    private String tipoPermiso;
    /** true → broadcast global (todos los usuarios lo reciben). */
    private boolean esGlobal;

    public static String ahora() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}