package com.uap.controlMedico.model.service.appActividades;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.uap.controlMedico.mapper.ActividadJornadaMapper;
import com.uap.controlMedico.model.dao.ActividadDao;
import com.uap.controlMedico.model.dao.ActividadJornadaDao;
import com.uap.controlMedico.model.dao.EventoAbandonoDao;
import com.uap.controlMedico.model.dao.InternoJornadaDao;
import com.uap.controlMedico.model.dao.SolicitudPermisoDao;
import com.uap.controlMedico.model.dao.TitularDao;
import com.uap.controlMedico.model.dao.TitularJornadaDao;
import com.uap.controlMedico.model.dao.UsuarioDao;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Locale;

import com.uap.controlMedico.model.dto.actividadDto.ActividadCatalogoDto;
import com.uap.controlMedico.model.dto.actividadDto.ActividadJornadaResponse;
import com.uap.controlMedico.model.dto.actividadDto.AutoReporteRequest;
import com.uap.controlMedico.model.dto.actividadDto.DiaTurnoCalendarioDto;
import com.uap.controlMedico.model.dto.actividadDto.MiFaltaDto;
import com.uap.controlMedico.model.dto.actividadDto.MiJornadaResponse;
import com.uap.controlMedico.model.dto.actividadDto.MiSolicitudRequest;
import com.uap.controlMedico.model.dto.permisosDto.SolicitudPermisoRequest;
import com.uap.controlMedico.model.dto.permisosDto.SolicitudPermisoResponse;
import com.uap.controlMedico.model.entity.Actividad;
import com.uap.controlMedico.model.entity.ActividadJornada;
import com.uap.controlMedico.model.entity.InternoJornada;
import com.uap.controlMedico.model.entity.Jornada;
import com.uap.controlMedico.model.entity.Lugar;
import com.uap.controlMedico.model.entity.SolicitudPermiso;
import com.uap.controlMedico.model.entity.Titular;
import com.uap.controlMedico.model.entity.TitularJornada;
import com.uap.controlMedico.model.entity.Usuario;
import com.uap.controlMedico.model.enums.EstadoActividad;
import com.uap.controlMedico.model.enums.TipoAsignacion;
import com.uap.controlMedico.model.enums.TipoPermiso;
import com.uap.controlMedico.model.service.permiso.PermisoService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppFuncionarioServiceImpl implements AppFuncionarioService {
    
    private final UsuarioDao              usuarioDao;
    private final TitularJornadaDao       titularJornadaDao;
    private final InternoJornadaDao       internoJornadaDao;
    private final ActividadJornadaDao     actividadJornadaDao;
    private final ActividadDao            actividadDao;
    private final ActividadJornadaMapper  mapper;
    private final TitularDao              titularDao;
    private final PermisoService          permisoService;
    private final SolicitudPermisoDao     solicitudDao;
    private final EventoAbandonoDao       eventoAbandonoDao;

    // ── Mi Jornada ────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public MiJornadaResponse getMiJornada() {
        AsignacionActual asig = resolverAsignacion();
        if (asig == null) return null;

        var jornada  = asig.jornada();
        var misActs  = misActividadesInternal(asig);

        return MiJornadaResponse.builder()
            .idJornada(jornada.getIdJornada())
            .nombre(jornada.getNombre())
            .fechaInicio(jornada.getFechaInicio())
            .fechaFin(jornada.getFechaFin())
            .horaInicio(horaInicioPropia(asig))
            .horaFin(horaFinPropia(asig))
            .nombreLugar(nombreLugarVigilado(asig))
            .estadoJornada(jornada.getEstado().name())
            .tipoAsignacion(asig.tipo())
            .idAsignacion(asig.idAsignacion())
            .totalActividades(misActs.size())
            .actividadesPendientes(
                (int) misActs.stream().filter(a -> !a.isCulminada()).count())
            .actividadesCompletadas(
                (int) misActs.stream().filter(a ->
                    a.isCulminada() && a.getEstadoActividad() != EstadoActividad.VALIDADA
                ).count())
            .actividadesValidadas(
                (int) misActs.stream().filter(a ->
                    a.getEstadoActividad() == EstadoActividad.VALIDADA
                ).count())
            .idTitular(asig.tipo() == TipoAsignacion.TITULAR
                ? asig.titularJornada().getTitular().getIdTitular() : null)
            .build();
    }

    // ── Mis Actividades ───────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<ActividadJornadaResponse> getMisActividades(Boolean culminada) {
        AsignacionActual asig = resolverAsignacion();
        if (asig == null) return List.of();

        List<ActividadJornada> list = misActividadesInternal(asig);

        if (culminada != null)
            list = list.stream().filter(a -> a.isCulminada() == culminada).toList();

        return list.stream().map(mapper::toResponse).toList();
    }

    // ── Auto-reporte ──────────────────────────────────────────

    @Override
    @Transactional
    public ActividadJornadaResponse crearMiActividad(AutoReporteRequest request) {
        AsignacionActual asig = resolverAsignacion();
        if (asig == null)
            throw new IllegalStateException("No tienes una jornada activa asignada");

        Actividad actividad = actividadDao.findById(request.getIdActividad())
            .orElseThrow(() -> new EntityNotFoundException(
                "Actividad no encontrada: " + request.getIdActividad()));

        if (!actividad.isActivo())
            throw new IllegalStateException("La actividad no está disponible en el catálogo");

        var nueva = ActividadJornada.builder()
            .actividad(actividad)
            .estadoActividad(EstadoActividad.PENDIENTE)
            .observaciones(request.getObservaciones())
            .horaInicio(request.getHoraInicio())
            .horaFin(request.getHoraFin())
            .build();

        if (asig.tipo() == TipoAsignacion.TITULAR)
            nueva.setTitularJornada(asig.titularJornada());
        else
            nueva.setInternoJornada(asig.internoJornada());

        return mapper.toResponse(actividadJornadaDao.save(nueva));
    }

    // ── Catálogo ──────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<ActividadCatalogoDto> getCatalogoActividades() {
        return actividadDao.findByActivoTrueOrderByNombreAsc().stream()
            .map(a -> ActividadCatalogoDto.builder()
                .idActividad(a.getIdActividad())
                .nombre(a.getNombre())
                .descripcion(a.getDescripcion())
                .tipoActividad(a.getTipoActividad())
                .build())
            .toList();
    }

    // ── Helpers privados ──────────────────────────────────────

    /**
     * Resuelve quién es el usuario actual y en qué jornada está.
     *
     * IMPORTANTE: la identificación se hace por persona.idPersona (ID interno del
     * sistema), NO por usuario.perId (ID de Londra). El usuario tiene una relación
     * @ManyToOne con Persona; esa persona es la misma que está vinculada al Titular
     * o Interno en las tablas titular_jornada / interno_jornada.
     */
    private AsignacionActual resolverAsignacion() {
        Authentication auth    = SecurityContextHolder.getContext().getAuthentication();
        Usuario        usuario = usuarioDao.findByUsernameWithPersona(auth.getName())
            .orElseThrow(() -> new EntityNotFoundException(
                "Usuario no encontrado: " + auth.getName()));

        // Si el usuario no tiene Persona vinculada, no puede tener jornada
        if (usuario.getPersona() == null) return null;
        Long idPersona = usuario.getPersona().getIdPersona();

        // 1. Buscar como TITULAR (MEDICO, ENFERMERO, CAJERO, etc.)
        List<TitularJornada> titulares = titularJornadaDao.findActivasByPersona(idPersona);
        if (!titulares.isEmpty()) {
            TitularJornada tj = titulares.get(0);
            return new AsignacionActual(
                TipoAsignacion.TITULAR,
                tj.getIdTitularJornada(),
                tj, null,
                tj.getJornada()
            );
        }

        // 2. Buscar como INTERNO
        List<InternoJornada> internos = internoJornadaDao.findActivasByPersona(idPersona);
        if (!internos.isEmpty()) {
            InternoJornada ij = internos.get(0);
            return new AsignacionActual(
                TipoAsignacion.INTERNO,
                ij.getIdInternoJornada(),
                null, ij,
                ij.getJornada()
            );
        }

        return null;
    }

    private List<ActividadJornada> misActividadesInternal(AsignacionActual asig) {
        return asig.tipo() == TipoAsignacion.TITULAR
            ? actividadJornadaDao.findByTitularJornada(asig.idAsignacion())
            : actividadJornadaDao.findByInternoJornada(asig.idAsignacion());
    }

    /** "HH:mm" o null — formato que espera el calendario de la app. */
    private static String hhmm(LocalTime t) {
        return t != null ? t.toString().substring(0, 5) : null;
    }

    // ── Horario propio del funcionario ──────────────────────────────────────
    // El horario de la jornada es el valor por defecto del turno, no el de cada
    // persona: en un mismo turno conviven 08–20, 08–14 y 14–20. La app mostraba
    // a las tres el mismo horario general, que para dos de ellas era falso.

    private static LocalTime horaInicioPropia(AsignacionActual asig) {
        TitularJornada tj = asig.titularJornada();
        if (tj != null) {
            // Quien solo cubre la tarde de una partida empieza en la franja de tarde.
            LocalTime ini = tj.isTrabajaManana() ? tj.inicioManana() : tj.inicioTarde();
            return ini != null ? ini : tj.inicioManana();
        }
        if (asig.internoJornada() != null
                && asig.internoJornada().getHoraInicioOverride() != null)
            return asig.internoJornada().getHoraInicioOverride();
        return asig.jornada() != null ? asig.jornada().getHoraInicio() : null;
    }

    private static LocalTime horaFinPropia(AsignacionActual asig) {
        TitularJornada tj = asig.titularJornada();
        if (tj != null) {
            // En jornada partida el día termina cuando cierra la última franja.
            return tj.esPartida() && tj.isTrabajaTarde() ? tj.finTarde() : tj.finManana();
        }
        if (asig.internoJornada() != null
                && asig.internoJornada().getHoraFinOverride() != null)
            return asig.internoJornada().getHoraFinOverride();
        return asig.jornada() != null ? asig.jornada().getHoraFin() : null;
    }

    /** Contexto de sesión del funcionario — record inmutable */
    private record AsignacionActual(
        TipoAsignacion  tipo,
        Long            idAsignacion,
        TitularJornada  titularJornada,
        InternoJornada  internoJornada,
        Jornada jornada
    ) {}

    /**
     * Lugar que la app debe mostrar: el MISMO contra el que se lo vigila.
     *
     * Antes esto devolvía solo el lugar de la jornada, mientras el geofence usa el
     * de la asignación con precedencia. Cuando ambos diferían la app anunciaba una
     * sede y las alertas citaban otra — el funcionario veía "RECTORADO" en su inicio
     * y le llegaban avisos de estar fuera de zona en SISU, sin forma de entender por
     * qué. La regla de resolución debe ser una sola.
     */
    private static String nombreLugarVigilado(AsignacionActual asig) {
        Lugar propio = asig.tipo() == TipoAsignacion.TITULAR
            ? (asig.titularJornada() != null ? asig.titularJornada().getLugar() : null)
            : (asig.internoJornada() != null ? asig.internoJornada().getLugar() : null);

        Lugar efectivo = propio != null
            ? propio
            : (asig.jornada() != null ? asig.jornada().getLugar() : null);

        return efectivo != null ? efectivo.getNombre() : null;
    }


    // ── Mis permisos ──────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<SolicitudPermisoResponse> getMisPermisos() {
        Long idPersona = getPersonaId();
        if (idPersona == null) return List.of();
        return titularDao.findByPersona_IdPersona(idPersona)
            .map(t -> permisoService.getSolicitudesPorTitular(t.getIdTitular()))
            .orElse(List.of());
    }

    @Override
    @Transactional
    public SolicitudPermisoResponse modificarMiPermiso(Long idSolicitud, MiSolicitudRequest request)
            throws java.nio.file.AccessDeniedException {
        Long idPersona = getPersonaId();
        Titular titular = titularDao.findByPersona_IdPersona(idPersona)
            .orElseThrow(() -> new IllegalStateException("No eres un titular registrado"));

        SolicitudPermiso solicitud = solicitudDao.findById(idSolicitud)
            .orElseThrow(() -> new EntityNotFoundException("Solicitud no encontrada"));

        if (!solicitud.getTitular().getIdTitular().equals(titular.getIdTitular()))
            throw new java.nio.file.AccessDeniedException("Esta solicitud no te pertenece");

        return permisoService.modificarSolicitud(
            idSolicitud,
            request.getTipoPermiso(),
            request.getMotivoSolicitud(),
            request.getFechaPermiso(),
            request.getHoraInicio(),
            request.getHoraFin()
        );
    }

    @Override
    @Transactional
    public SolicitudPermisoResponse crearMiPermiso(MiSolicitudRequest request) {
        Long idPersona = getPersonaId();
        Titular titular = titularDao.findByPersona_IdPersona(idPersona)
            .orElseThrow(() -> new IllegalStateException("No eres un titular registrado"));

        SolicitudPermisoRequest req = new SolicitudPermisoRequest();
        req.setIdTitular(titular.getIdTitular());
        req.setTipoPermiso(request.getTipoPermiso());
        req.setMotivoSolicitud(request.getMotivoSolicitud());
        req.setFechaPermiso(request.getFechaPermiso());
        req.setHoraInicioSolicitada(request.getHoraInicio());
        req.setHoraFinSolicitada(request.getHoraFin());

        // Vincular a jornada activa si existe
        AsignacionActual asig = resolverAsignacion();
        if (asig != null && asig.tipo() == TipoAsignacion.TITULAR)
            req.setIdTitularJornada(asig.idAsignacion());

        return permisoService.crearSolicitud(req); // dispara WS al admin
    }

    @Override
    @Transactional
    public SolicitudPermisoResponse subirMiJustificante(Long idSolicitud, MultipartFile file)
        throws IOException {
        Long idPersona = getPersonaId();
        Titular titular = titularDao.findByPersona_IdPersona(idPersona)
            .orElseThrow(() -> new AccessDeniedException("No eres un titular"));
        SolicitudPermiso sol = solicitudDao.findById(idSolicitud)
            .orElseThrow(() -> new EntityNotFoundException("Solicitud no encontrada"));
        if (!sol.getTitular().getIdTitular().equals(titular.getIdTitular()))
            throw new AccessDeniedException("Esta solicitud no te pertenece");
        return permisoService.subirJustificante(idSolicitud, file);
    }

    @Override
    public List<Map<String, String>> getTiposPermiso() {
        return Arrays.stream(TipoPermiso.values())
            .map(t -> Map.of("value", t.name(), "label", t.getLabel()))
            .toList();
    }

    // ── Mis Turnos (Calendario) ───────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<DiaTurnoCalendarioDto> getMisTurnos(int mes, int anio) {
        Long idPersona = getPersonaId();
        if (idPersona == null) return List.of();

        YearMonth ym       = YearMonth.of(anio, mes);
        LocalDate primerDia = ym.atDay(1);
        LocalDate ultimoDia = ym.atEndOfMonth();
        LocalDate hoy       = LocalDate.now();

        List<TitularJornada> tjs = titularJornadaDao
            .findByPersonaAndRango(idPersona, primerDia, ultimoDia);

        List<DiaTurnoCalendarioDto> resultado = new ArrayList<>();

        for (TitularJornada tj : tjs) {
            Jornada j = tj.getJornada();
            if (j == null) continue;

            // Lugar: preferir el del titular, luego el de la jornada
            String nombreLugar = tj.getLugar() != null ? tj.getLugar().getNombre()
                : (j.getLugar() != null ? j.getLugar().getNombre() : null);

            // Días específicos del titular en esta jornada
            List<LocalDate> diasTrabajo = tj.getDiasTrabajo();

            // Si no tiene días específicos, generar todos los días del rango
            // intersectados con el mes pedido
            List<LocalDate> diasEnMes;
            if (diasTrabajo.isEmpty()) {
                diasEnMes = new ArrayList<>();
                LocalDate desde = j.getFechaInicio().isBefore(primerDia) ? primerDia : j.getFechaInicio();
                LocalDate hasta = j.getFechaFin().isAfter(ultimoDia)     ? ultimoDia : j.getFechaFin();
                for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1)) {
                    diasEnMes.add(d);
                }
            } else {
                diasEnMes = diasTrabajo.stream()
                    .filter(d -> !d.isBefore(primerDia) && !d.isAfter(ultimoDia))
                    .sorted()
                    .toList();
            }

            // Horario EFECTIVO de este titular: su override si lo tiene, si no el
            // de la jornada. Antes se volcaba siempre el general y el funcionario
            // con horario propio veía en su calendario un horario que no es el suyo.
            boolean partida = tj.esPartida();
            String horaI, horaF, horaIT = null, horaFT = null;
            if (partida && !tj.isTrabajaManana()) {
                // Solo cubre la tarde: para él es una franja única, no una partida.
                horaI = hhmm(tj.inicioTarde());
                horaF = hhmm(tj.finTarde());
                partida = false;
            } else {
                horaI = hhmm(tj.inicioManana());
                horaF = hhmm(tj.finManana());
                if (partida && tj.isTrabajaTarde()) {
                    horaIT = hhmm(tj.inicioTarde());
                    horaFT = hhmm(tj.finTarde());
                } else {
                    partida = false;
                }
            }
            String modalidad = partida ? "PARTIDA" : "CONTINUA";
            String tipoTurno = j.getTipoJornada() != null ? j.getTipoJornada().name() : "REGULAR";
            String nombre    = j.getNombre() != null ? j.getNombre()
                : (tipoTurno + " · " + j.getFechaInicio() + " – " + j.getFechaFin());

            for (LocalDate dia : diasEnMes) {
                resultado.add(DiaTurnoCalendarioDto.builder()
                    .fecha(dia.toString())
                    .diaSemana(dia.getDayOfWeek().getDisplayName(TextStyle.FULL, new Locale("es")))
                    .horaInicio(horaI)
                    .horaFin(horaF)
                    .modalidad(modalidad)
                    .horaInicioTarde(horaIT)
                    .horaFinTarde(horaFT)
                    .nombreTurno(nombre)
                    .tipoTurno(tipoTurno)
                    .idJornada(j.getIdJornada())
                    .nombreLugar(nombreLugar)
                    .esHoy(dia.equals(hoy))
                    .esPasado(dia.isBefore(hoy))
                    .build());
            }
        }

        resultado.sort(java.util.Comparator.comparing(DiaTurnoCalendarioDto::getFecha));
        return resultado;
    }

    // ── Mis Faltas (historial de eventos de abandono) ─────────────

    @Override
    @Transactional(readOnly = true)
    public List<MiFaltaDto> getMisFaltas() {
        Long idPersona = getPersonaId();
        if (idPersona == null) return List.of();

        // Buscar como titular primero, luego como interno
        List<com.uap.controlMedico.model.entity.EventoAbandono> eventos =
            eventoAbandonoDao.findByTitularPersona(idPersona);
        if (eventos.isEmpty()) {
            eventos = eventoAbandonoDao.findByInternoPersona(idPersona);
        }

        return eventos.stream().map(e -> MiFaltaDto.builder()
            .idEvento(e.getIdEvento())
            .estado(e.getEstado().name())
            .nombreLugar(e.getSesion() != null && e.getSesion().getLugar() != null
                ? e.getSesion().getLugar().getNombre() : null)
            .timestampSalida(e.getTimestampSalida() != null
                ? e.getTimestampSalida().toString() : null)
            .timestampRetorno(e.getTimestampRetorno() != null
                ? e.getTimestampRetorno().toString() : null)
            .tiempoFueraMinutos(e.getTiempoFueraMinutos())
            .observaciones(e.getObservaciones())
            .tipo(e.getTipoEfectivo().name())
            .tipoLabel(e.getTipoEfectivo().getLabel())
            .estadoJustificacion(e.getEstadoJustificacionEfectivo().name())
            .estadoJustificacionLabel(e.getEstadoJustificacionEfectivo().getLabel())
            .puedeJustificar(e.isPuedeJustificar())
            .motivoJustificacion(e.getMotivoJustificacion())
            .observacionRevision(e.getObservacionRevision())
            .minutosIncidencia(e.getMinutosIncidencia())
            .build()
        ).toList();
    }

    // ── Helper: idPersona del usuario actual ─────────────────────
    // Devuelve persona.idPersona (ID interno), no perId de Londra.
    private Long getPersonaId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return usuarioDao.findByUsernameWithPersona(auth.getName())
            .map(u -> u.getPersona() != null ? u.getPersona().getIdPersona() : null)
            .orElse(null);
    }


}
