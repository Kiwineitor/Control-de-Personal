package com.uap.controlMedico.model.service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.uap.controlMedico.model.dto.asistenciaDto.RegistroAsistenciaResponse;
import com.uap.controlMedico.model.dto.catalagoDto.ActividadResponse;
import com.uap.controlMedico.model.dto.catalagoDto.LugarResponse;
import com.uap.controlMedico.model.dto.gestionDto.DelegacionResponse;
import com.uap.controlMedico.model.dto.gestionDto.SolicitudPermisoResponse;
import com.uap.controlMedico.model.dto.internosDto.InternoResponse;
import com.uap.controlMedico.model.dto.internosDto.InternoResumenResponse;
import com.uap.controlMedico.model.dto.jornadaDto.ActividadJornadaResponse;
import com.uap.controlMedico.model.dto.jornadaDto.InternoJornadaResponse;
import com.uap.controlMedico.model.dto.jornadaDto.JornadaResponse;
import com.uap.controlMedico.model.dto.permanenciaDto.RondaVerificacionResponse;
import com.uap.controlMedico.model.dto.permanenciaDto.VerificacionPresenciaResponse;
import com.uap.controlMedico.model.dto.titularDto.TitularResponse;
import com.uap.controlMedico.model.dto.titularDto.TitularResumenResponse;
import com.uap.controlMedico.model.dto.usuarioDto.UsuarioResponse;
import com.uap.controlMedico.model.entity.Actividad;
import com.uap.controlMedico.model.entity.ActividadJornada;
import com.uap.controlMedico.model.entity.Delegacion;
import com.uap.controlMedico.model.entity.Interno;
import com.uap.controlMedico.model.entity.InternoJornada;
import com.uap.controlMedico.model.entity.Jornada;
import com.uap.controlMedico.model.entity.Lugar;
import com.uap.controlMedico.model.entity.RegistroAsistencia;
import com.uap.controlMedico.model.entity.ResponsableInterno;
import com.uap.controlMedico.model.entity.RondaVerificacion;
import com.uap.controlMedico.model.entity.SolicitudPermiso;
import com.uap.controlMedico.model.entity.Titular;
import com.uap.controlMedico.model.entity.Usuario;
import com.uap.controlMedico.model.entity.VerificacionPresencia;
import com.uap.controlMedico.model.enums.EstadoAsignacion;
import com.uap.controlMedico.model.enums.EstadoDelegacion;
import com.uap.controlMedico.model.enums.EstadoJornada;
import com.uap.controlMedico.model.enums.EstadoRonda;
import com.uap.controlMedico.model.enums.RolNombre;

@Component
public class SisuMapper {
        // ── Usuario ────────────────────────────────────────────────────────
 
