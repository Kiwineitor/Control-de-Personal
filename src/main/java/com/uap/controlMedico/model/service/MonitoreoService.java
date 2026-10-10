package com.uap.controlMedico.model.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.uap.controlMedico.model.dto.monitoreo.CodigoQrDto;
import com.uap.controlMedico.model.dto.monitoreo.ConfirmarPresenciaRequest;
import com.uap.controlMedico.model.dto.monitoreo.EscanearQrRequest;
import com.uap.controlMedico.model.dto.monitoreo.EstadisticasMonitoreoDto;
import com.uap.controlMedico.model.dto.monitoreo.RegistrarMovimientoRequest;
import com.uap.controlMedico.model.dto.monitoreo.RegistroAsistenciaDto;
import com.uap.controlMedico.model.dto.monitoreo.ResumenRondaDto;
import com.uap.controlMedico.model.dto.monitoreo.RondaVerificacionDto;
import com.uap.controlMedico.model.dto.monitoreo.RondaVerificacionRequest;
import com.uap.controlMedico.model.dto.monitoreo.VerificacionPresenciaDto;
import com.uap.controlMedico.model.enums.EstadoRonda;

public interface MonitoreoService {
    // ── Códigos QR ────────────────────────────────────────────────────────
    CodigoQrDto crearQrParaLugar(Long idLugar);
    CodigoQrDto regenerarQr(Long idLugar);
    CodigoQrDto toggleQr(Long idLugar);
    CodigoQrDto findQrByLugar(Long idLugar);
    List<CodigoQrDto> listarQrs();

    // ── Rondas ────────────────────────────────────────────────────────────
    Page<RondaVerificacionDto> listarRondas(String q, EstadoRonda estado,
                                             LocalDate fecha, Pageable pageable);
    RondaVerificacionDto findRondaById(Long id);
    RondaVerificacionDto crearRonda(RondaVerificacionRequest request);
    RondaVerificacionDto iniciarRonda(Long id);
    RondaVerificacionDto cerrarRonda(Long id);
    RondaVerificacionDto cancelarRonda(Long id);

    // ── Verificaciones ────────────────────────────────────────────────────
    List<VerificacionPresenciaDto> findVerificacionesByRonda(Long idRonda);
    VerificacionPresenciaDto confirmarPresencia(Long idVerificacion,
                                                ConfirmarPresenciaRequest request);
    VerificacionPresenciaDto marcarAusente(Long idVerificacion, String observaciones);
    VerificacionPresenciaDto escanearQr(EscanearQrRequest request);

    // ── Asistencia ────────────────────────────────────────────────────────
    List<RegistroAsistenciaDto> findAsistenciaByJornada(Long idJornada);
    RegistroAsistenciaDto registrarEntrada(RegistrarMovimientoRequest request);
    RegistroAsistenciaDto registrarSalida(RegistrarMovimientoRequest request);
    void inicializarRegistrosJornada(Long idJornada);

    // ── Reportes ──────────────────────────────────────────────────────────
    ResumenRondaDto resumenRonda(Long idRonda);
    EstadisticasMonitoreoDto estadisticasGlobales();
}
