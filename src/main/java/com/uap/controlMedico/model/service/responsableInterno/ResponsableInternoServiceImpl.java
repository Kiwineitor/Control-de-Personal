package com.uap.controlMedico.model.service.responsableInterno;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uap.controlMedico.model.dao.InternoDao;
import com.uap.controlMedico.model.dao.ResponsableInternoDao;
import com.uap.controlMedico.model.dao.TitularDao;
import com.uap.controlMedico.model.dto.internosDto.InternoAsignadoDto;
import com.uap.controlMedico.model.dto.responsableInterno.AsignarResponsableRequest;
import com.uap.controlMedico.model.dto.responsableInterno.ResponsableResponseDto;
import com.uap.controlMedico.model.dto.responsableInterno.ResponsableResumenDto;
import com.uap.controlMedico.model.entity.Interno;
import com.uap.controlMedico.model.entity.ResponsableInterno;
import com.uap.controlMedico.model.entity.Titular;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ResponsableInternoServiceImpl implements ResponsableInternoService {
    
    private final ResponsableInternoDao responsableDao;
    private final TitularDao            titularDao;
    private final InternoDao            internoDao;
 
    // ─────────────────────────────────────────────────────────────────
    //  Responsable actual
    // ─────────────────────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public ResponsableResumenDto findResponsableActual() {
        // El responsable actual = titular de la asignación activa más reciente
        List<ResponsableInterno> activos = responsableDao
                .findByActivoTrueOrderByFechaAsignacionDesc();
 
        if (activos.isEmpty()) return null;
 
        // Titular del primer registro activo (el más reciente)
        Titular titular = activos.get(0).getTitular();
 
        List<InternoAsignadoDto> internos = activos.stream()
                .filter(r -> r.getTitular().getIdTitular().equals(titular.getIdTitular()))
                .map(r -> InternoAsignadoDto.builder()
                        .idInterno(r.getInterno().getIdInterno())
                        .idResponsableInterno(r.getIdResponsableInterno())
                        .nombreCompleto(r.getInterno().getPersona().getNombreCompleto())
                        .carrera(r.getInterno().getCarrera())
                        .universidad(r.getInterno().getUniversidad())
                        .estaEnRotacion(r.getInterno().estaEnRotacion())
                        .fechaAsignacion(r.getFechaAsignacion())
                        .build())
                .collect(Collectors.toList());
 
        return ResponsableResumenDto.builder()
                .idTitular(titular.getIdTitular())
                .nombreCompleto(titular.getPersona().getNombreCompleto())
                .cargo(titular.getCargo())
                .especialidad(titular.getEspecialidad())
                .email(titular.getPersona().getEmail())
                .telefono(titular.getPersona().getTelefono())
                .internos(internos)
                .totalInternos(internos.size())
                .fechaAsignacion(activos.get(0).getFechaAsignacion())
                .build();
    }
 
    // ─────────────────────────────────────────────────────────────────
    //  Historial
    // ─────────────────────────────────────────────────────────────────
    @Override
    @Transactional(readOnly = true)
    public List<ResponsableResponseDto> findHistorial() {
        return responsableDao.findAllByOrderByFechaCreacionDesc()
                .stream().map(this::toDto).collect(Collectors.toList());
    }
 
    // ─────────────────────────────────────────────────────────────────
    //  Asignar responsable
    // ─────────────────────────────────────────────────────────────────
    @Override
    @Transactional
    public List<ResponsableResponseDto> asignar(AsignarResponsableRequest request) {
        Titular titular = titularDao.findById(request.getIdTitular())
                .orElseThrow(() -> new RuntimeException(
                        "Titular no encontrado: " + request.getIdTitular()));
 
        // Determinar lista de internos a asignar
        List<Interno> internos;
        if (request.isAsignarTodos()) {
            internos = internoDao.findActivosOrdenados();
        } else {
            if (request.getIdInternos() == null || request.getIdInternos().isEmpty()) {
                throw new RuntimeException(
                        "Debe indicar internos o activar asignarTodos=true");
            }
            internos = internoDao.findAllById(request.getIdInternos());
        }
 
        List<ResponsableInterno> nuevas = new ArrayList<>();
        LocalDate hoy = LocalDate.now();
 
        for (Interno interno : internos) {
            // Desactivar asignación anterior de este interno
            responsableDao.findByInternoAndActivoTrue(interno)
                    .ifPresent(anterior -> {
                        anterior.setActivo(false);
                        anterior.setFechaFinAsignacion(hoy);
                        responsableDao.save(anterior);
                    });
 
            // Crear nueva asignación
            ResponsableInterno nueva = ResponsableInterno.builder()
                    .titular(titular)
                    .interno(interno)
                    .fechaAsignacion(hoy)                           // ← FIX principal
                    .fechaFinAsignacion(request.getFechaFinAsignacion())
                    .observaciones(request.getObservaciones())
                    .activo(true)
                    .build();
 
            nuevas.add(responsableDao.save(nueva));
        }
 
        return nuevas.stream().map(this::toDto).collect(Collectors.toList());
    }
 
    // ─────────────────────────────────────────────────────────────────
    //  Desasignar
    // ─────────────────────────────────────────────────────────────────
    @Override
    @Transactional
    public void desasignar(Long idResponsableInterno) {
        responsableDao.findById(idResponsableInterno).ifPresent(r -> {
            r.setActivo(false);
            r.setFechaFinAsignacion(LocalDate.now());
            responsableDao.save(r);
        });
    }
 
    // ─────────────────────────────────────────────────────────────────
    //  Mapper
    // ─────────────────────────────────────────────────────────────────
    private ResponsableResponseDto toDto(ResponsableInterno r) {
        return ResponsableResponseDto.builder()
                .idResponsableInterno(r.getIdResponsableInterno())
                .activo(r.isActivo())
                .fechaAsignacion(r.getFechaAsignacion())
                .fechaFinAsignacion(r.getFechaFinAsignacion())
                .observaciones(r.getObservaciones())
                .creadoPor(r.getCreadoPor())
                .fechaCreacion(r.getFechaCreacion())
                // Titular
                .idTitular(r.getTitular().getIdTitular())
                .nombreCompletoTitular(r.getTitular().getPersona().getNombreCompleto())
                .cargoTitular(r.getTitular().getCargo())
                .especialidadTitular(r.getTitular().getEspecialidad())
                .emailTitular(r.getTitular().getPersona().getEmail())
                .telefonoTitular(r.getTitular().getPersona().getTelefono())
                // Interno
                .idInterno(r.getInterno().getIdInterno())
                .nombreCompletoInterno(r.getInterno().getPersona().getNombreCompleto())
                .carreraInterno(r.getInterno().getCarrera())
                .universidadInterno(r.getInterno().getUniversidad())
                .build();
    }

    @Override
    public ResponsableResponseDto findActivo() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'findActivo'");
    }
}
