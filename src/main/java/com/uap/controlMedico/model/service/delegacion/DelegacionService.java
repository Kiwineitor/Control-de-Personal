package com.uap.controlMedico.model.service.delegacion;

import java.util.List;

import com.uap.controlMedico.model.dto.gestionDto.AprobacionRequest;
import com.uap.controlMedico.model.dto.gestionDto.DelegacionRequest;
import com.uap.controlMedico.model.dto.gestionDto.DelegacionResponse;

public interface DelegacionService {
    DelegacionResponse solicitar(DelegacionRequest request);
    DelegacionResponse buscarPorId(Long id);
    List<DelegacionResponse> findPendientes();
    List<DelegacionResponse> findByTitular(Long idTitular);
    List<DelegacionResponse> findByInterno(Long idInterno);
    DelegacionResponse aprobarORechazar(Long id, AprobacionRequest request);
    DelegacionResponse activar(Long id);    // Titular confirma que se va
    DelegacionResponse completar(Long id);  // Titular regresó

}
