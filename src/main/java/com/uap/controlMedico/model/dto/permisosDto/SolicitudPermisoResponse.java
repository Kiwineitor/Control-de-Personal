package com.uap.controlMedico.model.dto.permisosDto;

import java.time.LocalDateTime;

import com.uap.controlMedico.model.enums.EstadoDelegacion;
import com.uap.controlMedico.model.enums.EstadoSolicitud;
import com.uap.controlMedico.model.enums.TipoPermiso;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder
@NoArgsConstructor @AllArgsConstructor
public class SolicitudPermisoResponse {
    
    private Long   idSolicitudPermiso;

    // Titular
    private Long   idTitular;
    private String nombreTitular;
    private String apellidoTitular;

    // Contexto jornada
    private Long   idTitularJornada;
    private Long   idJornada;
    private String nombreJornada;
    private String lugarJornada;

    // Detalle permiso
    private TipoPermiso tipoPermiso;
    private String      tipoPermisoLabel;
    private String      motivoSolicitud;
    private String      fechaPermiso;          // yyyy-MM-dd
    private String      horaInicioSolicitada;  // HH:mm
    private String      horaFinSolicitada;     // HH:mm

    // Estado
    private EstadoSolicitud estadoSolicitud;
    private String          estadoSolicitudLabel;
    private LocalDateTime   fechaHoraSolicitud;

    // Revisión
    private String        revisadoPor;
    private LocalDateTime fechaHoraRevision;
    private String        observacionRevision;

    // Delegación resultante
    private Long            idDelegacion;
    private EstadoDelegacion estadoDelegacion;
    private String           estadoDelegacionLabel;
    private String           internoAsignado;   // nombre del interno en la delegación

    // Audit
    private LocalDateTime fechaCreacion;

    private String justificanteUrl;
    private String justificanteDownloadUrl;
}
