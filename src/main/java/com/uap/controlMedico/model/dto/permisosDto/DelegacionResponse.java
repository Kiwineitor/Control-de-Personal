package com.uap.controlMedico.model.dto.permisosDto;

import java.time.LocalDateTime;

import com.uap.controlMedico.model.enums.EstadoDelegacion;
import com.uap.controlMedico.model.enums.TipoPermiso;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data @Builder
@NoArgsConstructor @AllArgsConstructor
public class DelegacionResponse {
    
    private Long idDelegacion;

    // Titular que delega
    private Long   idTitularJornada;
    private Long   idTitular;
    private String nombreTitular;
    private Long   idJornada;
    private String nombreJornada;
    private String lugarNombre;

    // Interno que cubre
    private Long   idInternoResponsable;
    private String nombreInterno;

    // Horarios planificados vs reales
    private String  horaInicioPlanificada;
    private String  horaFinPlanificada;
    private String  horaInicioReal;
    private String  horaFinReal;
    private Integer duracionPlanificadaMinutos;

    // Motivo y estado
    private String          motivo;
    private EstadoDelegacion estado;
    private String           estadoLabel;

    // Aprobación
    private String        aprobadoPor;
    private LocalDateTime fechaAprobacion;
    private String        observacionAprobacion;

    // Vínculo con solicitud de permiso
    private Long        idSolicitudPermiso;
    private TipoPermiso tipoPermiso;
    private String      tipoPermisoLabel;

    // Audit
    private LocalDateTime fechaCreacion;
}
