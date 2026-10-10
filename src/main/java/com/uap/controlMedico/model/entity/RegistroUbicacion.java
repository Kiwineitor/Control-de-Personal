package com.uap.controlMedico.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.uap.controlMedico.model.enums.TipoRegistroUbicacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "registro_ubicacion",
    indexes = {
        @Index(name = "idx_ru_usuario_ts", columnList = "id_usuario, timestamp_registro DESC"),
        @Index(name = "idx_ru_tipo",       columnList = "tipo_registro"),
        @Index(name = "idx_ru_cliente",    columnList = "client_event_id")
    }
)
@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
public class RegistroUbicacion {
    
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idRegistro;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false,
                foreignKey = @ForeignKey(name = "fk_ru_usuario"))
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_jornada",
                foreignKey = @ForeignKey(name = "fk_ru_jornada"))
    private Jornada jornada;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_lugar",
                foreignKey = @ForeignKey(name = "fk_ru_lugar"))
    private Lugar lugar;

    @Column(nullable = false, precision = 11, scale = 8)
    private BigDecimal latitud;

    @Column(nullable = false, precision = 11, scale = 8)
    private BigDecimal longitud;

    @Column(name = "timestamp_registro", nullable = false)
    private LocalDateTime timestampRegistro;

    @Column(name = "dentro_radio", nullable = false)
    private boolean dentroRadio;

    @Column(name = "distancia_metros", precision = 10, scale = 2)
    private BigDecimal distanciaMetros;

    /**
     * Clasifica el motivo del registro.
     * Solo se guarda en BD si es TRANSICION_ZONA, INICIO/FIN_SESION,
     * o si han pasado ≥5 min desde el último SNAPSHOT del usuario (filtrado en servicio).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_registro", length = 25)
    @Builder.Default
    private TipoRegistroUbicacion tipoRegistro = TipoRegistroUbicacion.SNAPSHOT_PERIODICO;

    // ── Señales de contexto (Fase 2). Nullable: filas/fixes sin dato quedan en null. ──

    /** Batería del dispositivo 0–100 al momento del fix. */
    @Column(name = "bateria")
    private Integer bateria;

    /** Conectividad: WIFI | CELULAR | NINGUNA. */
    @Column(name = "conectividad", length = 15)
    private String conectividad;

    /** True si el fix provino de una ubicación simulada (mock) — anti-fraude. */
    @Column(name = "es_mock")
    private Boolean esMock;

    /**
     * ID del fix generado en el cliente (store-and-forward). Permite idempotencia al
     * sincronizar un lote: si ya existe un registro con este id, no se reprocesa.
     */
    @Column(name = "client_event_id", length = 80)
    private String clientEventId;

    @PrePersist
    private void prePersist() {
        if (timestampRegistro == null) timestampRegistro = LocalDateTime.now();
    }
}
