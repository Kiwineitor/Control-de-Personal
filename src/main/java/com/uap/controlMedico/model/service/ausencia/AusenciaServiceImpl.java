package com.uap.controlMedico.model.service.ausencia;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uap.controlMedico.model.dao.AusenciaNoJustificadaDao;
import com.uap.controlMedico.model.dao.InternoJornadaDao;
import com.uap.controlMedico.model.dao.RegistroUbicacionDao;
import com.uap.controlMedico.model.dao.SolicitudPermisoDao;
import com.uap.controlMedico.model.dao.TitularDao;
import com.uap.controlMedico.model.dao.TitularJornadaDao;
import com.uap.controlMedico.model.dao.UsuarioDao;
import com.uap.controlMedico.model.entity.AusenciaNoJustificada;
import com.uap.controlMedico.model.entity.InternoJornada;
import com.uap.controlMedico.model.entity.Jornada;
import com.uap.controlMedico.model.entity.RegistroUbicacion;
import com.uap.controlMedico.model.entity.Usuario;
import com.uap.controlMedico.model.enums.EstadoAsignacion;
import com.uap.controlMedico.model.enums.EstadoJornada;
import com.uap.controlMedico.model.enums.EstadoSolicitud;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Registro contable de ausencias ({@link AusenciaNoJustificada}), usado por el
 * reporte {@code GET /api/v1/monitoreo/ausencias/jornada/{id}}.
 *
 * <b>No notifica.</b> Este job nació antes del módulo de monitoreo y colapsaba tres
 * situaciones distintas —nunca reportó, dejó de reportar, y está fuera de la
 * geocerca— en un único booleano, para después anunciarlas todas como
 * "no está en su área asignada" y repetir el aviso cada 15 minutos. Alguien que
 * solo apagó el GPS aparecía en el grupo como si hubiera abandonado su puesto.
 *
 * Quien avisa hoy es {@code MonitoreoSchedulerService}, que distingue FALTA,
 * ATRASO, ABANDONO_ZONA y AUSENCIA_SENAL y manda un mensaje por hecho. Aquí solo
 * se abre y se cierra la fila del reporte.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AusenciaServiceImpl implements AusenciaService {
    
    // Minutos sin GPS para considerar ausente (2x el intervalo de envío)
    private static final int MINUTOS_UMBRAL = 10;

    private final TitularJornadaDao      titularJornadaDao;
    private final InternoJornadaDao      internoJornadaDao;
    private final UsuarioDao             usuarioDao;
    private final TitularDao             titularDao;
    private final RegistroUbicacionDao   registroDao;
    private final AusenciaNoJustificadaDao ausenciaDao;
    private final SolicitudPermisoDao    solicitudDao;

    @Override
    @Transactional
    public void verificarAusencias() {
        LocalDateTime umbral = LocalDateTime.now().minusMinutes(MINUTOS_UMBRAL);
        LocalDate     hoy    = LocalDate.now();
        LocalTime     ahora  = LocalTime.now();

        titularJornadaDao.findAllInActiveJornadas().forEach(tj -> {
            if (!tj.estaEnTurnoAhora(hoy, ahora)) return;
            procesar(tj.getTitular().getPersona().getIdPersona(),
                     tj.getJornada(), hoy, umbral);
        });

        internoJornadaDao.findAllInActiveJornadas().forEach(ij -> {
            if (!estaEnHorario(ij, hoy, ahora)) return;
            procesar(ij.getInterno().getPersona().getIdPersona(),
                     ij.getJornada(), hoy, umbral);
        });
    }

    private boolean estaEnHorario(InternoJornada ij, LocalDate hoy, LocalTime ahora) {
        if (ij.getEstado() == EstadoAsignacion.CANCELADO
                || ij.getEstado() == EstadoAsignacion.COMPLETADO) return false;
        var jornada = ij.getJornada();
        if (jornada == null || !jornada.monitoreoHabilitado()) return false;
        if (hoy.isBefore(jornada.getFechaInicio()) || hoy.isAfter(jornada.getFechaFin())) return false;
        return jornada.estaEnHorarioAhora(ahora);
    }

    // ── Private helpers ───────────────────────────────────────────

    private void procesar(Long perId, Jornada jornada, LocalDate hoy, LocalDateTime umbral) {
        if (perId == null) return;
        Usuario usuario = usuarioDao.findByPerId(perId).orElse(null);
        if (usuario == null) return;

        Optional<RegistroUbicacion> ultima = registroDao
            .findTopByUsuario_IdUsuarioOrderByTimestampRegistroDesc(usuario.getIdUsuario());

        boolean ausente = ultima.isEmpty()
            || ultima.get().getTimestampRegistro().isBefore(umbral)
            || !ultima.get().isDentroRadio();

        Long idJornada = jornada != null ? jornada.getIdJornada() : null;

        if (!ausente) {
            // Resolver ausencia abierta si volvió
            if (idJornada != null) {
                ausenciaDao.findByUsuario_IdUsuarioAndJornada_IdJornadaAndResueltaFalse(
                    usuario.getIdUsuario(), idJornada)
                    .ifPresent(a -> {
                        a.setResuelta(true);
                        a.setFinAusencia(LocalDateTime.now());
                        ausenciaDao.save(a);
                        log.info("Ausencia resuelta: {}", usuario.getUsername());
                    });
            }
            return;
        }

        // Tiene permiso aprobado hoy → no alertar
        if (tienePermiso(usuario, hoy)) return;

        // Crear o actualizar ausencia
        Optional<AusenciaNoJustificada> existente = idJornada != null
            ? ausenciaDao.findByUsuario_IdUsuarioAndJornada_IdJornadaAndResueltaFalse(
                usuario.getIdUsuario(), idJornada)
            : Optional.empty();

        if (existente.isEmpty()) {
            ausenciaDao.save(AusenciaNoJustificada.builder()
                .usuario(usuario).jornada(jornada)
                .inicioAusencia(LocalDateTime.now())
                .registradoPor("SISTEMA")
                .notificacionesEnviadas(0)
                .build());
            log.warn("Ausencia abierta en el reporte: {}", usuario.getUsername());
        } else {
            // Solo contabiliza ciclos: el aviso lo emite MonitoreoSchedulerService,
            // ya clasificado por causa y una única vez.
            AusenciaNoJustificada a = existente.get();
            a.setNotificacionesEnviadas(a.getNotificacionesEnviadas() + 1);
            ausenciaDao.save(a);
        }
    }

    private boolean tienePermiso(Usuario usuario, LocalDate hoy) {
        Long perId = usuario.getPerId();
        if (perId == null) return false;
        return titularDao.findByPersona_IdPersona(perId)
            .map(t -> solicitudDao
                .existsByTitular_IdTitularAndEstadoSolicitudAndFechaPermiso(
                    t.getIdTitular(), EstadoSolicitud.APROBADA, hoy))
            .orElse(false);
    }
}
