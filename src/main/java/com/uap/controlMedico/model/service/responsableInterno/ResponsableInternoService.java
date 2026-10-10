package com.uap.controlMedico.model.service.responsableInterno;

import java.util.List;

import com.uap.controlMedico.model.dto.responsableInterno.AsignarResponsableRequest;
import com.uap.controlMedico.model.dto.responsableInterno.ResponsableResponseDto;
import com.uap.controlMedico.model.dto.responsableInterno.ResponsableResumenDto;

public interface ResponsableInternoService {
        /** Retorna la responsable activa actual, o null si no hay ninguna. */
    ResponsableResponseDto findActivo();

     /** Responsable actual + sus internos asignados. Null si no hay. */
    ResponsableResumenDto findResponsableActual();
 
    /** Historial completo ordenado por fecha DESC. */
    List<ResponsableResponseDto> findHistorial();
 
    /**
     * Asigna un titular como responsable de internos.
     * Si request.asignarTodos = true → asigna a todos los internos activos.
     * Si false → asigna solo a los IDs en request.idInternos.
     * Desactiva asignaciones anteriores de esos internos.
     */
    List<ResponsableResponseDto> asignar(AsignarResponsableRequest request);

    /** Desactiva la asignación de un interno específico. */
    void desasignar(Long idResponsableInterno);
}