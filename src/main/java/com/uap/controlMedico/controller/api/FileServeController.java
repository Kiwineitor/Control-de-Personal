package com.uap.controlMedico.controller.api;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uap.controlMedico.shared.storage.FileStorageService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileServeController {
    
    private final FileStorageService fileStorageService;

    @GetMapping("/evidencias/{filename:.+}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> servirEvidencia(@PathVariable String filename) {
        // Anti path-traversal
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\"))
            return ResponseEntity.badRequest().build();

        Path dir      = fileStorageService.getEvidenciasDir();
        Path filePath = dir.resolve(filename).normalize();

        if (!filePath.startsWith(dir))
            return ResponseEntity.badRequest().build();

        Resource resource = new FileSystemResource(filePath);
        if (!resource.exists() || !resource.isReadable())
            return ResponseEntity.notFound().build();

        return ResponseEntity.ok()
            .contentType(detectarMediaType(filename))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=\"" + filename + "\"")
            .body(resource);
    }

    private MediaType detectarMediaType(String filename) {
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        return switch (ext) {
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "png"         -> MediaType.IMAGE_PNG;
            case "pdf"         -> MediaType.APPLICATION_PDF;
            case "mp4"         -> MediaType.parseMediaType("video/mp4");
            case "mov"         -> MediaType.parseMediaType("video/quicktime");
            default            -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    @GetMapping("/justificantes/{filename:.+}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> servirJustificante(@PathVariable String filename) {
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\"))
            return ResponseEntity.badRequest().build();

        Path dir      = Paths.get(fileStorageService.resolverRutaAbsoluta("justificantes").toString());
        Path filePath = dir.resolve(filename).normalize();
        if (!filePath.startsWith(dir)) return ResponseEntity.badRequest().build();

        Resource resource = new FileSystemResource(filePath);
        if (!resource.exists() || !resource.isReadable())
            return ResponseEntity.notFound().build();

        return ResponseEntity.ok()
            .contentType(detectarMediaType(filename))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
            .body(resource);
    }

    @GetMapping("/bitacoras/{filename:.+}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> servirBitacora(@PathVariable String filename) {
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\"))
            return ResponseEntity.badRequest().build();

        Path dir      = Paths.get(fileStorageService.resolverRutaAbsoluta("bitacoras").toString());
        Path filePath = dir.resolve(filename).normalize();
        if (!filePath.startsWith(dir)) return ResponseEntity.badRequest().build();

        Resource resource = new FileSystemResource(filePath);
        if (!resource.exists() || !resource.isReadable())
            return ResponseEntity.notFound().build();

        return ResponseEntity.ok()
            .contentType(detectarMediaType(filename))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
            .body(resource);
    }
}
