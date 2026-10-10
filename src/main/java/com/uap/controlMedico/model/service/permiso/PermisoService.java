package com.uap.controlMedico.model.service.permiso;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import com.uap.controlMedico.model.dto.permisosDto.AccionConObservacionRequest;
import com.uap.controlMedico.model.dto.permisosDto.AprobarPermisoRequest;
import com.uap.controlMedico.model.dto.permisosDto.DelegacionRequest;
import com.uap.controlMedico.model.dto.permisosDto.DelegacionResponse;
import com.uap.controlMedico.model.dto.permisosDto.SolicitudPermisoRequest;
import com.uap.controlMedico.model.dto.permisosDto.SolicitudPermisoResponse;
import com.uap.controlMedico.model.enums.EstadoDelegacion;
import com.uap.controlMedico.model.enums.EstadoSolicitud;
import com.uap.controlMedico.model.enums.TipoPermiso;

public interface PermisoService {
    // ── Solicitudes ────────────────────────────────────────────────
    Page<SolicitudPermisoResponse> getSolicitudes(Long idTitular, Long idJornada,
        EstadoSolicitud estado, TipoPermiso tipoPermiso, Pageable pageable);
    SolicitudPermisoResponse getSolicitud(Long id);
    SolicitudPermisoResponse crearSolicitud(SolicitudPermisoRequest request);
    SolicitudPermisoResponse modificarSolicitud(Long id, TipoPermiso tipoPermiso,
        String motivoSolicitud, String fechaPermiso, String horaInicio, String horaFin);
    SolicitudPermisoResponse aprobarSolicitud(Long id, AprobarPermisoRequest request, String username);
    SolicitudPermisoResponse rechazarSolicitud(Long id, AccionConObservacionRequest request, String username);
    SolicitudPermisoResponse observarSolicitud(Long id, AccionConObservacionRequest request, String username);
    SolicitudPermisoResponse cancelarSolicitud(Long id);
    List<SolicitudPermisoResponse> getSolicitudesPendientes();
    List<SolicitudPermisoResponse> getSolicitudesPorTitular(Long idTitular);
    Map<String, Object> getEstadisticas();

    // ── Delegaciones ───────────────────────────────────────────────
    Page<DelegacionResponse> getDelegaciones(Long idJornada, EstadoDelegacion estado, Pageable pageable);
    DelegacionResponse getDelegacion(Long id);
    DelegacionResponse crearDelegacion(DelegacionRequest request, String username);
    DelegacionResponse aprobarDelegacion(Long id, AccionConObservacionRequest request, String username);
    DelegacionResponse rechazarDelegacion(Long id, AccionConObservacionRequest request, String username);
    DelegacionResponse iniciarDelegacion(Long id);
    DelegacionResponse completarDelegacion(Long id);
    DelegacionResponse cancelarDelegacion(Long id, AccionConObservacionRequest request);
    List<DelegacionResponse> getDelegacionesActivas();
    List<DelegacionResponse> getDelegacionesPorJornada(Long idJornada);

    SolicitudPermisoResponse subirJustificante(Long idSolicitud, MultipartFile file) throws IOException;
}
