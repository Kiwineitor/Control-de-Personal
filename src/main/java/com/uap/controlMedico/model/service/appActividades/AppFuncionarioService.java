package com.uap.controlMedico.model.service.appActividades;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.web.multipart.MultipartFile;

import com.uap.controlMedico.model.dto.actividadDto.ActividadCatalogoDto;
import com.uap.controlMedico.model.dto.actividadDto.ActividadJornadaResponse;
import com.uap.controlMedico.model.dto.actividadDto.AutoReporteRequest;
import com.uap.controlMedico.model.dto.actividadDto.DiaTurnoCalendarioDto;
import com.uap.controlMedico.model.dto.actividadDto.MiFaltaDto;
import com.uap.controlMedico.model.dto.actividadDto.MiJornadaResponse;
import com.uap.controlMedico.model.dto.actividadDto.MiSolicitudRequest;
import com.uap.controlMedico.model.dto.permisosDto.SolicitudPermisoResponse;

public interface AppFuncionarioService {
    /** Jornada activa del usuario actual + resumen. Null si no tiene jornada. */
    MiJornadaResponse getMiJornada();

    /** Actividades del usuario en su jornada activa */
    List<ActividadJornadaResponse> getMisActividades(Boolean culminada);

    /** El funcionario registra una actividad propia */
    ActividadJornadaResponse crearMiActividad(AutoReporteRequest request);

    /** Catálogo de actividades activas (para el selector) */
    List<ActividadCatalogoDto> getCatalogoActividades();

    /** Días de turno del usuario en el mes/año solicitado */
    List<DiaTurnoCalendarioDto> getMisTurnos(int mes, int anio);

    /** Historial de eventos de abandono de zona del usuario actual */
    List<MiFaltaDto> getMisFaltas();

    List<SolicitudPermisoResponse>  getMisPermisos();
    SolicitudPermisoResponse        crearMiPermiso(MiSolicitudRequest request);
    SolicitudPermisoResponse        modificarMiPermiso(Long idSolicitud, MiSolicitudRequest request) throws java.nio.file.AccessDeniedException;
    SolicitudPermisoResponse        subirMiJustificante(Long idSolicitud, MultipartFile file) throws IOException;
    List<Map<String, String>>       getTiposPermiso();
}