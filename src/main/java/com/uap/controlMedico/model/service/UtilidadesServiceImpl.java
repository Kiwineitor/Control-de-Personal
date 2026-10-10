package com.uap.controlMedico.model.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UtilidadesServiceImpl implements UtilidadesService {
  String secretKey = "RRHH-UAP98765432"; // La clave debe tener 16, 24 o 32 caracteres para AES-128, AES-192 o
  // AES-256 respectivamente

  private final Logger log = LoggerFactory.getLogger(getClass());

  @Autowired
  private DataSource dataSource;

  @Override
  public String decrypt(String encryptedText) throws Exception {
    Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
    SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "AES");
    cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
    byte[] encryptedBytes = Base64.getDecoder().decode(encryptedText);
    byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
    return new String(decryptedBytes, StandardCharsets.UTF_8);
  }

  @Override
  public String encrypt(String data) throws Exception {
    Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
    SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "AES");
    cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
    byte[] encryptedBytes = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(encryptedBytes);
  }

  @Override
  public String guardarArchivo(MultipartFile archivo, String subDirectorio) {
    // Generamos un nombre único para el archivo
    String uniqueFilename = UUID.randomUUID().toString() + "_" +
        archivo.getOriginalFilename();

    // Construimos la ruta dinámica: uploads/subDirectorio/nombrePersona/
    Path directorioPath = Paths.get("uploads", subDirectorio);
    Path rutaCompleta = directorioPath.resolve(uniqueFilename);
    Path rutaAbsoluta = rutaCompleta.toAbsolutePath();

    System.out.println("LA DIRECCIÓN ES: " + rutaAbsoluta);
    log.info("directorioPath: " + directorioPath);
    log.info("rutaCompleta: " + rutaCompleta);
    log.info("rutaAbsoluta: " + rutaAbsoluta);

    try {
      // Creamos los directorios si no existen
      Files.createDirectories(directorioPath);

      System.out.println("GUARDAR EN EL DIRECTORIO");
      // Copiamos el archivo a la ruta completa
      Files.copy(archivo.getInputStream(), rutaAbsoluta,
          StandardCopyOption.REPLACE_EXISTING);

    } catch (IOException e) {
      System.out.println("ERROR AL GUARDAR EL ARCHIVO: " + e.getMessage());
      e.printStackTrace();
      return null; // Retornamos null para indicar que hubo un error
    }

    // Devolvemos la ruta relativa del archivo (para almacenar en BD o usar en la
    // aplicación)
    // return Paths.get(subDirectorio, uniqueFilename).toString();
    return uniqueFilename;
  }

  @Override
  public String moverArchivo(String nombreArchivo, String subDirectorioOrigen, String subDirectorioDestino) {
    Path origenPath = Paths.get("uploads", subDirectorioOrigen, nombreArchivo);

    // Generar un nuevo nombre único
    String nuevoNombre = UUID.randomUUID().toString() + "_" + nombreArchivo;

    Path destinoDirectorioPath = Paths.get("uploads", subDirectorioDestino);
    Path destinoPath = destinoDirectorioPath.resolve(nuevoNombre);

    try {
      // Crear el directorio destino si no existe
      Files.createDirectories(destinoDirectorioPath);

      // Mover el archivo sin sobrescribir archivos existentes
      Files.move(origenPath, destinoPath);

      System.out.println("Archivo movido exitosamente a: " + destinoPath.toAbsolutePath());
      return nuevoNombre;
    } catch (IOException e) {
      System.err.println("Error al mover el archivo: " + e.getMessage());
      e.printStackTrace();
      return null;
    }
  }

  @Override
  public boolean renombrarDirectorio(String carpetaContenedor, String nombreAntiguo, String nombreNuevo) {
    Path directorioBase = Paths.get("uploads", carpetaContenedor); // ahora incluye la carpeta contenedora
    Path origenPath = directorioBase.resolve(nombreAntiguo);
    Path destinoPath = directorioBase.resolve(nombreNuevo);

    try {
      // Verifica que el directorio de origen exista
      if (!Files.exists(origenPath) || !Files.isDirectory(origenPath)) {
        System.err.println("El directorio de origen no existe o no es un directorio: " + origenPath);
        return false;
      }

      // Verifica que el destino no exista
      if (Files.exists(destinoPath)) {
        System.err.println("Ya existe un directorio con el nuevo nombre: " + destinoPath);
        return false;
      }

      // Renombrar (mover) el directorio
      Files.move(origenPath, destinoPath);
      System.out.println("Directorio renombrado exitosamente de '" + nombreAntiguo + "' a '" + nombreNuevo + "' en '"
          + carpetaContenedor + "'");
      return true;

    } catch (IOException e) {
      System.err.println("Error al renombrar el directorio: " + e.getMessage());
      e.printStackTrace();
      return false;
    }
  }

  @Override
  public boolean crearDirectorio(String rutaRelativa) {
    Path directorioPath = Paths.get("uploads", rutaRelativa);

    try {
      // Crea el directorio (y subdirectorios intermedios si no existen)
      Files.createDirectories(directorioPath);
      System.out.println("Directorio creado en: " + directorioPath.toAbsolutePath());
      return true;
    } catch (IOException e) {
      System.err.println("Error al crear el directorio: " + e.getMessage());
      e.printStackTrace();
      return false;
    }
  }

  @Override
  public boolean moverDirectorio(String rutaOrigenRelativa, String rutaDestinoRelativa) {
    Path directorioBase = Paths.get("uploads");
    Path origenPath = directorioBase.resolve(rutaOrigenRelativa);
    Path destinoPath = directorioBase.resolve(rutaDestinoRelativa);

    try {
      // Validar que el directorio origen exista
      if (!Files.exists(origenPath) || !Files.isDirectory(origenPath)) {
        System.err.println("El directorio de origen no existe o no es válido: " + origenPath);
        return false;
      }

      // Crear el directorio padre de destino si no existe
      Files.createDirectories(destinoPath.getParent());

      // Mover el directorio completo
      Files.move(origenPath, destinoPath, StandardCopyOption.REPLACE_EXISTING);

      System.out.println("Directorio movido exitosamente de '" + origenPath + "' a '" + destinoPath + "'");
      return true;

    } catch (IOException e) {
      System.err.println("Error al mover el directorio: " + e.getMessage());
      e.printStackTrace();
      return false;
    }
  }

}
