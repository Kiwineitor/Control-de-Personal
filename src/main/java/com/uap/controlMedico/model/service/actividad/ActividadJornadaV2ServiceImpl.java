package com.uap.controlMedico.model.service.actividad;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.uap.controlMedico.mapper.ActividadJornadaMapper;
import com.uap.controlMedico.model.dao.ActividadJornadaDao;
import com.uap.controlMedico.model.dao.InternoJornadaDao;
import com.uap.controlMedico.model.dao.UsuarioDao;
import com.uap.controlMedico.model.dto.actividadDto.ActividadJornadaResponse;
import com.uap.controlMedico.model.dto.actividadDto.CarryOverResultDto;
import com.uap.controlMedico.model.dto.actividadDto.CulminarActividadRequest;
import com.uap.controlMedico.model.dto.actividadDto.SubirEvidenciaResponse;
import com.uap.controlMedico.model.entity.ActividadJornada;
import com.uap.controlMedico.model.entity.InternoJornada;
import com.uap.controlMedico.model.entity.Usuario;
import com.uap.controlMedico.model.enums.EstadoActividad;
import com.uap.controlMedico.shared.storage.FileStorageService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActividadJornadaV2ServiceImpl implements ActividadJornadaV2Service {
    
    private final ActividadJornadaDao actividadJornadaDao;
    private final InternoJornadaDao   internoJornadaDao;
    private final UsuarioDao          usuarioDao;
    private final ActividadJornadaMapper   mapper;
    private final FileStorageService fileStorageService;

    // ── CARRY-OVER ────────────────────────────────────────────────────

    @Override
    @Transactional
    public CarryOverResultDto ejecutarCarryOver(Long idJornada) {
        List<ActividadJornada> pendientes =
            actividadJornadaDao.findPendientesByJornada(idJornada);

        int traspasados     = 0;
        int sinDestino      = 0;
        List<String> detalle = new ArrayList<>();

        for (ActividadJornada original : pendientes) {
            Long idInterno = original.getInternoJornada().getInterno().getIdInterno();

            InternoJornada siguienteIJ = internoJornadaDao
                .findProximasActivasParaInterno(idInterno, idJornada)
                .stream()
                .findFirst()
                .orElse(null);

            if (siguienteIJ == null) {
                sinDestino++;
                String nombreI = original.getInternoJornada().getInterno()
                    .getPersona().getNombre();
                detalle.add("[SIN DESTINO] " + nombreI + " — " +
                    original.getActividad().getNombre());
                continue;
            }

            // este InternoJornada destino
        if (actividadJornadaDao.existsByCarryOverDeAndInternoJornada(
                original, siguienteIJ)) {
            detalle.add("[DUPLICADO OMITIDO] " + original.getActividad().getNombre());
            continue;
        }

        ActividadJornada copia = ActividadJornada.builder()
            .internoJornada(siguienteIJ)
            .actividad(original.getActividad())
            .estadoActividad(EstadoActividad.PENDIENTE)
            .culminada(false)
            .carryOverDe(original)
            .observaciones("Traspasado de: " +
                original.getInternoJornada().getJornada().getNombre())
            .build();

        actividadJornadaDao.save(copia);
        traspasados++;
        detalle.add("[OK] " + original.getActividad().getNombre() +
            " → " + siguienteIJ.getJornada().getNombre());
    }

    log.info("Carry-over jornada {}: {} pendientes | {} traspasados | {} sin destino",
        idJornada, pendientes.size(), traspasados, sinDestino);

    return CarryOverResultDto.builder()
        .totalPendientes(pendientes.size())
        .traspasados(traspasados)
        .sinJornadaDestino(sinDestino)
        .detalles(detalle)
        .build();
}

// ── EVIDENCIA ─────────────────────────────────────────────────────

@Override
@Transactional
public SubirEvidenciaResponse subirEvidencia(Long idActividadJornada, MultipartFile file)
    throws IOException {
    ActividadJornada aj = findOrThrow(idActividadJornada);
    verificarPermiso(aj);

    if (aj.getEstadoActividad() == EstadoActividad.VALIDADA)
        throw new IllegalStateException(
            "No se puede modificar una actividad ya validada");

    // Reemplazar archivo anterior
    if (aj.getEvidenciaUrl() != null)
        fileStorageService.eliminarArchivo(aj.getEvidenciaUrl());

    String rutaRelativa = fileStorageService.guardarEvidencia(file, idActividadJornada);
    aj.setEvidenciaUrl(rutaRelativa);
    actividadJornadaDao.save(aj);

    return SubirEvidenciaResponse.builder()
        .idActividadJornada(idActividadJornada)
        .evidenciaUrl(rutaRelativa)
        .downloadUrl(fileStorageService.generarDownloadUrl(rutaRelativa))
        .mensaje("Evidencia cargada correctamente")
        .build();
}

// ── CULMINAR ──────────────────────────────────────────────────────

@Override
@Transactional
public ActividadJornadaResponse culminarActividad(Long idActividadJornada,
                                                   CulminarActividadRequest request) {
    ActividadJornada aj = findOrThrow(idActividadJornada);
    verificarPermiso(aj);

    if (aj.isCulminada())
        throw new IllegalStateException("La actividad ya fue marcada como culminada");
    if (aj.getEstadoActividad() == EstadoActividad.VALIDADA)
        throw new IllegalStateException(
            "No se puede modificar una actividad ya validada");

    aj.setCulminada(true);
    aj.setFechaCulminacion(LocalDateTime.now());
    aj.setEstadoActividad(EstadoActividad.COMPLETADA);

    if (request.getObservaciones() != null && !request.getObservaciones().isBlank())
        aj.setObservaciones(request.getObservaciones());

    return mapper.toResponse(actividadJornadaDao.save(aj));
}

// ── CONSULTAS ─────────────────────────────────────────────────────

@Override
@Transactional(readOnly = true)
public Page<ActividadJornadaResponse> listarPorJornada(Long idJornada,
                                                        Boolean culminada,
                                                        Pageable pageable) {
    Page<ActividadJornada> page = (culminada == null)
        ? actividadJornadaDao.findByJornada(idJornada, pageable)
        : actividadJornadaDao.findByJornadaAndCulminada(idJornada, culminada, pageable);
    return page.map(mapper::toResponse);
}

@Override
@Transactional(readOnly = true)
public ActividadJornadaResponse findById(Long idActividadJornada) {
    return mapper.toResponse(findOrThrow(idActividadJornada));
}

// ── HELPERS ───────────────────────────────────────────────────────

private ActividadJornada findOrThrow(Long id) {
    return actividadJornadaDao.findById(id)
        .orElseThrow(() -> new EntityNotFoundException(
            "ActividadJornada no encontrada: " + id));
}

/** ADMIN y SUPERVISOR pasan libre. Otros roles: solo el asignado. */
private void verificarPermiso(ActividadJornada aj) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) throw new AccessDeniedException("Sin sesión activa");

    boolean esPrivilegiado = auth.getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                   || a.getAuthority().equals("ROLE_SUPERVISOR"));
    if (esPrivilegiado) return;

    // findByUsernameWithPersona hace LEFT JOIN FETCH de persona
    Usuario usuario = usuarioDao.findByUsernameWithPersona(auth.getName())
        .orElseThrow(() -> new AccessDeniedException(
            "Usuario no encontrado: " + auth.getName()));

    if (usuario.getPersona() == null && usuario.getPerId() == null)
        throw new AccessDeniedException(
            "El usuario no tiene perfil de persona vinculado");

    boolean ok = false;

    // Titulares: comparar por perId de Londra (mismo sistema en usuario y titular)
    if (!ok && usuario.getPerId() != null && aj.getTitularJornada() != null)
        ok = usuario.getPerId().equals(
            aj.getTitularJornada().getTitular().getPerId());

    // Internos (y titulares con FK de persona local): comparar por id_persona local
    if (!ok && usuario.getPersona() != null) {
        Long personaId = usuario.getPersona().getIdPersona();
        if (aj.getInternoJornada() != null)
            ok = personaId.equals(
                aj.getInternoJornada().getInterno().getPersona().getIdPersona());
        if (!ok && aj.getTitularJornada() != null)
            ok = personaId.equals(
                aj.getTitularJornada().getTitular().getPersona().getIdPersona());
    }

    if (!ok)
        throw new AccessDeniedException(
            "Solo el funcionario asignado puede realizar esta acción");
}

}