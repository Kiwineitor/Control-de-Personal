package com.uap.controlMedico.model.service.titulares;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.uap.controlMedico.model.dto.titularDto.TitularRequest;
import com.uap.controlMedico.model.dto.titularDto.TitularResponse;
import com.uap.controlMedico.model.dto.titularDto.TitularResumenResponse;

public interface TitularService {

    TitularResponse buscarPorId(Long id);
    TitularResponse buscarPorPerId(Long perId);
    Page<TitularResponse> listarActivos(Pageable pageable);
    List<TitularResumenResponse> buscarPorTermino(String termino);
    List<TitularResumenResponse> findEnTurnoActual();
    List<TitularResumenResponse> findResponsablesDeInterno(Long idInterno);
    List<TitularResumenResponse> findSinTurnoEnFecha(LocalDate fecha);
    void desactivar(Long id);

    /* PARA VUE PAPI */

    // Listado paginado con filtros dinámicos (para Vue + VxeTable)
    Page<TitularResponse> listarConFiltros(
            String q,
            String cargo,
            String especialidad,
            Boolean activo,
            Pageable pageable);
 
    // Toggle activo/inactivo (retorna el DTO actualizado)
    TitularResponse toggleActivo(Long id);
 
    // Lista plana de activos (para dropdown "Asignar Responsable")
    List<TitularResponse> findAllActivos();
 
    // Valores únicos para filtros del frontend
    List<String> getDistinctCargos();
    List<String> getDistinctEspecialidades();
 
    // Ajuste: sincronizarDesdeLondra ahora retorna DTO (antes era void)
    TitularResponse sincronizarDesdeLondra(Long id);
 
    // Ajuste: crear y actualizar retornan DTO (si ya retornan void, cambiar)
    TitularResponse crear(TitularRequest request);
    TitularResponse actualizar(Long id, TitularRequest request);
}
