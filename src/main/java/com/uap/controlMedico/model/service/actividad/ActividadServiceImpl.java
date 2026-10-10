package com.uap.controlMedico.model.service.actividad;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uap.controlMedico.exception.BusinessException;
import com.uap.controlMedico.exception.ResourceNotFoundException;
import com.uap.controlMedico.model.dao.ActividadDao;
import com.uap.controlMedico.model.dao.ActividadJornadaDao;
import com.uap.controlMedico.model.dao.InternoJornadaDao;
import com.uap.controlMedico.model.dao.JornadaDao;
import com.uap.controlMedico.model.dao.TitularDao;
import com.uap.controlMedico.model.dao.TitularJornadaDao;
import com.uap.controlMedico.model.dto.actividadDto.ActividadDto;
import com.uap.controlMedico.model.dto.actividadDto.ActividadJornadaDto;
import com.uap.controlMedico.model.dto.actividadDto.CambiarEstadoActividadRequest;
import com.uap.controlMedico.model.dto.actividadDto.EstadisticasActividadesDto;
import com.uap.controlMedico.model.dto.actividadDto.ResumenActividadesDto;
import com.uap.controlMedico.model.dto.actividadDto.ValidarActividadRequest;
import com.uap.controlMedico.model.dto.catalagoDto.ActividadRequest;
import com.uap.controlMedico.model.dto.catalagoDto.ActividadResponse;
import com.uap.controlMedico.model.dto.jornadaDto.ActividadJornadaRequest;
import com.uap.controlMedico.model.dto.jornadaDto.ActividadJornadaResponse;
import com.uap.controlMedico.model.entity.Actividad;
import com.uap.controlMedico.model.entity.ActividadJornada;
import com.uap.controlMedico.model.entity.InternoJornada;
import com.uap.controlMedico.model.entity.Jornada;
import com.uap.controlMedico.model.entity.Persona;
import com.uap.controlMedico.model.entity.Titular;
import com.uap.controlMedico.model.entity.TitularJornada;
import com.uap.controlMedico.model.enums.EstadoActividad;
import com.uap.controlMedico.model.enums.EstadoCatalogo;
import com.uap.controlMedico.model.enums.TipoActividad;
import com.uap.controlMedico.model.service.SisuMapper;
import com.uap.controlMedico.model.service.notificacion.NotificacionService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ActividadServiceImpl implements ActividadService {

    private final ActividadDao        actividadDao;
    private final ActividadJornadaDao actividadJornadaDao;
    private final InternoJornadaDao   internoJornadaDao;
    private final TitularJornadaDao   titularJornadaDao;
    private final TitularDao          titularDao;
    private final JornadaDao          jornadaDao;
    private final NotificacionService notificacionService;

    // ════════════════════════════════════════════════════════════════════════
    //  CATÁLOGO
    // ════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public Page<ActividadDto> listarConFiltros(String q, TipoActividad tipo,
                                                EstadoCatalogo estado, Pageable pageable) {
        return actividadDao
            .findConFiltros(q == null ? "" : q, tipo, estado, pageable)
            .map(a -> toDto(a, actividadJornadaDao.countGlobalByEstado())); // se simplifica abajo
    }

    @Override
    @Transactional(readOnly = true)
    public ActividadDto buscarPorId(Long id) {
        return toDto(findActividadOrThrow(id), null);
    }

    @Override
    @Transactional
    public ActividadDto crear(ActividadRequest request) {
        if (actividadDao.existsByNombreIgnoreCase(request.getNombre()))
            throw new IllegalArgumentException("Ya existe una actividad con ese nombre");

        Actividad a = Actividad.builder()
            .nombre(request.getNombre().trim())
            .descripcion(request.getDescripcion())
            .tipoActividad(request.getTipoActividad() != null
                ? request.getTipoActividad() : TipoActividad.CLINICA)
            .estado(EstadoCatalogo.ACTIVO)
            .activo(request.isActivo())
            .build();
        return toDto(actividadDao.save(a), null);
    }

    @Override
    @Transactional
    public ActividadDto actualizar(Long id, ActividadRequest request) {
        Actividad a = findActividadOrThrow(id);
        if (actividadDao.existsByNombreIgnoreCaseAndIdActividadNot(request.getNombre(), id))
            throw new IllegalArgumentException("Ya existe otra actividad con ese nombre");

        a.setNombre(request.getNombre().trim());
        a.setDescripcion(request.getDescripcion());
        if (request.getTipoActividad() != null) a.setTipoActividad(request.getTipoActividad());
        a.setActivo(request.isActivo());
        if (!request.isActivo()) a.setEstado(EstadoCatalogo.INACTIVO);
        else a.setEstado(EstadoCatalogo.ACTIVO);
        return toDto(actividadDao.save(a), null);
    }

    @Override
    @Transactional
    public void desactivar(Long id) {
        Actividad a = findActividadOrThrow(id);
        a.setActivo(false);
        a.setEstado(EstadoCatalogo.INACTIVO);
        actividadDao.save(a);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActividadDto> getCatalogoActivo() {
        return actividadDao.findByActivoTrueOrderByNombreAsc()
            .stream().map(a -> toDto(a, null)).toList();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  ASIGNACIONES
    // ════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public ActividadJornadaDto asignar(ActividadJornadaRequest request) {
        // Exactamente uno de los dos debe estar seteado
        boolean tieneInterno  = request.getIdInternoJornada()  != null;
        boolean tieneTitular  = request.getIdTitularJornada()  != null;
        if (tieneInterno == tieneTitular)
            throw new IllegalArgumentException(
                "Debe especificarse exactamente uno: idInternoJornada o idTitularJornada");

        Actividad actividad = findActividadOrThrow(request.getIdActividad());

        ActividadJornada.ActividadJornadaBuilder<?, ?> builder = ActividadJornada.builder()
            .actividad(actividad)
            .estadoActividad(EstadoActividad.PENDIENTE)
            .horaInicio(request.getHoraInicio())
            .horaFin(request.getHoraFin())
            .observaciones(request.getObservaciones());

        if (tieneInterno) {
            InternoJornada ij = internoJornadaDao.findById(request.getIdInternoJornada())
                .orElseThrow(() -> new EntityNotFoundException(
                    "InternoJornada no encontrada: " + request.getIdInternoJornada()));
            builder.internoJornada(ij);
        } else {
            TitularJornada tj = titularJornadaDao.findById(request.getIdTitularJornada())
                .orElseThrow(() -> new EntityNotFoundException(
                    "TitularJornada no encontrada: " + request.getIdTitularJornada()));
            builder.titularJornada(tj);
        }

        ActividadJornada saved = actividadJornadaDao.save(builder.build());
        notificacionService.notificarAsignacionActividad(saved);
        return toAjDto(saved);
    }

    @Override
    @Transactional
    public ActividadJornadaDto cambiarEstado(Long id, CambiarEstadoActividadRequest req) {
        ActividadJornada aj = findAjOrThrow(id);
        aj.setEstadoActividad(req.getEstado());
        if (req.getObservaciones() != null) aj.setObservaciones(req.getObservaciones());
        return toAjDto(actividadJornadaDao.save(aj));
    }

    @Override
    @Transactional
    public ActividadJornadaDto validar(Long id, ValidarActividadRequest req) {
        ActividadJornada aj = findAjOrThrow(id);
        Titular titular = titularDao.findById(req.getIdTitular())
            .orElseThrow(() -> new EntityNotFoundException("Titular no encontrado: " + req.getIdTitular()));

        aj.setValidadoPor(titular);
        aj.setFechaValidacion(LocalDateTime.now());
        if (req.getObservaciones() != null) aj.setObservaciones(req.getObservaciones());
        // Al validar, si está en progreso → se marca completada automáticamente
        if (aj.getEstadoActividad() == EstadoActividad.EN_PROGRESO)
            aj.setEstadoActividad(EstadoActividad.COMPLETADA);

        return toAjDto(actividadJornadaDao.save(aj));
    }

    @Override
    @Transactional
    public void remover(Long id) {
        actividadJornadaDao.delete(findAjOrThrow(id));
    }

    // ════════════════════════════════════════════════════════════════════════
    //  CONSULTAS
    // ════════════════════════════════════════════════════════════════════════

    @Override @Transactional(readOnly = true)
    public List<ActividadJornadaDto> findByInternoJornada(Long id) {
        return actividadJornadaDao.findByInternoJornadaId(id)
            .stream().map(this::toAjDto).toList();
    }

    @Override @Transactional(readOnly = true)
    public List<ActividadJornadaDto> findByTitularJornada(Long id) {
        return actividadJornadaDao.findByTitularJornadaId(id)
            .stream().map(this::toAjDto).toList();
    }

    @Override @Transactional(readOnly = true)
    public List<ActividadJornadaDto> findByJornada(Long idJornada) {
        return actividadJornadaDao.findByJornada(idJornada)
            .stream().map(this::toAjDto).toList();
    }

    @Override @Transactional(readOnly = true)
    public List<ActividadJornadaDto> findPendientesInterno(Long idInterno) {
        return actividadJornadaDao
            .findPendientesInterno(idInterno,
                List.of(EstadoActividad.PENDIENTE, EstadoActividad.EN_PROGRESO))
            .stream().map(this::toAjDto).toList();
    }

    @Override @Transactional(readOnly = true)
    public List<ActividadJornadaDto> findHistorialInterno(Long idInterno) {
        return actividadJornadaDao.findHistorialInterno(idInterno)
            .stream().map(this::toAjDto).toList();
    }

    @Override @Transactional(readOnly = true)
    public List<ActividadJornadaDto> findHistorialTitular(Long idTitular) {
        return actividadJornadaDao.findHistorialTitular(idTitular)
            .stream().map(this::toAjDto).toList();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  REPORTES
    // ════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public ResumenActividadesDto resumenJornada(Long idJornada) {
        Jornada jornada = jornadaDao.findById(idJornada)
            .orElseThrow(() -> new EntityNotFoundException("Jornada no encontrada: " + idJornada));

        List<ActividadJornada> todas = actividadJornadaDao.findByJornada(idJornada);

        Map<EstadoActividad, Long> conteos = todas.stream()
            .collect(Collectors.groupingBy(ActividadJornada::getEstadoActividad, Collectors.counting()));

        long completadas = conteos.getOrDefault(EstadoActividad.COMPLETADA, 0L);
        long total       = todas.size();

        // Desglose por persona
        Map<String, List<ActividadJornada>> porPersona = new LinkedHashMap<>();
        for (ActividadJornada aj : todas) {
            String key = aj.esDeInterno()
                ? "I:" + aj.getInternoJornada().getInterno().getIdInterno()
                : "T:" + aj.getTitularJornada().getTitular().getIdTitular();
            porPersona.computeIfAbsent(key, k -> new ArrayList<>()).add(aj);
        }

        List<ResumenActividadesDto.DesglosePorPersona> desglose = porPersona.entrySet().stream()
            .map(e -> {
                List<ActividadJornada> lista = e.getValue();
                ActividadJornada primer = lista.get(0);
                boolean esInterno = primer.esDeInterno();
                Persona p = esInterno
                    ? primer.getInternoJornada().getInterno().getPersona()
                    : primer.getTitularJornada().getTitular().getPersona();
                Long idP = esInterno
                    ? primer.getInternoJornada().getInterno().getIdInterno()
                    : primer.getTitularJornada().getTitular().getIdTitular();

                Map<EstadoActividad, Long> ct = lista.stream()
                    .collect(Collectors.groupingBy(ActividadJornada::getEstadoActividad, Collectors.counting()));
                return ResumenActividadesDto.DesglosePorPersona.builder()
                    .idPersona(idP)
                    .nombreCompleto(p.getApellido() + " " + p.getNombre())
                    .tipo(esInterno ? "INTERNO" : "TITULAR")
                    .total(lista.size())
                    .completadas(ct.getOrDefault(EstadoActividad.COMPLETADA, 0L).intValue())
                    .pendientes(ct.getOrDefault(EstadoActividad.PENDIENTE,   0L).intValue())
                    .enProgreso(ct.getOrDefault(EstadoActividad.EN_PROGRESO, 0L).intValue())
                    .build();
            }).toList();

        return ResumenActividadesDto.builder()
            .idJornada(idJornada)
            .nombreJornada(jornada.getNombre() != null
                ? jornada.getNombre()
                : jornada.getTipoJornada().name() + " · " + jornada.getFechaInicio())
            .fechaJornada(jornada.getFechaInicio().toString())
            .total((int) total)
            .pendientes(conteos.getOrDefault(EstadoActividad.PENDIENTE,   0L).intValue())
            .enProgreso(conteos.getOrDefault(EstadoActividad.EN_PROGRESO, 0L).intValue())
            .completadas((int) completadas)
            .canceladas(conteos.getOrDefault(EstadoActividad.CANCELADA,   0L).intValue())
            .porcentajeCompletado(total == 0 ? 0.0 : Math.round((completadas * 100.0 / total) * 10) / 10.0)
            .porPersona(desglose)
            .build();
    }

    @Override
    @Transactional(readOnly = true)
    public EstadisticasActividadesDto estadisticasGlobales() {
        List<Object[]> porEstado = actividadJornadaDao.countGlobalByEstado();
        List<Object[]> porTipo   = actividadJornadaDao.countGlobalByTipo();
        long totalReg = porEstado.stream().mapToLong(r -> (Long) r[1]).sum();

        Map<EstadoActividad, Long> estadoMap = porEstado.stream()
            .collect(Collectors.toMap(r -> (EstadoActividad) r[0], r -> (Long) r[1]));

        List<EstadisticasActividadesDto.ConteoPorClave> listaEstados = porEstado.stream()
            .map(r -> EstadisticasActividadesDto.ConteoPorClave.builder()
                .clave(r[0].toString())
                .cantidad((Long) r[1])
                .porcentaje(totalReg == 0 ? 0 : Math.round(((Long) r[1] * 100.0 / totalReg) * 10) / 10.0)
                .build())
            .toList();

        List<EstadisticasActividadesDto.ConteoPorClave> listaTipos = porTipo.stream()
            .map(r -> EstadisticasActividadesDto.ConteoPorClave.builder()
                .clave(r[0].toString())
                .cantidad((Long) r[1])
                .porcentaje(totalReg == 0 ? 0 : Math.round(((Long) r[1] * 100.0 / totalReg) * 10) / 10.0)
                .build())
            .toList();

        List<Actividad> masUsadas = actividadDao.findMasUsadas(PageRequest.of(0, 5));
        List<EstadisticasActividadesDto.ActividadPopular> populares = masUsadas.stream()
            .map(a -> EstadisticasActividadesDto.ActividadPopular.builder()
                .idActividad(a.getIdActividad())
                .nombre(a.getNombre())
                .tipo(a.getTipoActividad().name())
                .vecesUsada(a.getActividadJornadas().size())
                .build())
            .toList();

        return EstadisticasActividadesDto.builder()
            .totalRegistradas(totalReg)
            .totalCompletadas(estadoMap.getOrDefault(EstadoActividad.COMPLETADA, 0L))
            .totalPendientes(estadoMap.getOrDefault(EstadoActividad.PENDIENTE, 0L))
            .totalEnProgreso(estadoMap.getOrDefault(EstadoActividad.EN_PROGRESO, 0L))
            .totalCanceladas(estadoMap.getOrDefault(EstadoActividad.CANCELADA, 0L))
            .porEstado(listaEstados)
            .porTipo(listaTipos)
            .masUtilizadas(populares)
            .build();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  HELPERS PRIVADOS
    // ════════════════════════════════════════════════════════════════════════

    private Actividad findActividadOrThrow(Long id) {
        return actividadDao.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Actividad no encontrada: " + id));
    }

    private ActividadJornada findAjOrThrow(Long id) {
        return actividadJornadaDao.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("ActividadJornada no encontrada: " + id));
    }

    private ActividadDto toDto(Actividad a, List<Object[]> ignored) {
        return ActividadDto.builder()
            .idActividad(a.getIdActividad())
            .nombre(a.getNombre())
            .descripcion(a.getDescripcion())
            .tipoActividad(a.getTipoActividad())
            .estado(a.getEstado())
            .activo(a.isActivo())
            .vecesUsada(a.getActividadJornadas().size())
            .fechaCreacion(a.getFechaCreacion())
            .build();
    }

    private ActividadJornadaDto toAjDto(ActividadJornada aj) {
        String asignadoA = aj.esDeInterno() ? "INTERNO" : "TITULAR";
        Long   idPersona;
        String nombrePersona;
        Long   idIntJornada = null;
        Long   idTitJornada = null;
        Long   idJornada;
        String nombreJornada;
        String fechaJornada;

        if (aj.esDeInterno()) {
            InternoJornada ij = aj.getInternoJornada();
            Persona p   = ij.getInterno().getPersona();
            idPersona   = ij.getInterno().getIdInterno();
            nombrePersona = p.getApellido() + " " + p.getNombre();
            idIntJornada  = ij.getIdInternoJornada();
            idJornada     = ij.getJornada().getIdJornada();
            nombreJornada = ij.getJornada().getNombre();
            fechaJornada  = ij.getJornada().getFechaInicio().toString();
        } else {
            TitularJornada tj = aj.getTitularJornada();
            Persona p   = tj.getTitular().getPersona();
            idPersona   = tj.getTitular().getIdTitular();
            nombrePersona = p.getApellido() + " " + p.getNombre();
            idTitJornada  = tj.getIdTitularJornada();
            idJornada     = tj.getJornada().getIdJornada();
            nombreJornada = tj.getJornada().getNombre();
            fechaJornada  = tj.getJornada().getFechaInicio().toString();
        }

        String nombreValidador = null;
        Long   idValidadoPor   = null;
        if (aj.getValidadoPor() != null) {
            idValidadoPor   = aj.getValidadoPor().getIdTitular();
            Persona pv = aj.getValidadoPor().getPersona();
            nombreValidador = pv.getApellido() + " " + pv.getNombre();
        }

        return ActividadJornadaDto.builder()
            .idActividadJornada(aj.getIdActividadJornada())
            .idActividad(aj.getActividad().getIdActividad())
            .nombreActividad(aj.getActividad().getNombre())
            .tipoActividad(aj.getActividad().getTipoActividad())
            .descripcionActividad(aj.getActividad().getDescripcion())
            .asignadoA(asignadoA)
            .idInternoJornada(idIntJornada)
            .idTitularJornada(idTitJornada)
            .idPersona(idPersona)
            .nombrePersona(nombrePersona)
            .idJornada(idJornada)
            .nombreJornada(nombreJornada)
            .fechaJornada(fechaJornada)
            .estadoActividad(aj.getEstadoActividad())
            .horaInicio(aj.getHoraInicio())
            .horaFin(aj.getHoraFin())
            .evidenciaUrl(aj.getEvidenciaUrl())
            .observaciones(aj.getObservaciones())
            .validada(aj.estaValidada())
            .idValidadoPor(idValidadoPor)
            .nombreValidador(nombreValidador)
            .fechaValidacion(aj.getFechaValidacion())
            .fechaCreacion(aj.getFechaCreacion())
            .build();
    }
}
