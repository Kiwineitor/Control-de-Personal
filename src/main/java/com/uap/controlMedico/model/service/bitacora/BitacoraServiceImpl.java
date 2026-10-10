package com.uap.controlMedico.model.service.bitacora;

import java.io.IOException;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.uap.controlMedico.model.dao.BitacoraEntradaDao;
import com.uap.controlMedico.model.dao.TitularDao;
import com.uap.controlMedico.model.dao.TitularJornadaDao;
import com.uap.controlMedico.model.dao.UsuarioDao;
import com.uap.controlMedico.model.dto.bitacoraDto.BitacoraEntradaResponse;
import com.uap.controlMedico.model.dto.bitacoraDto.CrearBitacoraRequest;
import com.uap.controlMedico.model.entity.BitacoraEntrada;
import com.uap.controlMedico.model.entity.Titular;
import com.uap.controlMedico.model.entity.TitularJornada;
import com.uap.controlMedico.model.entity.Usuario;
import com.uap.controlMedico.shared.storage.FileStorageService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BitacoraServiceImpl implements BitacoraService {

    private final BitacoraEntradaDao bitacoraDao;
    private final TitularDao titularDao;
    private final TitularJornadaDao titularJornadaDao;
    private final UsuarioDao usuarioDao;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional
    public BitacoraEntradaResponse crearEntrada(CrearBitacoraRequest request) {
        Titular titular = findTitularActual();
        TitularJornada tj = findJornadaActiva(titular);

        BitacoraEntrada entrada = BitacoraEntrada.builder()
            .titularJornada(tj)
            .fechaHoraEvento(request.getFechaHoraEvento())
            .descripcion(request.getDescripcion())
            .build();

        entrada = bitacoraDao.save(entrada);
        return toResponse(entrada);
    }

    @Override
    @Transactional
    public BitacoraEntradaResponse subirEvidencia(Long idBitacora, MultipartFile file) throws IOException {
        BitacoraEntrada entrada = bitacoraDao.findById(idBitacora)
            .orElseThrow(() -> new IllegalArgumentException("Entrada de bitácora no encontrada"));

        Titular titular = findTitularActual();
        if (entrada.getTitularJornada() != null
            && !entrada.getTitularJornada().getTitular().getIdTitular().equals(titular.getIdTitular())) {
            throw new SecurityException("No tienes permiso para modificar esta entrada");
        }

        if (entrada.getEvidenciaUrl() != null) {
            fileStorageService.eliminarArchivo(entrada.getEvidenciaUrl());
        }

        String rutaRelativa = fileStorageService.guardarBitacora(file, idBitacora);
        entrada.setEvidenciaUrl(rutaRelativa);
        entrada = bitacoraDao.save(entrada);

        return toResponse(entrada);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BitacoraEntradaResponse> misEntradas(int limit) {
        Titular titular = findTitularActual();
        List<BitacoraEntrada> entradas = bitacoraDao.findRecientesByTitular(
            titular.getIdTitular(), PageRequest.of(0, Math.min(limit, 100)));
        return entradas.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BitacoraEntradaResponse> entradasPorJornada(Long idTitularJornada) {
        List<BitacoraEntrada> entradas = bitacoraDao.findByTitularJornada(idTitularJornada);
        return entradas.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BitacoraEntradaResponse> entradasPorJornadaAdmin(Long idJornada) {
        List<BitacoraEntrada> entradas = bitacoraDao.findByJornadaId(idJornada);
        return entradas.stream().map(this::toResponseAdmin).toList();
    }

    // ── helpers ──────────────────────────────────────────────────────

    private Titular findTitularActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Usuario usuario = usuarioDao.findByUsernameWithPersona(auth.getName())
            .orElseThrow(() -> new IllegalArgumentException(
                "Usuario no encontrado: " + auth.getName()));

        if (usuario.getPersona() == null) {
            throw new IllegalArgumentException("El usuario no tiene perfil personal vinculado");
        }

        return titularDao.findByPerIdAndActivoTrue(usuario.getPersona().getIdPersona())
            .orElseThrow(() -> new IllegalArgumentException(
                "No se encontró perfil de titular para el usuario: " + auth.getName()));
    }

    private TitularJornada findJornadaActiva(Titular titular) {
        List<TitularJornada> jornadas = titularJornadaDao.findActivasByPersona(
            titular.getPersona().getIdPersona());
        return jornadas.isEmpty() ? null : jornadas.get(0);
    }

    private BitacoraEntradaResponse toResponse(BitacoraEntrada b) {
        String nombreJornada = null;
        String nombreLugar   = null;
        if (b.getTitularJornada() != null) {
            TitularJornada tj = b.getTitularJornada();
            if (tj.getJornada() != null) {
                nombreJornada = tj.getJornada().getNombre();
            }
            if (tj.getJornada() != null && tj.getJornada().getLugar() != null) {
                nombreLugar = tj.getJornada().getLugar().getNombre();
            }
        }

        return BitacoraEntradaResponse.builder()
            .idBitacora(b.getIdBitacora())
            .fechaHoraEvento(b.getFechaHoraEvento())
            .descripcion(b.getDescripcion())
            .evidenciaUrl(b.getEvidenciaUrl())
            .evidenciaDownloadUrl(fileStorageService.generarDownloadUrl(b.getEvidenciaUrl()))
            .nombreJornada(nombreJornada)
            .nombreLugar(nombreLugar)
            .createdAt(b.getFechaCreacion())
            .build();
    }

    private BitacoraEntradaResponse toResponseAdmin(BitacoraEntrada b) {
        String nombreJornada  = null;
        String nombreLugar    = null;
        String nombrePersonal = null;
        if (b.getTitularJornada() != null) {
            TitularJornada tj = b.getTitularJornada();
            if (tj.getJornada() != null) {
                nombreJornada = tj.getJornada().getNombre();
            }
            if (tj.getJornada() != null && tj.getJornada().getLugar() != null) {
                nombreLugar = tj.getJornada().getLugar().getNombre();
            }
            if (tj.getTitular() != null && tj.getTitular().getPersona() != null) {
                var p = tj.getTitular().getPersona();
                nombrePersonal = (p.getNombre() != null ? p.getNombre() : "")
                    + " " + (p.getApellido() != null ? p.getApellido() : "");
            }
        }

        return BitacoraEntradaResponse.builder()
            .idBitacora(b.getIdBitacora())
            .fechaHoraEvento(b.getFechaHoraEvento())
            .descripcion(b.getDescripcion())
            .evidenciaUrl(b.getEvidenciaUrl())
            .evidenciaDownloadUrl(fileStorageService.generarDownloadUrl(b.getEvidenciaUrl()))
            .nombreJornada(nombreJornada)
            .nombreLugar(nombreLugar)
            .nombrePersonal(nombrePersonal)
            .createdAt(b.getFechaCreacion())
            .build();
    }
}
