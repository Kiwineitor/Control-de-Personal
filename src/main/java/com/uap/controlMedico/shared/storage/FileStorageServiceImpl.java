package com.uap.controlMedico.shared.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageServiceImpl implements FileStorageService {
    
    private final FileStorageProperties props;

    private static final Set<String> EXTENSIONES_PERMITIDAS =
        Set.of("jpg", "jpeg", "png", "pdf", "mp4", "mov", "avi");
    private static final long MAX_BYTES = 150L * 1024 * 1024; // 150 MB

    @Override
    public String guardarEvidencia(MultipartFile file, Long idActividadJornada) throws IOException {
        validarArchivo(file);
        String ext           = extraerExtension(Objects.requireNonNull(file.getOriginalFilename()));
        String nombreArchivo = "ev_" + idActividadJornada + "_" + UUID.randomUUID() + "." + ext;
        Path   dir           = getEvidenciasDir();
        Files.createDirectories(dir);
        Files.copy(file.getInputStream(), dir.resolve(nombreArchivo),
                   StandardCopyOption.REPLACE_EXISTING);
        log.info("Evidencia guardada → {}", dir.resolve(nombreArchivo).toAbsolutePath());
        return "evidencias/" + nombreArchivo;
    }

    @Override
    public void eliminarArchivo(String rutaRelativa) {
        if (rutaRelativa == null || rutaRelativa.isBlank()) return;
        try {
            Files.deleteIfExists(Paths.get(props.getPath()).resolve(rutaRelativa).normalize());
        } catch (IOException e) {
            log.warn("No se pudo eliminar: {}", rutaRelativa, e);
        }
    }

    @Override
    public Path getEvidenciasDir() {
        return Paths.get(props.getPath(), "evidencias");
    }

    @Override
    public Path resolverRutaAbsoluta(String rutaRelativa) {
        return Paths.get(props.getPath()).resolve(rutaRelativa).normalize();
    }

    @Override
    public String generarDownloadUrl(String rutaRelativa) {
        if (rutaRelativa == null || rutaRelativa.isBlank()) return null;
        return props.getBaseUrl() + "/" + rutaRelativa;
    }

    // ── privados ─────────────────────────────────────────────────────

    private void validarArchivo(MultipartFile file) {
        if (file.isEmpty())
            throw new IllegalArgumentException("El archivo está vacío");
        if (file.getSize() > MAX_BYTES)
            throw new IllegalArgumentException("El archivo supera el límite de 150 MB");
        String ext = extraerExtension(Objects.requireNonNull(file.getOriginalFilename()));
        if (!EXTENSIONES_PERMITIDAS.contains(ext))
            throw new IllegalArgumentException(
                "Tipo no permitido: ." + ext + " — Permitidos: jpg, jpeg, png, pdf, mp4, mov");
    }

    private String extraerExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0) throw new IllegalArgumentException("El archivo no tiene extensión");
        return filename.substring(dot + 1).toLowerCase();
    }

    @Override
    public String guardarJustificante(MultipartFile file, Long idSolicitud) throws IOException {
        validarArchivo(file);
        String ext           = extraerExtension(Objects.requireNonNull(file.getOriginalFilename()));
        String nombreArchivo = "jus_" + idSolicitud + "_" + UUID.randomUUID() + "." + ext;
        Path   dir           = Paths.get(props.getPath(), "justificantes");
        Files.createDirectories(dir);
        Files.copy(file.getInputStream(), dir.resolve(nombreArchivo),
                StandardCopyOption.REPLACE_EXISTING);
        log.info("Justificante guardado → {}", dir.resolve(nombreArchivo).toAbsolutePath());
        return "justificantes/" + nombreArchivo;
    }

    @Override
    public String guardarBitacora(MultipartFile file, Long idBitacora) throws IOException {
        validarArchivo(file);
        String ext           = extraerExtension(Objects.requireNonNull(file.getOriginalFilename()));
        String nombreArchivo = "bit_" + idBitacora + "_" + UUID.randomUUID() + "." + ext;
        Path   dir           = Paths.get(props.getPath(), "bitacoras");
        Files.createDirectories(dir);
        Files.copy(file.getInputStream(), dir.resolve(nombreArchivo),
                StandardCopyOption.REPLACE_EXISTING);
        log.info("Evidencia bitácora guardada → {}", dir.resolve(nombreArchivo).toAbsolutePath());
        return "bitacoras/" + nombreArchivo;
    }
}
