package com.uap.controlMedico.mapper;

import org.springframework.stereotype.Component;

import com.uap.controlMedico.model.dto.actividadDto.ActividadJornadaResponse;
import com.uap.controlMedico.model.entity.ActividadJornada;
import com.uap.controlMedico.shared.storage.FileStorageService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ActividadJornadaMapper {
    
    private final FileStorageService fileStorageService;

    public ActividadJornadaResponse toResponse(ActividadJornada aj) {

        String nombreInterno    = null;
        Long   idInternoJornada = null;
        Long   idJornada        = null;
        String nombreJornada    = null;

        if (aj.getInternoJornada() != null) {
            idInternoJornada = aj.getInternoJornada().getIdInternoJornada();
            var p = aj.getInternoJornada().getInterno().getPersona();
            nombreInterno = p.getApellido() + " " + p.getNombre();
            if (aj.getInternoJornada().getJornada() != null) {
                idJornada     = aj.getInternoJornada().getJornada().getIdJornada();
                nombreJornada = aj.getInternoJornada().getJornada().getNombre();
            }
        }

        String nombreTitular    = null;
        Long   idTitularJornada = null;
        if (aj.getTitularJornada() != null) {
            idTitularJornada = aj.getTitularJornada().getIdTitularJornada();
            var p = aj.getTitularJornada().getTitular().getPersona();
            nombreTitular = p.getApellido() + " " + p.getNombre();
            if (idJornada == null && aj.getTitularJornada().getJornada() != null) {
                idJornada     = aj.getTitularJornada().getJornada().getIdJornada();
                nombreJornada = aj.getTitularJornada().getJornada().getNombre();
            }
        }

        String nombreValidador = null;
        if (aj.getValidadoPor() != null) {
            var p = aj.getValidadoPor().getPersona();
            nombreValidador = p.getApellido() + " " + p.getNombre();
        }

        return ActividadJornadaResponse.builder()
            .idActividadJornada(aj.getIdActividadJornada())
            .idActividad(aj.getActividad() != null ? aj.getActividad().getIdActividad()  : null)
            .nombreActividad(aj.getActividad() != null ? aj.getActividad().getNombre()   : null)
            .tipoActividad(aj.getActividad() != null ? aj.getActividad().getTipoActividad() : null)
            .idJornada(idJornada)
            .nombreJornada(nombreJornada)
            .idInternoJornada(idInternoJornada)
            .nombreInterno(nombreInterno)
            .idTitularJornada(idTitularJornada)
            .nombreTitular(nombreTitular)
            .estadoActividad(aj.getEstadoActividad())
            .culminada(aj.isCulminada())
            .fechaCulminacion(aj.getFechaCulminacion())
            .observaciones(aj.getObservaciones())
            .evidenciaUrl(aj.getEvidenciaUrl())
            .evidenciaDownloadUrl(fileStorageService.generarDownloadUrl(aj.getEvidenciaUrl()))
            .validadoPor(nombreValidador)
            .fechaValidacion(aj.getFechaValidacion())
            .carryOverDeId(aj.getCarryOverDe() != null ? aj.getCarryOverDe().getIdActividadJornada() : null)
            .esCarryOver(aj.esCarryOver())
            .horaInicio(aj.getHoraInicio())
            .horaFin(aj.getHoraFin())
            .fechaCreacion(aj.getFechaCreacion())
            .build();
    }
}
