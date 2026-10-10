package com.uap.controlMedico.mapper;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.uap.controlMedico.model.dto.permisosDto.DelegacionResponse;
import com.uap.controlMedico.model.dto.permisosDto.SolicitudPermisoResponse;
import com.uap.controlMedico.model.entity.Delegacion;
import com.uap.controlMedico.model.entity.SolicitudPermiso;
import com.uap.controlMedico.shared.storage.FileStorageService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PermisosMapper {

    private final FileStorageService fileStorageService;

    public SolicitudPermisoResponse toSolicitudResponse(SolicitudPermiso s) {
        var builder = SolicitudPermisoResponse.builder()
            .idSolicitudPermiso(s.getIdSolicitudPermiso())
            .tipoPermiso(s.getTipoPermiso())
            .tipoPermisoLabel(s.getTipoPermiso() != null ? s.getTipoPermiso().getLabel() : null)
            .motivoSolicitud(s.getMotivoSolicitud())
            .fechaPermiso(s.getFechaPermiso() != null ? s.getFechaPermiso().toString() : null)
            .horaInicioSolicitada(s.getHoraInicioSolicitada() != null ? s.getHoraInicioSolicitada().toString() : null)
            .horaFinSolicitada(s.getHoraFinSolicitada() != null ? s.getHoraFinSolicitada().toString() : null)
            .estadoSolicitud(s.getEstadoSolicitud())
            .estadoSolicitudLabel(s.getEstadoSolicitud() != null ? s.getEstadoSolicitud().getLabel() : null)
            .fechaHoraSolicitud(s.getFechaHoraSolicitud())
            .fechaHoraRevision(s.getFechaHoraRevision())
            .observacionRevision(s.getObservacionRevision())
            .fechaCreacion(s.getFechaCreacion())
            .justificanteUrl(s.getJustificanteUrl())
            .justificanteDownloadUrl(fileStorageService.generarDownloadUrl(s.getJustificanteUrl()));

        // Titular
        if (s.getTitular() != null && s.getTitular().getPersona() != null) {
            builder.idTitular(s.getTitular().getIdTitular())
                   .nombreTitular(s.getTitular().getPersona().getNombre())
                   .apellidoTitular(s.getTitular().getPersona().getApellido());
        }

        // Contexto: TitularJornada tiene prioridad sobre Jornada directa
        if (s.getTitularJornada() != null) {
            builder.idTitularJornada(s.getTitularJornada().getIdTitularJornada());
            if (s.getTitularJornada().getJornada() != null) {
                builder.idJornada(s.getTitularJornada().getJornada().getIdJornada())
                       .nombreJornada(s.getTitularJornada().getJornada().getNombre());
            }
            if (s.getTitularJornada().getLugar() != null) {
                builder.lugarJornada(s.getTitularJornada().getLugar().getNombre());
            }
        } else if (s.getJornada() != null) {
            builder.idJornada(s.getJornada().getIdJornada())
                   .nombreJornada(s.getJornada().getNombre());
        }

        // Revisor
        if (s.getRevisadoPor() != null) {
            builder.revisadoPor(s.getRevisadoPor().getUsername());
        }

        // Delegación resultante
        if (s.getDelegacion() != null) {
            var d = s.getDelegacion();
            builder.idDelegacion(d.getIdDelegacion())
                   .estadoDelegacion(d.getEstado())
                   .estadoDelegacionLabel(d.getEstado() != null ? d.getEstado().getLabel() : null);
            if (d.getInternoResponsable() != null && d.getInternoResponsable().getPersona() != null) {
                builder.internoAsignado(d.getInternoResponsable().getPersona().getNombreCompleto());
            }
        }

        return builder.build();
    }

    public DelegacionResponse toDelegacionResponse(Delegacion d) {
        var builder = DelegacionResponse.builder()
            .idDelegacion(d.getIdDelegacion())
            .motivo(d.getMotivo())
            .estado(d.getEstado())
            .estadoLabel(d.getEstado() != null ? d.getEstado().getLabel() : null)
            .horaInicioPlanificada(d.getHoraInicioPlanificada() != null ? d.getHoraInicioPlanificada().toString() : null)
            .horaFinPlanificada(d.getHoraFinPlanificada() != null ? d.getHoraFinPlanificada().toString() : null)
            .horaInicioReal(d.getHoraInicioReal() != null ? d.getHoraInicioReal().toString() : null)
            .horaFinReal(d.getHoraFinReal() != null ? d.getHoraFinReal().toString() : null)
            .fechaAprobacion(d.getFechaAprobacion())
            .observacionAprobacion(d.getObservacionAprobacion())
            .fechaCreacion(d.getFechaCreacion());

        // Duración planificada en minutos
        if (d.getHoraInicioPlanificada() != null && d.getHoraFinPlanificada() != null) {
            builder.duracionPlanificadaMinutos(
                (int) Duration.between(d.getHoraInicioPlanificada(), d.getHoraFinPlanificada()).toMinutes()
            );
        }

        // TitularJornada → Titular + Jornada + Lugar
        if (d.getTitularJornada() != null) {
            builder.idTitularJornada(d.getTitularJornada().getIdTitularJornada());

            if (d.getTitularJornada().getTitular() != null) {
                var t = d.getTitularJornada().getTitular();
                builder.idTitular(t.getIdTitular());
                if (t.getPersona() != null) {
                    builder.nombreTitular(t.getPersona().getNombreCompleto());
                }
            }
            if (d.getTitularJornada().getJornada() != null) {
                builder.idJornada(d.getTitularJornada().getJornada().getIdJornada())
                       .nombreJornada(d.getTitularJornada().getJornada().getNombre());
            }
            if (d.getTitularJornada().getLugar() != null) {
                builder.lugarNombre(d.getTitularJornada().getLugar().getNombre());
            }
        }

        // Interno responsable
        if (d.getInternoResponsable() != null) {
            builder.idInternoResponsable(d.getInternoResponsable().getIdInterno());
            if (d.getInternoResponsable().getPersona() != null) {
                builder.nombreInterno(d.getInternoResponsable().getPersona().getNombreCompleto());
            }
        }

        // Aprobador
        if (d.getAprobadoPor() != null) {
            builder.aprobadoPor(d.getAprobadoPor().getUsername());
        }

        // Solicitud de permiso origen
        if (d.getSolicitudPermiso() != null) {
            builder.idSolicitudPermiso(d.getSolicitudPermiso().getIdSolicitudPermiso())
                   .tipoPermiso(d.getSolicitudPermiso().getTipoPermiso())
                   .tipoPermisoLabel(d.getSolicitudPermiso().getTipoPermiso() != null
                       ? d.getSolicitudPermiso().getTipoPermiso().getLabel() : null);
        }

        return builder.build();
    }
}
