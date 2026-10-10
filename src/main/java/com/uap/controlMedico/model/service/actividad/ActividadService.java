package com.uap.controlMedico.model.service.actividad;


import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.uap.controlMedico.model.dto.actividadDto.ActividadDto;
import com.uap.controlMedico.model.dto.actividadDto.ActividadJornadaDto;
import com.uap.controlMedico.model.dto.actividadDto.CambiarEstadoActividadRequest;
import com.uap.controlMedico.model.dto.actividadDto.EstadisticasActividadesDto;
import com.uap.controlMedico.model.dto.actividadDto.ResumenActividadesDto;
import com.uap.controlMedico.model.dto.actividadDto.ValidarActividadRequest;
import com.uap.controlMedico.model.dto.catalagoDto.ActividadRequest;
import com.uap.controlMedico.model.dto.jornadaDto.ActividadJornadaRequest;
import com.uap.controlMedico.model.enums.EstadoCatalogo;
import com.uap.controlMedico.model.enums.TipoActividad;

public interface ActividadService{
    
    // ── Catálogo ──────────────────────────────────────────────────────────
    Page<ActividadDto>   listarConFiltros(String q, TipoActividad tipo,
                                          EstadoCatalogo estado, Pageable pageable);
    ActividadDto         buscarPorId(Long id);
    ActividadDto         crear(ActividadRequest request);
    ActividadDto         actualizar(Long id, ActividadRequest request);
    void                 desactivar(Long id);
    List<ActividadDto>   getCatalogoActivo();

    // ── Asignaciones ──────────────────────────────────────────────────────
    ActividadJornadaDto  asignar(ActividadJornadaRequest request);
    ActividadJornadaDto  cambiarEstado(Long id, CambiarEstadoActividadRequest req);
    ActividadJornadaDto  validar(Long id, ValidarActividadRequest req);
    void                 remover(Long id);

    // ── Consultas ─────────────────────────────────────────────────────────
    List<ActividadJornadaDto> findByInternoJornada(Long idInternoJornada);
    List<ActividadJornadaDto> findByTitularJornada(Long idTitularJornada);
    List<ActividadJornadaDto> findByJornada(Long idJornada);
    List<ActividadJornadaDto> findPendientesInterno(Long idInterno);
    List<ActividadJornadaDto> findHistorialInterno(Long idInterno);
    List<ActividadJornadaDto> findHistorialTitular(Long idTitular);

    // ── Reportes ──────────────────────────────────────────────────────────
    ResumenActividadesDto    resumenJornada(Long idJornada);
    EstadisticasActividadesDto estadisticasGlobales();
}
