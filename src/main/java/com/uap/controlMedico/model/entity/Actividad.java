package com.uap.controlMedico.model.entity;

import java.util.ArrayList;
import java.util.List;

import com.uap.controlMedico.config.AuditoriaConfig;
import com.uap.controlMedico.model.enums.EstadoCatalogo;
import com.uap.controlMedico.model.enums.TipoActividad;

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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
    name = "actividad",
    indexes = {
        @Index(name = "idx_actividad_tipo",   columnList = "tipo_actividad"),
        @Index(name = "idx_actividad_estado", columnList = "estado"),
        @Index(name = "idx_actividad_activo", columnList = "activo")
    }
)
@Getter @Setter @AllArgsConstructor @NoArgsConstructor @SuperBuilder
public class Actividad extends AuditoriaConfig{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idActividad;
 
    @NotBlank(message = "El nombre de la actividad es obligatorio")
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String nombre;
 
    @Size(max = 500)
    @Column(length = 500)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_actividad", length = 20, nullable = false)
    @Builder.Default
    private TipoActividad tipoActividad = TipoActividad.CLINICA;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    @Builder.Default
    private EstadoCatalogo estado = EstadoCatalogo.ACTIVO;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
 
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "actividad")
    @Builder.Default
    private List<ActividadJornada> actividadJornadas = new ArrayList<>();
}