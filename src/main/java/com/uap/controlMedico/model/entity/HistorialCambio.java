package com.uap.controlMedico.model.entity;

import com.uap.controlMedico.config.AuditoriaConfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
    name = "historial_cambios",
    indexes = {
        @Index(name = "idx_hc_entidad_id", columnList = "entidad, id_entidad"),
        @Index(name = "idx_hc_fecha",      columnList = "fecha_cambio")
    }
)
@Getter @Setter @AllArgsConstructor @NoArgsConstructor @SuperBuilder
public class HistorialCambio extends AuditoriaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idHistorial;

    @Column(name = "entidad", nullable = false, length = 100)
    private String entidad;

    @Column(name = "id_entidad", nullable = false)
    private Long idEntidad;

    @Column(name = "campo", nullable = false, length = 100)
    private String campo;

    @Lob
    @Column(name = "valor_anterior")
    private String valorAnterior;

    @Lob
    @Column(name = "valor_nuevo")
    private String valorNuevo;

    @Column(name = "accion", nullable = false, length = 20)
    private String accion;

    @Column(name = "usuario", nullable = false, length = 100)
    private String usuario;

    @Column(name = "fecha_cambio", nullable = false)
    private java.time.LocalDateTime fechaCambio;
}
