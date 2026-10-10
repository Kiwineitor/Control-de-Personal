package com.uap.controlMedico.model.service.asistencia;

import java.time.LocalDate;
import java.util.List;

import com.uap.controlMedico.model.dto.asistenciaDto.QrEscaneoRequest;
import com.uap.controlMedico.model.dto.asistenciaDto.RegistroAsistenciaRequest;
import com.uap.controlMedico.model.dto.asistenciaDto.RegistroAsistenciaResponse;

public interface AsistenciaService {
    RegistroAsistenciaResponse registrar(RegistroAsistenciaRequest request);
    RegistroAsistenciaResponse registrarPorQr(QrEscaneoRequest request);
    RegistroAsistenciaResponse buscarPorInternoJornada(Long idInternoJornada);
    List<RegistroAsistenciaResponse> findByFecha(LocalDate fecha);
    List<RegistroAsistenciaResponse> findTardanzas(LocalDate desde, LocalDate hasta);
    List<RegistroAsistenciaResponse> findInasistencias(LocalDate desde, LocalDate hasta);
    List<RegistroAsistenciaResponse> findHistorialDeInterno(Long idInterno, LocalDate desde, LocalDate hasta);
    List<RegistroAsistenciaResponse> findConEntradaSinSalida(LocalDate fecha);

}
