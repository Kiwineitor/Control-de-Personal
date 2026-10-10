package com.uap.controlMedico.model.service.horarioInterno;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uap.controlMedico.model.dao.DiaHorarioDao;
import com.uap.controlMedico.model.dao.HorarioInternoDao;
import com.uap.controlMedico.model.dao.InternoJornadaDao;
import com.uap.controlMedico.model.dto.horarioInternoDto.DiaHorarioDto;
import com.uap.controlMedico.model.dto.horarioInternoDto.HorarioInternoDto;
import com.uap.controlMedico.model.dto.horarioInternoDto.HorarioInternoRequest;
import com.uap.controlMedico.model.entity.DiaHorario;
import com.uap.controlMedico.model.entity.HorarioInterno;
import com.uap.controlMedico.model.entity.InternoJornada;
import com.uap.controlMedico.model.service.notificacion.NotificacionService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HorarioInternoServiceImpl implements HorarioInternoService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final HorarioInternoDao  horarioInternoDao;
    private final DiaHorarioDao      diaHorarioDao;
    private final InternoJornadaDao  internoJornadaDao;
    private final NotificacionService notificacionService;

    @Override
    @Transactional
    public HorarioInternoDto guardar(HorarioInternoRequest request) {
        InternoJornada ij = internoJornadaDao.findById(request.getIdInternoJornada())
            .orElseThrow(() -> new EntityNotFoundException("InternoJornada no encontrada"));

        HorarioInterno horario = horarioInternoDao
            .findByInternoJornada_IdInternoJornadaAndMesAndAnio(
                request.getIdInternoJornada(), request.getMes(), request.getAnio())
            .orElse(HorarioInterno.builder()
                .internoJornada(ij)
                .mes(request.getMes())
                .anio(request.getAnio())
                .build());

        horario.setObservaciones(request.getObservaciones());
        horario.setActivo(true);
        horario.getDias().clear();
        horario = horarioInternoDao.save(horario);

        for (HorarioInternoRequest.DiaHorarioRequest d : request.getDias()) {
            DiaHorario dia = DiaHorario.builder()
                .horarioInterno(horario)
                .fecha(LocalDate.parse(d.getFecha()))
                .horaInicio(d.getHoraInicio() != null ? LocalTime.parse(d.getHoraInicio(), TIME_FMT) : null)
                .horaFin(d.getHoraFin() != null ? LocalTime.parse(d.getHoraFin(), TIME_FMT) : null)
                .observaciones(d.getObservaciones())
                .build();
            horario.getDias().add(dia);
        }
        horario = horarioInternoDao.save(horario);

        try {
            notificacionService.notificarHorarioAsignado(horario);
        } catch (Exception e) {
            // La notificación no debe bloquear el guardado
        }

        return toDto(horario);
    }

    @Override
    public HorarioInternoDto findById(Long id) {
        return toDto(horarioInternoDao.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("HorarioInterno id=" + id)));
    }

    @Override
    public List<HorarioInternoDto> findByJornada(Long idJornada) {
        return horarioInternoDao.findByJornadaConDetalle(idJornada).stream().map(this::toDto).toList();
    }

    @Override
    public List<HorarioInternoDto> findByInternoJornada(Long idInternoJornada) {
        return horarioInternoDao.findByInternoJornada_IdInternoJornadaAndActivoTrue(idInternoJornada)
            .stream().map(this::toDto).toList();
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        HorarioInterno horario = horarioInternoDao.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("HorarioInterno id=" + id));
        horario.setActivo(false);
        horarioInternoDao.save(horario);
    }

    // ── Mapper ─────────────────────────────────────────────────────────────

    private HorarioInternoDto toDto(HorarioInterno h) {
        var ij      = h.getInternoJornada();
        var interno = ij.getInterno();
        var jornada = ij.getJornada();
        List<DiaHorarioDto> dias = h.getDias().stream()
            .map(d -> DiaHorarioDto.builder()
                .idDiaHorario(d.getIdDiaHorario())
                .fecha(d.getFecha().toString())
                .horaInicio(d.getHoraInicio() != null ? d.getHoraInicio().format(TIME_FMT) : null)
                .horaFin(d.getHoraFin() != null ? d.getHoraFin().format(TIME_FMT) : null)
                .observaciones(d.getObservaciones())
                .build())
            .sorted(java.util.Comparator.comparing(DiaHorarioDto::getFecha))
            .toList();
        return HorarioInternoDto.builder()
            .idHorarioInterno(h.getIdHorarioInterno())
            .idInternoJornada(ij.getIdInternoJornada())
            .idJornada(jornada.getIdJornada())
            .nombreJornada(jornada.getNombre())
            .idInterno(interno.getIdInterno())
            .nombreInterno(interno.getPersona() != null
                ? interno.getPersona().getNombreCompleto() : "Sin nombre")
            .carrera(interno.getCarrera())
            .mes(h.getMes())
            .anio(h.getAnio())
            .observaciones(h.getObservaciones())
            .activo(h.isActivo())
            .dias(dias)
            .horaInicioJornada(jornada.getHoraInicio() != null
                ? jornada.getHoraInicio().format(TIME_FMT) : null)
            .horaFinJornada(jornada.getHoraFin() != null
                ? jornada.getHoraFin().format(TIME_FMT) : null)
            .build();
    }
}
