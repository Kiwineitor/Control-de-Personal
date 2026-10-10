package com.uap.controlMedico.model.dto.internosDto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.uap.controlMedico.model.dto.titularDto.TitularResumenResponse;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InternoResponse {
    
    private Long idInterno;
 
    // Referencia Londra
    private Long perId;
    private Long contratoId;
 
    // Datos cacheados de Londra
    private String nombre;
    private String apellido;
    private String nombreCompleto;
    private String ci;
    private String email;
    private String telefono;
 
    // Datos universitarios
    private String universidad;
    private String carrera;
    private Integer anoFormacion;
    private String nroMatricula;
 
    // Rotación
    private LocalDate fechaInicioRotacion;
    private LocalDate fechaFinRotacion;
    private boolean enRotacionActual;
 
    // Datos SISU
    private boolean activo;
    private String codigoQr;           // UUID del QR para mostrar/imprimir
    private String imagenQrUrl;        // URL de la imagen QR en el servidor SMB
    private TitularResumenResponse titularResponsable;
    private LocalDateTime fechaCreacion;

}
