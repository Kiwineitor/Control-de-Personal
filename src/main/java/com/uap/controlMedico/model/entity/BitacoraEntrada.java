package com.uap.controlMedico.model.entity;

import java.time.LocalDateTime;

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
    name = "bitacora_entrada",
    indexes = {
        @Index(name = "idx_bit_titular_jornada", columnList = "id_titular_jornada"),
        @Index(name = "idx_bit_fecha_evento",    columnList = "fecha_hora_evento")
    }
)
@Getter @Setter @AllArgsConstructor @NoArgsConstructor @SuperBuilder
public class BitacoraEntrada extends AuditoriaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idBitacora;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "id_titular_jornada",
        nullable = true,
        foreignKey = @ForeignKey(name = "fk_bit_titular_jornada")
    )
    private TitularJornada titularJornada;

    @Column(name = "fecha_hora_evento", nullable = false)
    private LocalDateTime fechaHoraEvento;

    @Column(nullable = false, length = 1000)
    private String descripcion;

    @Column(name = "evidencia_url", length = 500)
    private String evidenciaUrl;
}
