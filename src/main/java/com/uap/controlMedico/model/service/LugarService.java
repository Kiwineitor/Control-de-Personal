package com.uap.controlMedico.model.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.uap.controlMedico.model.dto.catalagoDto.LugarRequest;
import com.uap.controlMedico.model.dto.catalagoDto.LugarResponse;
import com.uap.controlMedico.model.enums.TipoLugar;

public interface LugarService {

    LugarResponse crear(LugarRequest request);
    LugarResponse actualizar(Long id, LugarRequest request);
    LugarResponse buscarPorId(Long id);
    List<LugarResponse> listarActivos();
    List<LugarResponse> listarPorTipo(TipoLugar tipo);
    List<LugarResponse> findDisponiblesEnFecha(LocalDate fecha);
    void desactivar(Long id);

    Page<LugarResponse> getAll(String nombre, Boolean activo, Pageable pageable);

    List<LugarResponse> getAllActivos();

    void activar(Long id);

    long countConGeofencing();

}