    public UsuarioResponse toUsuarioResponse(Usuario u) {
        if (u == null) return null;
        List<RolNombre> roles = u.getUsuariosRol() == null ? Collections.emptyList()
            : u.getUsuariosRol().stream()
                .map(ur -> {
                    String nombreRol = ur.getRol().getNombre();
                    return nombreRol != null ? RolNombre.valueOf(nombreRol) : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Long idRol = (u.getUsuariosRol() == null || u.getUsuariosRol().isEmpty())
                ? null
                : u.getUsuariosRol().get(0).getRol().getIdRol();

        // Datos de Persona vinculada (si existe)
        var persona       = u.getPersona();
        Long   idPersona  = persona != null ? persona.getIdPersona() : null;
        String ci         = persona != null ? persona.getCi()        : null;
        String telefono   = persona != null ? persona.getTelefono()  : null;
        String nombreCompleto = persona != null ? persona.getNombreCompleto() : null;

        return UsuarioResponse.builder()
                .idUsuario(u.getIdUsuario())
                .username(u.getUsername())
                .email(u.getEmail())
                .nombre(u.getNombre())
                .apellido(u.getApellido())
                .activo(u.isActivo())
                .requiereReset(u.isRequiereReset())
                .ultimoAcceso(u.getUltimoAcceso())
                .perId(u.getPerId())
                .idRol(idRol)
                .roles(roles)
                .fechaCreacion(u.getFechaCreacion())
                .idPersona(idPersona)
                .ci(ci)
                .telefono(telefono)
                .nombreCompleto(nombreCompleto)
                .build();
    }
 
    // ── Titular ────────────────────────────────────────────────────────
 
    public TitularResponse toTitularResponse(Titular t) {
        if (t == null) return null;
        return TitularResponse.builder()
                .idTitular(t.getIdTitular())
                .perId(t.getPerId())
                .contratoId(t.getContratoId())
                .nombre(t.getPersona().getNombre())
                .apellido(t.getPersona().getApellido())
                .nombreCompleto(t.getPersona().getNombreCompleto())
                .ci(t.getPersona().getCi())
                .email(t.getPersona().getEmail())
                .telefono(t.getPersona().getTelefono())
                .cargo(t.getCargo())
                .especialidad(t.getEspecialidad())
                .activo(t.isActivo())
                .fechaCreacion(t.getFechaCreacion())
                .build();
    }
 
    public TitularResumenResponse toTitularResumen(Titular t) {
        if (t == null) return null;
        return TitularResumenResponse.builder()
                .idTitular(t.getIdTitular())
                .perId(t.getPerId())
                .nombreCompleto(t.getPersona().getNombreCompleto())
                .ci(t.getPersona().getCi())
                .cargo(t.getCargo())
                .especialidad(t.getEspecialidad())
                .activo(t.isActivo())
                .build();
    }
 
    // ── Interno ────────────────────────────────────────────────────────
 
    public InternoResponse toInternoResponse(Interno i) {
        if (i == null) return null;
 
        String codigoQr   = null;
        String imagenQrUrl = null;
 
        TitularResumenResponse titular = i.getResponsablesInterno() == null
            ? null
            : i.getResponsablesInterno().stream()
                .filter(ResponsableInterno::isActivo)
                .findFirst()
                .map(ri -> toTitularResumen(ri.getTitular()))
                .orElse(null);
 
        return InternoResponse.builder()
                .idInterno(i.getIdInterno())
                .nombre(i.getPersona().getNombre())
                .apellido(i.getPersona().getApellido())
                .nombreCompleto(i.getPersona().getNombreCompleto())
                .ci(i.getPersona().getCi())
                .email(i.getPersona().getEmail())
                .telefono(i.getPersona().getTelefono())    
                .universidad(i.getUniversidad())
                .carrera(i.getCarrera())
                .anoFormacion(i.getAnoFormacion())
                .nroMatricula(i.getNroMatricula())
                .fechaInicioRotacion(i.getFechaInicioRotacion())
                .fechaFinRotacion(i.getFechaFinRotacion())
                .enRotacionActual(i.estaEnRotacion())
                .activo(i.isActivo())
                .codigoQr(codigoQr)
                .imagenQrUrl(imagenQrUrl)
                .titularResponsable(titular)
                .fechaCreacion(i.getFechaCreacion())
                .build();
    }
 
    public InternoResumenResponse toInternoResumen(Interno i) {
        if (i == null) return null;
        return InternoResumenResponse.builder()
                .idInterno(i.getIdInterno())
                .nombreCompleto(i.getPersona().getNombreCompleto())
                .ci(i.getPersona().getCi())
                .universidad(i.getUniversidad())
                .carrera(i.getCarrera())
                .activo(i.isActivo())
                .build();
    }
 
    // ── Actividad ──────────────────────────────────────────────────────
 
    public ActividadResponse toActividadResponse(Actividad a) {
        if (a == null) return null;
        return ActividadResponse.builder()
                .idActividad(a.getIdActividad())
                .nombre(a.getNombre())
                .descripcion(a.getDescripcion())
                .activo(a.isActivo())
                .build();
    }
 
    // ── Lugar ──────────────────────────────────────────────────────────
 
    public LugarResponse toLugarResponse(Lugar l) {
        return LugarResponse.builder()
                .idLugar(l.getIdLugar())
                .nombre(l.getNombre())
                .descripcion(l.getDescripcion())
                .tipoLugar(l.getTipoLugar())
                .activo(l.isActivo())
                // geofencing
                .latitudCentro(l.getLatitudCentro())
                .longitudCentro(l.getLongitudCentro())
                .radioMetros(l.getRadioMetros())
                .tieneGeofencing(l.tieneGeofencing())
                .fechaCreacion(l.getFechaCreacion())
                .build();
    }
 
    // ── Jornada ────────────────────────────────────────────────────────
 
    public JornadaResponse toJornadaResponse(Jornada j) {
        if (j == null) return null;
        int totalInternos  = j.getInternoJornadas()  == null ? 0 : j.getInternoJornadas().size();
        int totalTitulares = j.getTitularJornadas() == null ? 0 : j.getTitularJornadas().size();
 
        return JornadaResponse.builder()
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
                .enCurso(j.estaEnCurso())
                .totalInternos(totalInternos)
                .totalTitulares(totalTitulares)
                .fechaCreacion(j.getFechaCreacion())
                .creadoPor(j.getCreadoPor())
                .build();
    }
 
    // ── InternoJornada ─────────────────────────────────────────────────
 
    public InternoJornadaResponse toInternoJornadaResponse(InternoJornada ij) {
        if (ij == null) return null;
        return InternoJornadaResponse.builder()
                .idInternoJornada(ij.getIdInternoJornada())
                .interno(toInternoResumen(ij.getInterno()))
                .jornada(toJornadaResponse(ij.getJornada()))
                .lugar(toLugarResponse(ij.getLugar()))
                .estado(ij.getEstado())
                .adjuntoUrl(ij.getAdjuntoUrl())
                .observaciones(ij.getObservaciones())
                .registroAsistencia(toRegistroAsistenciaResponse(ij.getRegistroAsistencia()))
                .build();
    }
 
    // ── RegistroAsistencia ─────────────────────────────────────────────
 
    public RegistroAsistenciaResponse toRegistroAsistenciaResponse(RegistroAsistencia ra) {
        if (ra == null) return null;
        Interno interno = ra.getInternoJornada() != null
            ? ra.getInternoJornada().getInterno() : null;
 
        return RegistroAsistenciaResponse.builder()
                .idRegistroAsistencia(ra.getIdRegistroAsistencia())
                .interno(toInternoResumen(interno))
                .horaEntradaEsperada(ra.getHoraEntradaEsperada())
                .horaSalidaEsperada(ra.getHoraSalidaEsperada())
                .horaEntradaReal(ra.getHoraEntradaReal())
                .horaSalidaReal(ra.getHoraSalidaReal())
                .estadoEntrada(ra.getEstadoEntrada())
                .estadoSalida(ra.getEstadoSalida())
                .metodoRegistroEntrada(ra.getMetodoRegistroEntrada())
                .metodoRegistroSalida(ra.getMetodoRegistroSalida())
                .latitudEntrada(ra.getLatitudEntrada())
                .longitudEntrada(ra.getLongitudEntrada())
                .latitudSalida(ra.getLatitudSalida())
                .longitudSalida(ra.getLongitudSalida())
                .minutosTardanza(ra.getMinutosTardanza())
                .tieneEntrada(ra.tieneEntrada())
                .tieneSalida(ra.tieneSalida())
                .observaciones(ra.getObservaciones())
                .build();
    }
 
    // ── RondaVerificacion ──────────────────────────────────────────────
 
    public RondaVerificacionResponse toRondaResponse(RondaVerificacion r) {
        if (r == null) return null;
        List<VerificacionPresenciaResponse> verificaciones =
            r.getVerificaciones() == null ? Collections.emptyList()
                : r.getVerificaciones().stream()
                    .map(this::toVerificacionResponse)
                    .collect(Collectors.toList());
 
        return RondaVerificacionResponse.builder()
                .idRondaVerificacion(r.getIdRondaVerificacion())
                .nombre(r.getNombre())
                .tipoRonda(r.getTipoRonda())
                .estadoRonda(r.getEstado())
                .fechaHoraInicio(r.getFechaHoraInicio())
                .fechaHoraFin(r.getFechaHoraFin())
                .timeoutMinutos(r.getTimeoutMinutos())
                .creadaPor(r.getCreadoPor())
                .totalVerificaciones(r.getTotalVerificaciones())
                .totalPresentes(r.getTotalPresentes())
                .totalSinRespuesta(r.getTotalSinRespuesta())
                .verificaciones(verificaciones)
                .build();
    }
 
    public VerificacionPresenciaResponse toVerificacionResponse(VerificacionPresencia v) {
        if (v == null) return null;
        return VerificacionPresenciaResponse.builder()
                .idVerificacionPresencia(v.getIdVerificacionPresencia())
                .titular(toTitularResumen(v.getTitular()))
                .interno(toInternoResumen(v.getInterno()))
                .tipoPersonal(v.getTipoPersonal())
                .nombrePersonal(v.getNombrePersonal())
                .estadoVerificacion(v.getEstadoVerificacion())
                .fechaHoraEnvio(v.getFechaHoraEnvio())
                .fechaHoraRespuesta(v.getFechaHoraRespuesta())
                .tiempoRespuestaMinutos(v.getTiempoRespuestaMinutos())
                .latitud(v.getLatitud())
                .longitud(v.getLongitud())
                .fotoUrl(v.getFotoUrl())
                .observaciones(v.getObservaciones())
                .build();
    }
 
    // ── Delegacion ─────────────────────────────────────────────────────
 
    public DelegacionResponse toDelegacionResponse(Delegacion d) {
        if (d == null) return null;
 
        Titular titular = d.getTitularJornada() != null
            ? d.getTitularJornada().getTitular() : null;
        Lugar lugar = d.getTitularJornada() != null
            ? d.getTitularJornada().getLugar() : null;
        String aprobadoPor = d.getAprobadoPor() != null
            ? d.getAprobadoPor().getNombre() + " " + d.getAprobadoPor().getApellido()
            : null;
 
        return DelegacionResponse.builder()
                .idDelegacion(d.getIdDelegacion())
                .titular(toTitularResumen(titular))
                .internoResponsable(toInternoResumen(d.getInternoResponsable()))
                .lugar(toLugarResponse(lugar))
                .horaInicioPlanificada(d.getHoraInicioPlanificada())
                .horaFinPlanificada(d.getHoraFinPlanificada())
                .horaInicioReal(d.getHoraInicioReal())
                .horaFinReal(d.getHoraFinReal())
                .motivo(d.getMotivo())
                .estado(d.getEstado())
                .aprobadoPor(aprobadoPor)
                .fechaAprobacion(d.getFechaAprobacion())
                .observacionAprobacion(d.getObservacionAprobacion())
                .fechaCreacion(d.getFechaCreacion())
                .build();
    }
 
    // ── ActividadJornada ───────────────────────────────────────────────
 
    public ActividadJornadaResponse toActividadJornadaResponse(ActividadJornada aj) {
        if (aj == null) return null;
        return ActividadJornadaResponse.builder()
                .idActividadJornada(aj.getIdActividadJornada())
                .actividad(toActividadResponse(aj.getActividad()))
                .estadoActividad(aj.getEstadoActividad())
                .horaInicio(aj.getHoraInicio())
                .horaFin(aj.getHoraFin())
                .evidenciaUrl(aj.getEvidenciaUrl())
                .observaciones(aj.getObservaciones())
                .build();
    }

}
