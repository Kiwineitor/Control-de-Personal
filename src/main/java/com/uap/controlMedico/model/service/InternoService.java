package com.uap.controlMedico.model.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.uap.controlMedico.model.dto.internosDto.InternoRequest;
import com.uap.controlMedico.model.dto.internosDto.InternoResponse;
import com.uap.controlMedico.model.dto.internosDto.InternoResumenResponse;

public interface InternoService{
    
    InternoResponse buscarPorId(Long id);
    Page<InternoResponse> listarActivos(Pageable pageable);
    List<InternoResumenResponse> buscarPorTermino(String termino);
    List<InternoResumenResponse> findEnRotacionEnFecha(LocalDate fecha);
    List<InternoResumenResponse> findEnTurnoActual();
    List<InternoResumenResponse> findSinResponsableActivo();
    List<InternoResumenResponse> findByTitularResponsable(Long idTitular);
    void sincronizarDesdeLondra(Long id);
    void desactivar(Long id);
    String regenerarCodigoQr(Long idInterno);
    
    /* PARA EL VUE PAPI */

    Page<InternoResponse> listarConFiltros(
            String q,
            String carrera,
            String universidad,
            Boolean enRotacion,
            Boolean activo,
            Pageable pageable);
 
    InternoResponse toggleActivo(Long id);
 
    List<InternoResponse> findAllActivos();
 
    List<String> getDistinctCarreras();
    List<String> getDistinctUniversidades();
 
    // Ajuste de retorno (si ya retornan void, cambiar a DTO)
    InternoResponse crear(InternoRequest request);
    InternoResponse actualizar(Long id, InternoRequest request);

}