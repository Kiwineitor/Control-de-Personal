package com.uap.controlMedico.shared.storage;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    
    /** Guarda evidencia y retorna ruta relativa: "evidencias/ev_1_uuid.jpg" */
    String guardarEvidencia(MultipartFile file, Long idActividadJornada) throws IOException;

    /** Elimina archivo. No lanza excepción si no existe. */
    void eliminarArchivo(String rutaRelativa);

    /** Directorio absoluto donde viven las evidencias */
    Path getEvidenciasDir();

    /** Resuelve ruta absoluta desde ruta relativa */
    Path resolverRutaAbsoluta(String rutaRelativa);

    /** URL de descarga para incluir en respuestas JSON */
    String generarDownloadUrl(String rutaRelativa);

    // Agregar en la interfaz FileStorageService:
    String guardarJustificante(MultipartFile file, Long idSolicitud) throws IOException;

    /** Guarda evidencia de bitácora y retorna ruta relativa: "bitacoras/bit_1_uuid.jpg" */
    String guardarBitacora(MultipartFile file, Long idBitacora) throws IOException;
}
