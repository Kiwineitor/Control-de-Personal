package com.uap.controlMedico.config;

import java.io.Serializable;
import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Setter
@Getter
@NoArgsConstructor
@SuperBuilder
public class AuditoriaConfig implements Serializable{
    /**
     * Fecha y hora exacta en que se creó el registro.
     * Se asigna automáticamente al hacer persist().
     * No se puede actualizar después de la creación.
     */
    @CreatedDate
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;
 
    /**
     * Fecha y hora de la última modificación del registro.
     * Se actualiza automáticamente en cada merge().
     */
    @LastModifiedDate
    @Column(name = "fecha_modificacion")
    private LocalDateTime fechaModificacion;
 
    /**
     * Nombre de usuario (username) que creó el registro.
     * Obtenido desde Spring Security → AuditorAware.
     */
    @CreatedBy
    @Column(name = "creado_por", nullable = false, updatable = false, length = 100)
    private String creadoPor;
 
    /**
     * Nombre de usuario que realizó la última modificación.
     */
    @LastModifiedBy
    @Column(name = "modificado_por", length = 100)
    private String modificadoPor;
}