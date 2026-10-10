package com.uap.controlMedico.model.entity;

import java.time.LocalDate;

import com.uap.controlMedico.config.AuditoriaConfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
    name = "responsable_interno",
    indexes = {
        @Index(name = "idx_resp_interno_titular", columnList = "id_titular"),
        @Index(name = "idx_resp_interno_interno",  columnList = "id_interno")
    }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class ResponsableInterno extends AuditoriaConfig {
        @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idResponsableInterno;
 
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "id_titular",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_resp_interno_titular")
    )
    private Titular titular;
 
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "id_interno",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_resp_interno_interno")
    )
    private Interno interno;
 
    /** Fecha desde la que el titular es responsable de este interno. */
    @Column(name = "fecha_asignacion", nullable = false)
    private LocalDate fechaAsignacion;
 
    /** Fecha hasta la que es válida la asignación (puede ser null si es indefinida). */
    @Column(name = "fecha_fin_asignacion")
    private LocalDate fechaFinAsignacion;
 
    /**
     * Si la relación está activa actualmente.
     * Permite mantener el historial de supervisiones anteriores.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
 
    @Column(length = 255)
    private String observaciones;

}
