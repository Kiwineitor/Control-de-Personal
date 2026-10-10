package com.uap.controlMedico.model.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.uap.controlMedico.config.AuditoriaConfig;
import com.uap.controlMedico.model.enums.TipoLugar;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "lugar")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Lugar extends AuditoriaConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_lugar")
    private Long idLugar;
 
    @NotBlank(message = "El nombre del lugar es obligatorio")
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String nombre;
 
    @Size(max = 300)
    @Column(length = 300)
    private String descripcion;
 
    /**
     * Tipo de área hospitalaria.
     * Permite filtrar y reportar por tipo de lugar.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_lugar", length = 50)
    private TipoLugar tipoLugar;
 
    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    /** Centro del perímetro GPS del lugar (latitud). */
    @Column(name = "latitud_centro", precision = 11, scale = 8)
    private BigDecimal latitudCentro;

    /** Centro del perímetro GPS del lugar (longitud). */
    @Column(name = "longitud_centro", precision = 11, scale = 8)
    private BigDecimal longitudCentro;

    /**
     * Radio en metros del perímetro circular.
     * Si es null → no se valida geocerca (acepta cualquier posición).
     * Default 100 metros es razonable para interiores de hospital.
     */
    @Column(name = "radio_metros")
    private Integer radioMetros;

    @Transient
    public boolean tieneGeofencing() {
        return latitudCentro != null && longitudCentro != null && radioMetros != null;
    }

}
