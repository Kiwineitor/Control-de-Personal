package com.uap.controlMedico.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Catálogo de "vistas" (opciones de menú) que se pueden asignar a roles y usuarios.
 * El {@code codigo} es el identificador estable que el frontend usa en meta.authority.
 */
@Entity
@Table(
    name = "vista",
    uniqueConstraints = @UniqueConstraint(name = "uk_vista_codigo", columnNames = "codigo"),
    indexes = @Index(name = "idx_vista_codigo", columnList = "codigo")
)
@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
public class Vista {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idVista;

    /** Código estable (ej. "MONITOREO"). Coincide con meta.authority del frontend. */
    @Column(nullable = false, length = 50)
    private String codigo;

    /** Nombre visible para el admin al asignar. */
    @Column(nullable = false, length = 100)
    private String nombre;

    /** Grupo/sección del menú (ej. "Operaciones"). */
    @Column(length = 60)
    private String grupo;

    /** Orden de despliegue en la UI de asignación. */
    @Column(name = "orden")
    @Builder.Default
    private Integer orden = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
}
