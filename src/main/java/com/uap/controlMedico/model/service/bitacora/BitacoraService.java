package com.uap.controlMedico.model.service.bitacora;

import java.io.IOException;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.uap.controlMedico.model.dto.bitacoraDto.BitacoraEntradaResponse;
import com.uap.controlMedico.model.dto.bitacoraDto.CrearBitacoraRequest;

public interface BitacoraService {

    BitacoraEntradaResponse crearEntrada(CrearBitacoraRequest request);

    BitacoraEntradaResponse subirEvidencia(Long idBitacora, MultipartFile file) throws IOException;

    List<BitacoraEntradaResponse> misEntradas(int limit);

    List<BitacoraEntradaResponse> entradasPorJornada(Long idTitularJornada);

    List<BitacoraEntradaResponse> entradasPorJornadaAdmin(Long idJornada);
}
