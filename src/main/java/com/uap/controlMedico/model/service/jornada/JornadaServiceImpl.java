package com.uap.controlMedico.model.service.jornada;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uap.controlMedico.model.dao.InternoDao;
import com.uap.controlMedico.model.dao.InternoJornadaDao;
import com.uap.controlMedico.model.dao.JornadaDao;
import com.uap.controlMedico.model.dao.LugarDao;
import com.uap.controlMedico.model.dao.ResponsableInternoDao;
import com.uap.controlMedico.model.dao.TitularDao;
import com.uap.controlMedico.model.dao.TitularJornadaDao;
import com.uap.controlMedico.model.dto.actividadDto.CarryOverResultDto;
import com.uap.controlMedico.model.dto.internosDto.InternoJornadaDto;
import com.uap.controlMedico.model.dto.jornadaDto.AsignarInternoRequest;
import com.uap.controlMedico.model.dto.jornadaDto.AsignarTitularRequest;
import com.uap.controlMedico.model.dto.jornadaDto.CambiarEstadoAsignacionRequest;
import com.uap.controlMedico.model.dto.jornadaDto.HorarioIndividual;
import com.uap.controlMedico.model.dto.jornadaDto.InternoSelectDto;
import com.uap.controlMedico.model.dto.jornadaDto.JornadaDto;
import com.uap.controlMedico.model.dto.jornadaDto.JornadaRequest;
import com.uap.controlMedico.model.dto.jornadaDto.JornadaTitularDto;
import com.uap.controlMedico.model.dto.jornadaDto.LugarSelectDto;
import com.uap.controlMedico.model.dto.jornadaDto.TitularSelectDto;
import com.uap.controlMedico.model.dto.titularDto.TitularJornadaDto;
import com.uap.controlMedico.model.entity.Interno;
import com.uap.controlMedico.model.entity.InternoJornada;
import com.uap.controlMedico.model.entity.Jornada;
import com.uap.controlMedico.model.entity.Lugar;
import com.uap.controlMedico.model.entity.Persona;
import com.uap.controlMedico.model.entity.ResponsableInterno;
import com.uap.controlMedico.model.entity.Titular;
import com.uap.controlMedico.model.entity.TitularJornada;
import com.uap.controlMedico.model.enums.EstadoAsignacion;
import com.uap.controlMedico.model.enums.EstadoJornada;
import com.uap.controlMedico.model.enums.ModalidadHorario;
import com.uap.controlMedico.model.enums.TipoJornada;
import com.uap.controlMedico.model.service.actividad.ActividadJornadaV2Service;
import com.uap.controlMedico.model.service.notificacion.NotificacionService;
import com.uap.controlMedico.model.service.turno.HistorialCambioService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class JornadaServiceImpl implements JornadaService {
    
    private final JornadaDao            jornadaDao;
    private final TitularJornadaDao     titularJornadaDao;
    private final InternoJornadaDao     internoJornadaDao;
    private final TitularDao            titularDao;
    private final InternoDao            internoDao;
    private final ResponsableInternoDao responsableInternoDao;
    private final LugarDao              lugarDao;
    private final ActividadJornadaV2Service actividadJornadaV2Service;
    private final NotificacionService   notificacionService;
    private final HistorialCambioService historialService;

    @Override
    @Transactional(readOnly = true)
    public Page<JornadaDto> listarConFiltros(String q, EstadoJornada estado, TipoJornada tipo,
                                              LocalDate fechaIni, LocalDate fechaFin, Pageable pageable) {
        return jornadaDao
            .findConFiltros(q == null ? "" : q, estado, tipo, fechaIni, fechaFin, pageable)
            .map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public JornadaDto findById(Long id) {
        return toDto(findJornadaOrThrow(id));
    }

    @Override
    @Transactional
    public JornadaDto crear(JornadaRequest request) {
        validarFechasHoras(request.getFechaInicio(), request.getFechaFin(),
                           request.getHoraInicio(), request.getHoraFin());
        Lugar lugar = resolverLugar(request.getIdLugar());

        ModalidadHorario modalidad = request.getModalidad() != null
            ? request.getModalidad() : ModalidadHorario.CONTINUA;

        Jornada jornada = Jornada.builder()
            .nombre(request.getNombre())
            .fechaInicio(request.getFechaInicio())
            .fechaFin(request.getFechaFin())
            .horaInicio(request.getHoraInicio())
            .horaFin(request.getHoraFin())
            .tipoJornada(request.getTipoJornada() != null ? request.getTipoJornada() : TipoJornada.REGULAR)
            .modalidad(modalidad)
            .horaInicioTarde(modalidad == ModalidadHorario.PARTIDA ? request.getHoraInicioTarde() : null)
            .horaFinTarde(modalidad == ModalidadHorario.PARTIDA ? request.getHoraFinTarde() : null)
            .diasDefaultJson(request.getDiasDefault())
            .estado(EstadoJornada.ACTIVA)
            .descripcion(request.getDescripcion())
            .lugar(lugar)
            .build();

        return toDto(jornadaDao.save(jornada));
    }

    @Override
    @Transactional
    public JornadaDto actualizar(Long id, JornadaRequest request) {
        Jornada jornada = findJornadaOrThrow(id);
        if (jornada.getEstado() != EstadoJornada.ACTIVA)
            throw new IllegalStateException("Solo se pueden editar jornadas ACTIVAS");

        validarFechasHoras(request.getFechaInicio(), request.getFechaFin(),
                           request.getHoraInicio(), request.getHoraFin());

        jornada.setNombre(request.getNombre());
        jornada.setFechaInicio(request.getFechaInicio());
        jornada.setFechaFin(request.getFechaFin());
        jornada.setHoraInicio(request.getHoraInicio());
        jornada.setHoraFin(request.getHoraFin());
        jornada.setDescripcion(request.getDescripcion());

        // El lugar se COPIA a cada asignación al asignar personal, y el geofence le
        // da precedencia sobre el de la jornada. Sin propagar el cambio, mover la
        // jornada de lugar dejaba a la gente vigilada contra el lugar viejo mientras
        // su app mostraba el nuevo: seguían saliendo alertas de "fuera de zona" en
        // una sede donde ya no trabajan.
        Lugar lugarAnterior = jornada.getLugar();
        Lugar lugarNuevo    = resolverLugar(request.getIdLugar());
        jornada.setLugar(lugarNuevo);
        propagarCambioDeLugar(jornada, lugarAnterior, lugarNuevo);
        if (request.getTipoJornada() != null)
            jornada.setTipoJornada(request.getTipoJornada());
        ModalidadHorario modalidad = request.getModalidad() != null
            ? request.getModalidad() : ModalidadHorario.CONTINUA;
        jornada.setModalidad(modalidad);
        jornada.setHoraInicioTarde(modalidad == ModalidadHorario.PARTIDA ? request.getHoraInicioTarde() : null);
        jornada.setHoraFinTarde(modalidad == ModalidadHorario.PARTIDA ? request.getHoraFinTarde() : null);
        jornada.setDiasDefaultJson(request.getDiasDefault());

        Jornada guardada = jornadaDao.save(jornada);

        // Avisar al personal asignado. Sin esto, su app seguía mostrando el horario
        // anterior hasta que algo la forzara a recargar (volver a primer plano, o el
        // aviso del planificador al abrir la sesión de monitoreo): podían presentarse
        // a una hora que ya no correspondía. No debe tumbar la edición si falla.
        try {
            notificacionService.notificarCambioJornada(guardada);
        } catch (Exception e) {
            log.warn("No se pudo notificar el cambio de la jornada {}: {}", id, e.getMessage());
        }

        return toDto(guardada);
    }

    @Override
    @Transactional
    public JornadaDto cerrar(Long id) {
        Jornada jornada = findJornadaOrThrow(id);
        if (jornada.getEstado() != EstadoJornada.ACTIVA)
            throw new IllegalStateException("Solo se pueden cerrar jornadas ACTIVAS");

        jornada.setEstado(EstadoJornada.CERRADA);
        jornadaDao.save(jornada);

        // Carry-over automático: traspasa actividades pendientes a próximos turnos
        CarryOverResultDto carryOver = actividadJornadaV2Service.ejecutarCarryOver(id);
        log.info("Jornada {} cerrada — carry-over: {} traspasados / {} sin destino",
            id, carryOver.getTraspasados(), carryOver.getSinJornadaDestino());

        return toDto(jornada);
    }

    @Override
    @Transactional
    public JornadaDto cancelar(Long id) {
        Jornada jornada = findJornadaOrThrow(id);
        if (jornada.getEstado() == EstadoJornada.CERRADA)
            throw new IllegalStateException("No se puede cancelar una jornada ya CERRADA");
        jornada.setEstado(EstadoJornada.CANCELADA);
        return toDto(jornadaDao.save(jornada));
    }

    /**
     * Suspende o reanuda el control GPS de un turno sin tocar su programación.
     *
     * Pausar cierra las sesiones abiertas en la siguiente pasada del scheduler y
     * corta atrasos, faltas y avisos de ubicación; reanudar hace que el sistema
     * vuelva a evaluar a la gente que esté dentro de su horario. El turno, su
     * horario y sus asignaciones quedan intactos en ambos casos.
     */
    @Override
    @Transactional
    public JornadaDto cambiarMonitoreo(Long id, boolean pausado) {
        Jornada jornada = findJornadaOrThrow(id);
        if (jornada.isMonitoreoPausado() == pausado) return toDto(jornada);

        jornada.setMonitoreoPausado(pausado);
        JornadaDto dto = toDto(jornadaDao.save(jornada));
        historialService.registrarAccion("JORNADA", id, "MODIFICADO", "monitoreoPausado",
            String.valueOf(!pausado), String.valueOf(pausado));
        log.info("Monitoreo de la jornada {} {}", id, pausado ? "PAUSADO" : "REANUDADO");
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public List<JornadaDto> listarActivas() {
        return jornadaDao.findByEstadoOrderByFechaInicioDesc(EstadoJornada.ACTIVA)
            .stream().map(this::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TitularJornadaDto> listarTitulares(Long idJornada) {
        findJornadaOrThrow(idJornada);
        return titularJornadaDao.findConDetalleByJornada(idJornada)
            .stream().map(this::toTitularDto).toList();
    }

    @Override
    @Transactional
    public List<TitularJornadaDto> asignarTitulares(Long idJornada, AsignarTitularRequest request) {
        Jornada jornada = findJornadaOrThrow(idJornada);
        Lugar lugar = request.getIdLugar() != null
            ? resolverLugar(request.getIdLugar())
            : jornada.getLugar();

        // Si la jornada tiene días de turno definidos, se copian automáticamente a cada titular
        String diasDefault = jornada.getDiasDefaultJson();

        return request.getIdTitulares().stream()
            .filter(idT -> !titularJornadaDao
                .existsByTitular_IdTitularAndJornada_IdJornada(idT, idJornada))
            .map(idT -> {
                Titular titular = titularDao.findById(idT)
                    .orElseThrow(() -> new EntityNotFoundException("Titular no encontrado: " + idT));
                TitularJornada tj = TitularJornada.builder()
                    .titular(titular)
                    .jornada(jornada)
                    .lugar(lugar)
                    .estado(EstadoAsignacion.PROGRAMADO)
                    .observaciones(request.getObservaciones())
                    .build();
                if (diasDefault != null && !diasDefault.isBlank()) {
                    tj.setDiasTrabajoJson(diasDefault);
                }
                // Aplicar override de horario individual si existe
                if (request.getHorariosIndividuales() != null
                    && request.getHorariosIndividuales().containsKey(idT)) {
                    aplicarHorario(tj, request.getHorariosIndividuales().get(idT));
                }
                TitularJornada saved = titularJornadaDao.save(tj);
                notificacionService.notificarAsignacionJornada(saved);
                return saved;
            })
            .map(this::toTitularDto)
            .toList();
    }

    @Override
    @Transactional
    public TitularJornadaDto cambiarEstadoTitular(Long idTitularJornada,
                                                   CambiarEstadoAsignacionRequest req) {
        TitularJornada tj = titularJornadaDao.findById(idTitularJornada)
            .orElseThrow(() -> new EntityNotFoundException(
                "Asignación de titular no encontrada: " + idTitularJornada));
        tj.setEstado(req.getEstado());
        if (req.getObservaciones() != null) tj.setObservaciones(req.getObservaciones());
        TitularJornadaDto dto = toTitularDto(titularJornadaDao.save(tj));
        historialService.registrarAccion("TITULAR_JORNADA", idTitularJornada,
            "MODIFICADO", "estado", null, req.getEstado().name());
        return dto;
    }

    @Override
    @Transactional
    public TitularJornadaDto actualizarHorarioTitular(Long idTitularJornada,
                                                      HorarioIndividual horario) {
        TitularJornada tj = titularJornadaDao.findById(idTitularJornada)
            .orElseThrow(() -> new EntityNotFoundException(
                "Asignación de titular no encontrada: " + idTitularJornada));

        String antes = descripcionHorario(tj);
        aplicarHorario(tj, horario);

        // Desmarcar ambas franjas dejaría al titular asignado pero sin ninguna hora
        // en la que se lo considere en turno: nunca se le abriría sesión de GPS y
        // constaría como no presentado todos los días.
        if (tj.esPartida() && !tj.isTrabajaManana() && !tj.isTrabajaTarde()) {
            throw new IllegalArgumentException(
                "El titular debe cubrir al menos una franja: mañana o tarde");
        }

        TitularJornadaDto dto = toTitularDto(titularJornadaDao.save(tj));
        historialService.registrarAccion("TITULAR_JORNADA", idTitularJornada,
            "MODIFICADO", "horario", antes, descripcionHorario(tj));

        // Su app muestra el horario en pantalla; sin aviso seguiría mostrando el
        // anterior hasta que algo la forzara a recargar.
        try {
            notificacionService.notificarAsignacionJornada(tj);
        } catch (Exception e) {
            log.warn("No se pudo notificar el cambio de horario de la asignación {}: {}",
                idTitularJornada, e.getMessage());
        }
        return dto;
    }

    /** Texto compacto del horario, para el historial de cambios. */
    private static String descripcionHorario(TitularJornada tj) {
        StringBuilder sb = new StringBuilder();
        if (!tj.esPartida()) {
            return sb.append(tj.inicioManana()).append('-').append(tj.finManana()).toString();
        }
        sb.append(tj.isTrabajaManana()
            ? tj.inicioManana() + "-" + tj.finManana() : "sin mañana");
        sb.append(" / ");
        sb.append(tj.isTrabajaTarde()
            ? tj.inicioTarde() + "-" + tj.finTarde() : "sin tarde");
        return sb.toString();
    }

    @Override
    @Transactional
    public void removerTitular(Long idTitularJornada) {
        TitularJornada tj = titularJornadaDao.findById(idTitularJornada)
            .orElseThrow(() -> new EntityNotFoundException(
                "Asignación no encontrada: " + idTitularJornada));
        historialService.registrarAccion("TITULAR_JORNADA", idTitularJornada,
            "ELIMINADO", "titular",
            tj.getTitular().getPersona().getApellido() + " " + tj.getTitular().getPersona().getNombre(),
            null);
        titularJornadaDao.delete(tj);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InternoJornadaDto> listarInternos(Long idJornada) {
        findJornadaOrThrow(idJornada);
        return internoJornadaDao.findConDetalleByJornada(idJornada)
            .stream().map(ij -> toInternoDto(ij)).toList();
    }

    @Override
    @Transactional
    public List<InternoJornadaDto> asignarInternos(Long idJornada, AsignarInternoRequest request) {
        Jornada jornada = findJornadaOrThrow(idJornada);
        Lugar lugar = request.getIdLugar() != null
            ? resolverLugar(request.getIdLugar())
            : jornada.getLugar();

        return request.getIdInternos().stream()
            .filter(idI -> !internoJornadaDao
                .existsByInterno_IdInternoAndJornada_IdJornada(idI, idJornada))
            .map(idI -> {
                Interno interno = internoDao.findById(idI)
                    .orElseThrow(() -> new EntityNotFoundException("Interno no encontrado: " + idI));

                ResponsableInterno responsable = responsableInternoDao
                    .findByInternoAndActivoTrue(interno)
                    .orElse(null);

                if (responsable == null)
                    log.warn("Interno {} asignado sin responsable activo en jornada {}",
                             idI, idJornada);

                InternoJornada saved = internoJornadaDao.save(
                    InternoJornada.builder()
                        .interno(interno)
                        .jornada(jornada)
                        .lugar(lugar)
                        .responsableInterno(responsable)
                        .estado(EstadoAsignacion.PROGRAMADO)
                        .observaciones(request.getObservaciones())
                        .build()
                );
                // Aplicar override de horario individual si existe
                if (request.getHorariosIndividuales() != null
                    && request.getHorariosIndividuales().containsKey(idI)) {
                    var hi = request.getHorariosIndividuales().get(idI);
                    if (hi.getHoraInicio() != null) saved.setHoraInicioOverride(hi.getHoraInicio());
                    if (hi.getHoraFin() != null) saved.setHoraFinOverride(hi.getHoraFin());
                    saved = internoJornadaDao.save(saved);
                }
                notificacionService.notificarAsignacionJornada(saved);
                return saved;
            })
            .map(this::toInternoDto)
            .toList();
    }

    @Override
    @Transactional
    public InternoJornadaDto cambiarEstadoInterno(Long idInternoJornada,
                                                   CambiarEstadoAsignacionRequest req) {
        InternoJornada ij = internoJornadaDao.findById(idInternoJornada)
            .orElseThrow(() -> new EntityNotFoundException(
                "Asignación de interno no encontrada: " + idInternoJornada));
        ij.setEstado(req.getEstado());
        if (req.getObservaciones() != null) ij.setObservaciones(req.getObservaciones());
        InternoJornadaDto dto = toInternoDto(internoJornadaDao.save(ij));
        historialService.registrarAccion("INTERNO_JORNADA", idInternoJornada,
            "MODIFICADO", "estado", null, req.getEstado().name());
        return dto;
    }

    @Override
    @Transactional
    public void removerInterno(Long idInternoJornada) {
        InternoJornada ij = internoJornadaDao.findById(idInternoJornada)
            .orElseThrow(() -> new EntityNotFoundException(
                "Asignación no encontrada: " + idInternoJornada));
        historialService.registrarAccion("INTERNO_JORNADA", idInternoJornada,
            "ELIMINADO", "interno",
            ij.getInterno().getPersona().getApellido() + " " + ij.getInterno().getPersona().getNombre(),
            null);
        internoJornadaDao.delete(ij);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TitularSelectDto> getTitularesDisponibles() {
        return titularDao.findActivosConPersona().stream()
            .map(t -> TitularSelectDto.builder()
                .idTitular(t.getIdTitular())
                .nombreCompleto(t.getPersona().getApellido() + " " + t.getPersona().getNombre())
                .cargo(t.getCargo())
                .build())
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InternoSelectDto> getInternosDisponibles() {
        List<Interno> internos = internoDao.findActivosConPersona();
        List<InternoSelectDto> result = new ArrayList<>(internos.size());
        for (Interno i : internos) {
            result.add(InternoSelectDto.builder()
                .idInterno(i.getIdInterno())
                .nombreCompleto(i.getPersona().getApellido() + " " + i.getPersona().getNombre())
                .carrera(i.getCarrera())
                .universidad(i.getUniversidad())
                .estaEnRotacion(calcularEstaEnRotacion(i))
                .build());
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LugarSelectDto> getLugaresDisponibles() {
        return lugarDao.findByActivoTrueOrderByNombreAsc().stream()
            .map(l -> LugarSelectDto.builder()
                .idLugar(l.getIdLugar())
                .nombre(l.getNombre())
                .descripcion(l.getDescripcion())
                .build())
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<JornadaTitularDto> getJornadasPorTitular(Long idTitular) {
        Titular titular = titularDao.findById(idTitular)
            .orElseThrow(() -> new EntityNotFoundException("Titular no encontrado: " + idTitular));
        return titularJornadaDao.findByTitular(titular).stream()
            .map(tj -> JornadaTitularDto.builder()
                .idTitularJornada(tj.getIdTitularJornada())
                .idJornada(tj.getJornada().getIdJornada())
                .nombre(tj.getJornada().getNombre())
                .fechaInicio(tj.getJornada().getFechaInicio())
                .fechaFin(tj.getJornada().getFechaFin())
                .estadoJornada(tj.getJornada().getEstado())
                .estadoAsignacion(tj.getEstado())
                .lugarNombre(tj.getLugar() != null ? tj.getLugar().getNombre() : null)
                .build())
            .toList();
    }

    //  HELPERS PRIVADOS

    private Jornada findJornadaOrThrow(Long id) {
        return jornadaDao.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Jornada no encontrada: " + id));
    }

    private Lugar resolverLugar(Long idLugar) {
        if (idLugar == null) return null;
        return lugarDao.findById(idLugar)
            .orElseThrow(() -> new EntityNotFoundException("Lugar no encontrado: " + idLugar));
    }

    /**
     * Propaga un cambio de lugar de la jornada a las asignaciones que lo heredaban.
     *
     * Al asignar personal, el lugar de la jornada se copia a cada TitularJornada /
     * InternoJornada, y el geofence le da precedencia a esa copia. Por eso mover la
     * jornada de sede no bastaba: las asignaciones seguían apuntando a la anterior.
     *
     * Solo se tocan las que coincidían con el lugar ANTERIOR de la jornada — esas
     * son las que venían heredando. Una asignación con un lugar distinto es un
     * override deliberado (alguien que trabaja en otra sede dentro de la misma
     * jornada) y se respeta.
     */
    private void propagarCambioDeLugar(Jornada jornada, Lugar anterior, Lugar nuevo) {
        if (java.util.Objects.equals(idLugarDe(anterior), idLugarDe(nuevo))) return;

        Long idJornada = jornada.getIdJornada();
        Long idAnterior = idLugarDe(anterior);
        int actualizadas = 0;

        for (TitularJornada tj : titularJornadaDao.findByJornada_IdJornada(idJornada)) {
            if (java.util.Objects.equals(idLugarDe(tj.getLugar()), idAnterior)) {
                tj.setLugar(nuevo);
                titularJornadaDao.save(tj);
                actualizadas++;
            }
        }
        for (InternoJornada ij : internoJornadaDao.findByJornada_IdJornada(idJornada)) {
            if (java.util.Objects.equals(idLugarDe(ij.getLugar()), idAnterior)) {
                ij.setLugar(nuevo);
                internoJornadaDao.save(ij);
                actualizadas++;
            }
        }

        if (actualizadas > 0) {
            log.info("Jornada {}: lugar {} → {}; {} asignaciones re-apuntadas",
                idJornada,
                anterior != null ? anterior.getNombre() : "(sin lugar)",
                nuevo    != null ? nuevo.getNombre()    : "(sin lugar)",
                actualizadas);
        }
    }

    private static Long idLugarDe(Lugar l) {
        return l == null ? null : l.getIdLugar();
    }

    private void validarFechasHoras(java.time.LocalDate fechaInicio, java.time.LocalDate fechaFin,
                                     java.time.LocalTime horaInicio, java.time.LocalTime horaFin) {
        if (fechaFin.isBefore(fechaInicio))
            throw new IllegalArgumentException("La fecha de fin debe ser igual o posterior al inicio");
        if (horaInicio == null || horaFin == null)
            throw new IllegalArgumentException("Las horas de inicio y fin son obligatorias");
    }

    // Mappers 

    private JornadaDto toDto(Jornada j) {
        return JornadaDto.builder()
            .idJornada(j.getIdJornada())
            .nombre(j.getNombre())
            .fechaInicio(j.getFechaInicio())
            .fechaFin(j.getFechaFin())
            .horaInicio(j.getHoraInicio())
            .horaFin(j.getHoraFin())
            .tipoJornada(j.getTipoJornada())
            .estado(j.getEstado())
            .descripcion(j.getDescripcion())
            .duracionHoras(j.getDuracionHoras())
            .estaEnCurso(j.estaEnCurso())
            .monitoreoPausado(j.isMonitoreoPausado())
            .idLugar(j.getLugar() != null ? j.getLugar().getIdLugar() : null)
            .nombreLugar(j.getLugar() != null ? j.getLugar().getNombre() : null)
            .totalTitulares((int) titularJornadaDao.countByJornada_IdJornada(j.getIdJornada()))
            .totalInternos((int) internoJornadaDao.countByJornada_IdJornada(j.getIdJornada()))
            .modalidad(j.getModalidad())
            .horaInicioTarde(j.getHoraInicioTarde())
            .horaFinTarde(j.getHoraFinTarde())
            .diasDefault(j.getDiasDefaultJson())
            .build();
    }

    private TitularJornadaDto toTitularDto(TitularJornada tj) {
        Persona p = tj.getTitular().getPersona();
        return TitularJornadaDto.builder()
            .idTitularJornada(tj.getIdTitularJornada())
            .idTitular(tj.getTitular().getIdTitular())
            .nombreCompleto(p.getApellido() + " " + p.getNombre())
            .cargo(tj.getTitular().getCargo())
            .especialidad(tj.getTitular().getEspecialidad())
            .idLugar(tj.getLugar() != null ? tj.getLugar().getIdLugar() : null)
            .nombreLugar(tj.getLugar() != null ? tj.getLugar().getNombre() : null)
            .estado(tj.getEstado())
            .observaciones(tj.getObservaciones())
            .horaInicioOverride(tj.getHoraInicioOverride())
            .horaFinOverride(tj.getHoraFinOverride())
            // Las efectivas salen de la entidad para no repetir acá la regla de
            // precedencia override → jornada, que ya vive en TitularJornada.
            .horaInicioEfectiva(tj.inicioManana())
            .horaFinEfectiva(tj.finManana())
            .horaInicioTardeOverride(tj.getHoraInicioTardeOverride())
            .horaFinTardeOverride(tj.getHoraFinTardeOverride())
            .horaInicioTardeEfectiva(tj.inicioTarde())
            .horaFinTardeEfectiva(tj.finTarde())
            .trabajaManana(tj.isTrabajaManana())
            .trabajaTarde(tj.isTrabajaTarde())
            .jornadaPartida(tj.esPartida())
            .diasTrabajoJson(tj.getDiasTrabajoJson())
            .build();
    }

    /**
     * Vuelca un {@link HorarioIndividual} sobre la asignación.
     *
     * Las horas se aplican solo si vienen con valor: null significa "hereda de la
     * jornada" y no debe pisar un override ya existente. Los flags de franja sí se
     * escriben siempre, porque desmarcar una franja es justamente pasar de true a
     * false y hay que poder expresarlo.
     */
    private void aplicarHorario(TitularJornada tj, HorarioIndividual hi) {
        if (hi == null) return;
        if (hi.getHoraInicio()      != null) tj.setHoraInicioOverride(hi.getHoraInicio());
        if (hi.getHoraFin()         != null) tj.setHoraFinOverride(hi.getHoraFin());
        if (hi.getHoraInicioTarde() != null) tj.setHoraInicioTardeOverride(hi.getHoraInicioTarde());
        if (hi.getHoraFinTarde()    != null) tj.setHoraFinTardeOverride(hi.getHoraFinTarde());
        tj.setTrabajaManana(hi.cubreManana());
        tj.setTrabajaTarde(hi.cubreTarde());
    }

    private InternoJornadaDto toInternoDto(InternoJornada ij) {
        Persona p = ij.getInterno().getPersona();

        Long   idResp     = null;
        String nombreResp = null;
        if (ij.getResponsableInterno() != null) {
            idResp = ij.getResponsableInterno().getIdResponsableInterno();
            Persona pR = ij.getResponsableInterno().getTitular().getPersona();
            nombreResp = pR.getApellido() + " " + pR.getNombre();
        }

        return InternoJornadaDto.builder()
            .idInternoJornada(ij.getIdInternoJornada())
            .idInterno(ij.getInterno().getIdInterno())
            .nombreCompleto(p.getApellido() + " " + p.getNombre())
            .carrera(ij.getInterno().getCarrera())
            .universidad(ij.getInterno().getUniversidad())
            .estaEnRotacion(calcularEstaEnRotacion(ij.getInterno()))
            .idLugar(ij.getLugar() != null ? ij.getLugar().getIdLugar() : null)
            .nombreLugar(ij.getLugar() != null ? ij.getLugar().getNombre() : null)
            .idResponsableInterno(idResp)
            .nombreResponsable(nombreResp)
            .estado(ij.getEstado())
            .adjuntoUrl(ij.getAdjuntoUrl())
            .observaciones(ij.getObservaciones())
            .horaInicioOverride(ij.getHoraInicioOverride())
            .horaFinOverride(ij.getHoraFinOverride())
            .horaInicioEfectiva(ij.getHoraInicioOverride() != null
                ? ij.getHoraInicioOverride() : ij.getJornada().getHoraInicio())
            .horaFinEfectiva(ij.getHoraFinOverride() != null
                ? ij.getHoraFinOverride() : ij.getJornada().getHoraFin())
            .build();
    }

    private boolean calcularEstaEnRotacion(Interno i) {
        if (i.getFechaInicioRotacion() == null || i.getFechaFinRotacion() == null) return false;
        LocalDate hoy = LocalDate.now();
        return !hoy.isBefore(i.getFechaInicioRotacion()) && !hoy.isAfter(i.getFechaFinRotacion());
    }
}
