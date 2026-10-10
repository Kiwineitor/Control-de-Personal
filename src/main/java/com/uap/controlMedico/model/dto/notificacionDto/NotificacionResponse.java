package com.uap.controlMedico.model.dto.notificacionDto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.uap.controlMedico.model.entity.Notificacion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class NotificacionResponse {

    private Long    idNotificacion;
    private String  tipo;
    private String  titulo;
    private String  mensaje;
    private String  timestamp;
    private boolean leida;
    private String  fechaLectura;
    private Long    idJornada;
    private Long    idActividadJornada;
    private Long    idSolicitud;
    private boolean esGlobal;
    private String  remitenteUsername;

    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public static NotificacionResponse from(Notificacion n) {
        return NotificacionResponse.builder()
            .idNotificacion(n.getIdNotificacion())
            .tipo(n.getTipo())
            .titulo(n.getTitulo())
            .mensaje(n.getMensaje())
            .timestamp(n.getTimestamp().format(FMT))
            .leida(n.isLeida())
            .fechaLectura(n.getFechaLectura() != null
                ? n.getFechaLectura().format(FMT) : null)
            .idJornada(n.getIdJornada())
            .idActividadJornada(n.getIdActividadJornada())
            .idSolicitud(n.getIdSolicitud())
            .esGlobal(n.isEsGlobal())
            .remitenteUsername(n.getRemitente() != null
                ? n.getRemitente().getUsername() : null)
            .build();
    }
}