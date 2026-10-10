package com.uap.controlMedico.model.service;

import org.springframework.web.multipart.MultipartFile;

public interface UtilidadesService {

        public String guardarArchivo(MultipartFile archivo, String subDirectorio);

        public String moverArchivo(String nombreArchivo, String subDirectorioOrigen, String subDirectorioDestino);

        public boolean renombrarDirectorio(String carpetaContenedor, String nombreAntiguo, String nombreNuevo);

        public boolean crearDirectorio(String rutaRelativa);

        public boolean moverDirectorio(String rutaOrigenRelativa, String rutaDestinoRelativa);

        String decrypt(String encryptedText) throws Exception;

        String encrypt(String data) throws Exception;
        
}
