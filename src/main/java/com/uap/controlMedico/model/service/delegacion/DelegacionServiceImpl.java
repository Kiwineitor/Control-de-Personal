package com.uap.controlMedico.model.service.delegacion;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uap.controlMedico.exception.BusinessException;
import com.uap.controlMedico.exception.ResourceNotFoundException;
import com.uap.controlMedico.model.dao.DelegacionDao;
import com.uap.controlMedico.model.dao.InternoDao;
import com.uap.controlMedico.model.dao.TitularJornadaDao;
import com.uap.controlMedico.model.dto.gestionDto.AprobacionRequest;
import com.uap.controlMedico.model.dto.gestionDto.DelegacionRequest;
import com.uap.controlMedico.model.dto.gestionDto.DelegacionResponse;
import com.uap.controlMedico.model.entity.Delegacion;
import com.uap.controlMedico.model.entity.Interno;
import com.uap.controlMedico.model.entity.TitularJornada;
import com.uap.controlMedico.model.enums.EstadoDelegacion;
import com.uap.controlMedico.model.service.SisuMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DelegacionServiceImpl implements DelegacionService {
    
        private final DelegacionDao     delegacionDao;
    private final TitularJornadaDao titularJornadaDao;
    private final InternoDao        internoDao;
    private final SisuMapper        mapper;
 
    @Override
    @Transactional
    public DelegacionResponse solicitar(DelegacionRequest request) {
        TitularJornada tj = titularJornadaDao.findById(request.getIdTitularJornada())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "TitularJornada", request.getIdTitularJornada()));
 
        Interno interno = internoDao.findById(request.getIdInternoResponsable())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Interno", request.getIdInternoResponsable()));
 
        // Verificar que no haya delegación activa para esta asignación
        delegacionDao.findActivaByTitularJornada(
                tj.getIdTitularJornada(), EstadoDelegacion.EN_CURSO)
            .ifPresent(d -> { throw new BusinessException(
                "Ya existe una delegación en curso para este turno"); });
 
        Delegacion delegacion = Delegacion.builder()
                .titularJornada(tj)
                .internoResponsable(interno)
                .motivo(request.getMotivo())
                .horaInicioPlanificada(request.getHoraInicioPlanificada())
                .horaFinPlanificada(request.getHoraFinPlanificada())
                .estado(EstadoDelegacion.PENDIENTE_APROBACION)
                .build();
 
        return mapper.toDelegacionResponse(delegacionDao.save(delegacion));
    }
 
    @Override
    @Transactional(readOnly = true)
    public DelegacionResponse buscarPorId(Long id) {
        return mapper.toDelegacionResponse(buscarEntidadPorId(id));
    }
 
    @Override
    @Transactional(readOnly = true)
    public List<DelegacionResponse> findPendientes() {
        return delegacionDao.findPendientesDeAprobacion(EstadoDelegacion.PENDIENTE_APROBACION)
                .stream().map(mapper::toDelegacionResponse).collect(Collectors.toList());
    }
 
    @Override
    @Transactional(readOnly = true)
    public List<DelegacionResponse> findByTitular(Long idTitular) {
        return delegacionDao.findByTitular(idTitular)
                .stream().map(mapper::toDelegacionResponse).collect(Collectors.toList());
    }
 
    @Override
    @Transactional(readOnly = true)
    public List<DelegacionResponse> findByInterno(Long idInterno) {
        return delegacionDao.findByInterno(idInterno)
                .stream().map(mapper::toDelegacionResponse).collect(Collectors.toList());
    }
 
    @Override
    @Transactional
    public DelegacionResponse aprobarORechazar(Long id, AprobacionRequest request) {
        Delegacion d = buscarEntidadPorId(id);
 
        if (!d.requiereAprobacion())
            throw new BusinessException("La delegación ya fue procesada");
 
        if (!request.getAprobado() &&
            (request.getObservacion() == null || request.getObservacion().isBlank()))
            throw new BusinessException("Debe indicar el motivo del rechazo");
 
        d.setEstado(request.getAprobado()
            ? EstadoDelegacion.APROBADA : EstadoDelegacion.RECHAZADA);
        d.setFechaAprobacion(LocalDateTime.now());
        d.setObservacionAprobacion(request.getObservacion());
 
        return mapper.toDelegacionResponse(delegacionDao.save(d));
    }
 
    @Override
    @Transactional
    public DelegacionResponse activar(Long id) {
        Delegacion d = buscarEntidadPorId(id);
        if (!EstadoDelegacion.APROBADA.equals(d.getEstado()))
            throw new BusinessException("La delegación debe estar aprobada para activarse");
 
        d.setEstado(EstadoDelegacion.EN_CURSO);
        d.setHoraInicioReal(LocalTime.now());
        return mapper.toDelegacionResponse(delegacionDao.save(d));
    }
 
    @Override
    @Transactional
    public DelegacionResponse completar(Long id) {
        Delegacion d = buscarEntidadPorId(id);
        if (!d.estaActiva())
            throw new BusinessException("Solo se pueden completar delegaciones EN_CURSO");
 
        d.setEstado(EstadoDelegacion.COMPLETADA);
        d.setHoraFinReal(LocalTime.now());
        return mapper.toDelegacionResponse(delegacionDao.save(d));
    }
 
    private Delegacion buscarEntidadPorId(Long id) {
        return delegacionDao.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delegacion", id));
    }

}
