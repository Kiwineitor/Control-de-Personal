package com.uap.controlMedico.model.service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uap.controlMedico.exception.BusinessException;
import com.uap.controlMedico.exception.ResourceNotFoundException;
import com.uap.controlMedico.model.dao.LugarDao;
import com.uap.controlMedico.model.dto.catalagoDto.LugarRequest;
import com.uap.controlMedico.model.dto.catalagoDto.LugarResponse;
import com.uap.controlMedico.model.entity.Lugar;
import com.uap.controlMedico.model.enums.EstadoAsignacion;
import com.uap.controlMedico.model.enums.TipoLugar;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LugarServiceImpl implements LugarService{

    private final LugarDao   lugarDao;
    private final SisuMapper mapper;

    // ── Lecturas existentes ─────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public LugarResponse buscarPorId(Long id) {
        return mapper.toLugarResponse(buscarEntidadPorId(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LugarResponse> listarActivos() {
        return lugarDao.findByActivoTrue().stream()
                .map(mapper::toLugarResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<LugarResponse> getAllActivos() {
        return lugarDao.findByActivoTrueOrderByNombreAsc().stream()
                .map(mapper::toLugarResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<LugarResponse> listarPorTipo(TipoLugar tipo) {
        return lugarDao.findByTipoLugarAndActivoTrue(tipo).stream()
                .map(mapper::toLugarResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<LugarResponse> findDisponiblesEnFecha(LocalDate fecha) {
        return lugarDao.findDisponiblesEnFecha(fecha, EstadoAsignacion.CANCELADO).stream()
                .map(mapper::toLugarResponse)
                .collect(Collectors.toList());
    }

    // ── Lecturas nuevas (Paso 7) ────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<LugarResponse> getAll(String nombre, Boolean activo, Pageable pageable) {
        return lugarDao.findWithFilters(
            nombre != null ? nombre : "",
            activo,
            pageable
        ).map(mapper::toLugarResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public long countConGeofencing() {
        return lugarDao.countConGeofencing();
    }

    // ── Escrituras ──────────────────────────────────────────────────

    @Override
    @Transactional
    public LugarResponse crear(LugarRequest request) {
        if (lugarDao.existsByNombreIgnoreCase(request.getNombre()))
            throw new BusinessException(
                "Ya existe un lugar con el nombre '" + request.getNombre() + "'");

        validarGeofencing(request);

        Lugar lugar = Lugar.builder()
                .nombre(request.getNombre().trim())
                .descripcion(request.getDescripcion())
                .tipoLugar(request.getTipoLugar())
                .activo(request.isActivo())
                .latitudCentro(request.getLatitudCentro())
                .longitudCentro(request.getLongitudCentro())
                .radioMetros(request.getRadioMetros())
                .build();

        return mapper.toLugarResponse(lugarDao.save(lugar));
    }

    @Override
    @Transactional
    public LugarResponse actualizar(Long id, LugarRequest request) {
        Lugar lugar = buscarEntidadPorId(id);

        validarGeofencing(request);

        lugar.setNombre(request.getNombre().trim());
        lugar.setDescripcion(request.getDescripcion());
        lugar.setTipoLugar(request.getTipoLugar());
        lugar.setActivo(request.isActivo());
        lugar.setLatitudCentro(request.getLatitudCentro());
        lugar.setLongitudCentro(request.getLongitudCentro());
        lugar.setRadioMetros(request.getRadioMetros());

        return mapper.toLugarResponse(lugarDao.save(lugar));
    }

    @Override
    @Transactional
    public void activar(Long id) {
        Lugar l = buscarEntidadPorId(id);
        l.setActivo(true);
        lugarDao.save(l);
    }

    @Override
    @Transactional
    public void desactivar(Long id) {
        Lugar l = buscarEntidadPorId(id);
        l.setActivo(false);
        lugarDao.save(l);
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private Lugar buscarEntidadPorId(Long id) {
        return lugarDao.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lugar", id));
    }

    /** Geofencing es todo-o-nada: los 3 campos o ninguno */
    private void validarGeofencing(LugarRequest request) {
        boolean tieneAlguno = request.getLatitudCentro()  != null
                           || request.getLongitudCentro() != null
                           || request.getRadioMetros()    != null;
        boolean tieneTodos  = request.getLatitudCentro()  != null
                           && request.getLongitudCentro() != null
                           && request.getRadioMetros()    != null;
        if (tieneAlguno && !tieneTodos)
            throw new BusinessException(
                "Para configurar geofencing debes proporcionar los 3 campos: " +
                "latitud, longitud y radio.");
    }
}
