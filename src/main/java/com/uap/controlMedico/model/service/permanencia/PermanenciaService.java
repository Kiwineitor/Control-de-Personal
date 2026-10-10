package com.uap.controlMedico.model.service.permanencia;

import java.util.List;

import com.uap.controlMedico.model.dto.permanenciaDto.RondaVerificacionRequest;
import com.uap.controlMedico.model.dto.permanenciaDto.RondaVerificacionResponse;
import com.uap.controlMedico.model.dto.permanenciaDto.VerificacionPresenciaRequest;

public interface PermanenciaService {
    RondaVerificacionResponse crearRonda(RondaVerificacionRequest request);
    RondaVerificacionResponse buscarRonda(Long id);
    List<RondaVerificacionResponse> findRondasHoy();
    List<RondaVerificacionResponse> findEnCurso();
    RondaVerificacionResponse responderVerificacion(VerificacionPresenciaRequest request);
    void procesarTimeoutsVencidos();   // Llamado por @Scheduled
    void cerrarRonda(Long id);
}