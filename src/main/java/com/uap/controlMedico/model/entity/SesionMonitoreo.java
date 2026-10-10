package com.uap.controlMedico.model.entity;

import java.time.LocalDateTime;

import com.uap.controlMedico.model.enums.EstadoZona;

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

/**
 * Representa una sesión de monitoreo activa para un funcionario durante su turno.
 * El scheduler la crea automáticamente al inicio del turno y la cierra al finalizar.
 * Reemplaza el concepto de "ronda manual" por monitoreo continuo y automático.
 */
@Entity
@Table(
    name = "sesion_monitoreo",
    indexes = {
        @Index(name = "idx_sm_titular", columnList = "id_titular"),
        @Index(name = "idx_sm_interno", columnList = "id_interno"),
        @Index(name = "idx_sm_jornada", columnList = "id_jornada"),
        @Index(name = "idx_sm_activa",  columnList = "activa")
    }
)
@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
public class SesionMonitoreo {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idSesion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_titular", foreignKey = @ForeignKey(name = "fk_sm_titular"))
    private Titular titular;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_interno", foreignKey = @ForeignKey(name = "fk_sm_interno"))
    private Interno interno;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_jornada", nullable = false, foreignKey = @ForeignKey(name = "fk_sm_jornada"))
    private Jornada jornada;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_lugar", foreignKey = @ForeignKey(name = "fk_sm_lugar"))
    private Lugar lugar;

    @Column(name = "timestamp_inicio", nullable = false)
    private LocalDateTime timestampInicio;

    @Column(name = "timestamp_fin")
    private LocalDateTime timestampFin;

    @Column(nullable = false)
    @Builder.Default
    private boolean activa = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_zona", length = 15)
    @Builder.Default
    private EstadoZona estadoZona = EstadoZona.DESCONOCIDO;

    @Column(name = "ultima_actualizacion")
    private LocalDateTime ultimaActualizacion;

    /** Última latitud conocida de la persona. */
    @Column(name = "ultima_latitud", precision = 11, scale = 8)
    private java.math.BigDecimal ultimaLatitud;

    /** Última longitud conocida de la persona. */
    @Column(name = "ultima_longitud", precision = 11, scale = 8)
    private java.math.BigDecimal ultimaLongitud;

    /** Distancia al centro del lugar en la última actualización. */
    @Column(name = "ultima_distancia_metros", precision = 10, scale = 2)
    private java.math.BigDecimal ultimaDistanciaMetros;

    /**
     * Última batería reportada (0–100). La usa el scheduler: si la señal se pierde
     * con la batería casi agotada, la ausencia se clasifica como incidencia no
     * punible ("posible batería agotada") en lugar de abandono. Null si nunca llegó.
     */
    @Column(name = "ultima_bateria")
    private Integer ultimaBateria;

    /** Última conectividad reportada: WIFI | CELULAR | NINGUNA. */
    @Column(name = "ultima_conectividad", length = 15)
    private String ultimaConectividad;

    /**
     * Momento de la PRIMERA presencia confirmada (primer fix DENTRO de zona). Null
     * mientras la persona no haya llegado a su puesto. Se usa para calcular el atraso
     * y para distinguir "nunca llegó" (FALTA) de "llegó y se fue" (abandono).
     */
    @Column(name = "timestamp_primera_presencia")
    private LocalDateTime timestampPrimeraPresencia;

    /**
     * El funcionario cerró sesión durante su turno y la sesión se mantiene abierta
     * (periodo de gracia). El scheduler NO le envía avisos al funcionario (ya se fue:
     * privacidad) pero sí registra la ausencia y avisa a los admins. Se limpia a
     * false en cuanto vuelve a llegar un ping de ubicación (re-inició sesión).
     */
    // columnDefinition con DEFAULT: necesario para que ddl-auto=update pueda AGREGAR
    // la columna a una tabla que ya tiene filas (PostgreSQL rechaza un NOT NULL sin
    // default sobre datos existentes; sin esto el ALTER falla y la columna no se crea).
    @Column(name = "cerrada_por_logout", nullable = false, columnDefinition = "boolean not null default false")
    @Builder.Default
    private boolean cerradaPorLogout = false;

    @PrePersist
    private void prePersist() {
        if (timestampInicio == null) timestampInicio = LocalDateTime.now();
    }
}
