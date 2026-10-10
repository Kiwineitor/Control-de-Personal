package com.uap.controlMedico.model.service.actividad;

import java.io.IOException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import com.uap.controlMedico.model.dto.actividadDto.ActividadJornadaResponse;
import com.uap.controlMedico.model.dto.actividadDto.CarryOverResultDto;
import com.uap.controlMedico.model.dto.actividadDto.CulminarActividadRequest;
import com.uap.controlMedico.model.dto.actividadDto.SubirEvidenciaResponse;

public interface ActividadJornadaV2Service {
    /** Llama al cerrar una jornada — transpasa actividades pendientes a la próxima jornada */
    CarryOverResultDto ejecutarCarryOver(Long idJornada);

    /** Sube (o reemplaza) la evidencia de una ActividadJornada */
    SubirEvidenciaResponse subirEvidencia(Long idActividadJornada, MultipartFile file)
        throws IOException;

    /** Marca una actividad como culminada */
    ActividadJornadaResponse culminarActividad(Long idActividadJornada,
                                               CulminarActividadRequest request);

    /** Listado paginado de asignaciones de una jornada */
    Page<ActividadJornadaResponse> listarPorJornada(Long idJornada,
                                                    Boolean culminada,
                                                    Pageable pageable);

    ActividadJornadaResponse findById(Long idActividadJornada);
}
