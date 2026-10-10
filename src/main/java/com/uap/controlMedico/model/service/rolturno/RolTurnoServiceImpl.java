package com.uap.controlMedico.model.service.rolturno;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uap.controlMedico.model.dao.DiaTurnoDao;
import com.uap.controlMedico.model.dao.HorarioInternoDao;
import com.uap.controlMedico.model.dao.InternoJornadaDao;
import com.uap.controlMedico.model.dao.RolTurnoDao;
import com.uap.controlMedico.model.dao.SesionMonitoreoDao;
import com.uap.controlMedico.model.dao.TitularJornadaDao;
import com.uap.controlMedico.model.dto.monitoreo.PersonalEnTurnoDto;
import com.uap.controlMedico.model.dto.rolturnoDto.DiaTurnoDto;
import com.uap.controlMedico.model.dto.rolturnoDto.RolTurnoDto;
import com.uap.controlMedico.model.dto.rolturnoDto.RolTurnoRequest;
import com.uap.controlMedico.model.entity.DiaTurno;
import com.uap.controlMedico.model.entity.InternoJornada;
import com.uap.controlMedico.model.entity.RolTurno;
import com.uap.controlMedico.model.entity.TitularJornada;
import com.uap.controlMedico.model.enums.EstadoAsignacion;
import com.uap.controlMedico.model.enums.EstadoJornada;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RolTurnoServiceImpl implements RolTurnoService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final RolTurnoDao        rolTurnoDao;
    private final DiaTurnoDao        diaTurnoDao;
    private final TitularJornadaDao  titularJornadaDao;
    private final InternoJornadaDao  internoJornadaDao;
    private final HorarioInternoDao  horarioInternoDao;
    private final SesionMonitoreoDao sesionDao;

    @Override
    @Transactional
    public RolTurnoDto guardar(RolTurnoRequest request) {
        TitularJornada tj = titularJornadaDao.findById(request.getIdTitularJornada())
            .orElseThrow(() -> new EntityNotFoundException("TitularJornada no encontrada"));

        RolTurno rol = rolTurnoDao
            .findByTitularJornada_IdTitularJornadaAndMesAndAnio(
                request.getIdTitularJornada(), request.getMes(), request.getAnio())
            .orElse(RolTurno.builder()
                .titularJornada(tj)
                .mes(request.getMes())
                .anio(request.getAnio())
                .build());

        rol.setObservaciones(request.getObservaciones());
        rol.setActivo(true);
        // Limpiar días previos
        rol.getDias().clear();
        rol = rolTurnoDao.save(rol);

        // Agregar nuevos días
        for (RolTurnoRequest.DiaTurnoRequest d : request.getDias()) {
            DiaTurno dia = DiaTurno.builder()
                .rolTurno(rol)
                .fecha(LocalDate.parse(d.getFecha()))
                .horaInicio(d.getHoraInicio() != null ? LocalTime.parse(d.getHoraInicio(), TIME_FMT) : null)
                .horaFin(d.getHoraFin() != null ? LocalTime.parse(d.getHoraFin(), TIME_FMT) : null)
                .observaciones(d.getObservaciones())
                .build();
            rol.getDias().add(dia);
        }
        rol = rolTurnoDao.save(rol);
        return toDto(rol);
    }

    @Override
    public RolTurnoDto findById(Long id) {
        return toDto(rolTurnoDao.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("RolTurno id=" + id)));
    }

    @Override
    public List<RolTurnoDto> findByJornada(Long idJornada) {
        return rolTurnoDao.findByJornadaConDetalle(idJornada).stream().map(this::toDto).toList();
    }

    @Override
    public List<RolTurnoDto> findByTitularJornada(Long idTitularJornada) {
        return rolTurnoDao.findByTitularJornada_IdTitularJornadaAndActivoTrue(idTitularJornada)
            .stream().map(this::toDto).toList();
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        RolTurno rol = rolTurnoDao.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("RolTurno id=" + id));
        rol.setActivo(false);
        rolTurnoDao.save(rol);
    }

    @Override
    public List<PersonalEnTurnoDto> getPersonalEnTurnoAhora() {
        LocalDate hoy  = LocalDate.now();
        LocalTime ahora = LocalTime.now();
        List<PersonalEnTurnoDto> resultado = new ArrayList<>();

        // Titulares en turno ahora
        List<TitularJornada> titulares = titularJornadaDao.findAllInActiveJornadas();
        for (TitularJornada tj : titulares) {
            if (tj.estaEnTurnoAhora(hoy, ahora)) {
                var titular = tj.getTitular();
                var persona = titular.getPersona();
                var lugar   = tj.getLugar() != null ? tj.getLugar()
                            : (tj.getJornada().getLugar() != null ? tj.getJornada().getLugar() : null);
                // Franja que le aplica AHORA: en una jornada partida se reportaba
                // siempre la de la mañana, así que a media tarde la vista mostraba un
                // horario que para esa persona ya había terminado.
                LocalTime[] franja = tj.franjaVigente(ahora);
                resultado.add(PersonalEnTurnoDto.builder()
                    .idPersonal(titular.getIdTitular())
                    .tipoPersonal("TITULAR")
                    .nombrePersonal(persona.getNombreCompleto())
                    .cargo(titular.getCargo())
                    .idJornada(tj.getJornada().getIdJornada())
                    .nombreJornada(tj.getJornada().getNombre())
                    .idLugar(lugar != null ? lugar.getIdLugar() : null)
                    .nombreLugar(lugar != null ? lugar.getNombre() : null)
                    .horaInicio(franja != null ? franja[0].format(TIME_FMT) : null)
                    .horaFin(franja != null ? franja[1].format(TIME_FMT) : null)
                    .tieneSesionActiva(sesionDao.existsByTitular_IdTitularAndActivaTrue(titular.getIdTitular()))
                    .build());
            }
        }

        // Internos en turno ahora
        // Si el interno tiene HorarioInterno para el mes actual, se respetan sus días específicos
        // y las horas sobreescritas por día; si no, usa el horario base de la jornada.
        List<InternoJornada> internos = internoJornadaDao.findAllInActiveJornadas();
        for (InternoJornada ij : internos) {
            if (ij.getEstado() == EstadoAsignacion.CANCELADO
                    || ij.getEstado() == EstadoAsignacion.COMPLETADO) continue;
            var jornada = ij.getJornada();
            if (jornada == null || !jornada.monitoreoHabilitado()) continue;
            if (hoy.isBefore(jornada.getFechaInicio()) || hoy.isAfter(jornada.getFechaFin())) continue;

            // Buscar HorarioInterno del mes actual
            var horarioOpt = horarioInternoDao
                .findByInternoJornada_IdInternoJornadaAndMesAndAnio(
                    ij.getIdInternoJornada(), hoy.getMonthValue(), hoy.getYear());

            LocalTime horaI;
            LocalTime horaF;

            if (horarioOpt.isPresent() && horarioOpt.get().isActivo()) {
                // Verificar si hoy está en los días asignados
                var horario = horarioOpt.get();
                var diaOpt  = horario.getDias().stream()
                    .filter(d -> d.getFecha().equals(hoy))
                    .findFirst();
                if (diaOpt.isEmpty()) continue;
                // Usa horas del día específico si están definidas, si no usa la jornada
                var dia = diaOpt.get();
                horaI = dia.getHoraInicio() != null ? dia.getHoraInicio() : jornada.getHoraInicio();
                horaF = dia.getHoraFin()    != null ? dia.getHoraFin()    : jornada.getHoraFin();
            } else {
                // Sin HorarioInterno: usa horario base de la jornada
                horaI = jornada.getHoraInicio();
                horaF = jornada.getHoraFin();
            }

            boolean enHorario = (horaI == null || horaF == null) ? false
                : (horaF.isAfter(horaI)
                    ? (!ahora.isBefore(horaI) && !ahora.isAfter(horaF))
                    : (!ahora.isBefore(horaI) || !ahora.isAfter(horaF)));
            if (!enHorario) continue;

            var interno = ij.getInterno();
            var persona = interno.getPersona();
            var lugar   = ij.getLugar() != null ? ij.getLugar()
                        : (jornada.getLugar() != null ? jornada.getLugar() : null);
            resultado.add(PersonalEnTurnoDto.builder()
                .idPersonal(interno.getIdInterno())
                .tipoPersonal("INTERNO")
                .nombrePersonal(persona.getNombreCompleto())
                .cargo("Interno")
                .idJornada(jornada.getIdJornada())
                .nombreJornada(jornada.getNombre())
                .idLugar(lugar != null ? lugar.getIdLugar() : null)
                .nombreLugar(lugar != null ? lugar.getNombre() : null)
                .horaInicio(horaI != null ? horaI.format(TIME_FMT) : null)
                .horaFin(horaF != null ? horaF.format(TIME_FMT) : null)
                .tieneSesionActiva(sesionDao.existsByInterno_IdInternoAndActivaTrue(interno.getIdInterno()))
                .build());
        }
        return resultado;
    }

    // ── Mapper ─────────────────────────────────────────────────────────────

    private RolTurnoDto toDto(RolTurno r) {
        var tj      = r.getTitularJornada();
        var titular = tj.getTitular();
        var jornada = tj.getJornada();
        List<DiaTurnoDto> dias = r.getDias().stream()
            .map(d -> DiaTurnoDto.builder()
                .idDiaTurno(d.getIdDiaTurno())
                .fecha(d.getFecha().toString())
                .horaInicio(d.getHoraInicio() != null ? d.getHoraInicio().format(TIME_FMT) : null)
                .horaFin(d.getHoraFin() != null ? d.getHoraFin().format(TIME_FMT) : null)
                .observaciones(d.getObservaciones())
                .build())
            .sorted(java.util.Comparator.comparing(DiaTurnoDto::getFecha))
            .toList();
        return RolTurnoDto.builder()
            .idRolTurno(r.getIdRolTurno())
            .idTitularJornada(tj.getIdTitularJornada())
            .idJornada(jornada.getIdJornada())
            .nombreJornada(jornada.getNombre())
            .idTitular(titular.getIdTitular())
            .nombreTitular(titular.getPersona() != null
                ? titular.getPersona().getNombreCompleto() : "Sin nombre")
            .cargo(titular.getCargo())
            .mes(r.getMes())
            .anio(r.getAnio())
            .observaciones(r.getObservaciones())
            .activo(r.isActivo())
            .dias(dias)
            .horaInicioJornada(jornada.getHoraInicio() != null
                ? jornada.getHoraInicio().format(TIME_FMT) : null)
            .horaFinJornada(jornada.getHoraFin() != null
                ? jornada.getHoraFin().format(TIME_FMT) : null)
            .build();
    }
}
