package com.uap.controlMedico.model.entity;

import java.math.BigDecimal;
import java.time.LocalTime;

import com.uap.controlMedico.config.AuditoriaConfig;
import com.uap.controlMedico.model.enums.EstadoEntrada;
import com.uap.controlMedico.model.enums.EstadoSalida;
import com.uap.controlMedico.model.enums.MetodoRegistro;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
    name = "registro_asistencia",
    indexes = {
        @Index(name = "idx_reg_asist_interno_jornada",  columnList = "id_interno_jornada"),
        @Index(name = "idx_reg_asist_titular_jornada",  columnList = "id_titular_jornada"),
        @Index(name = "idx_reg_asist_estado_entrada",   columnList = "estado_entrada"),
        @Index(name = "idx_reg_asist_fecha",            columnList = "hora_entrada_real")
    }
)
@Getter @Setter @AllArgsConstructor @NoArgsConstructor @SuperBuilder
public class RegistroAsistencia extends AuditoriaConfig{
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idRegistroAsistencia;
 
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "id_interno_jornada",
        nullable = true,
        unique = true,
        foreignKey = @ForeignKey(name = "fk_reg_asist_interno_jornada")
    )
    private InternoJornada internoJornada;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_titular_jornada", nullable = true, unique = true,
                foreignKey = @ForeignKey(name = "fk_reg_asist_titular_jornada"))
    private TitularJornada titularJornada;
 
    // ──────────────────────────────────────────────────────────────────
    //  TIEMPOS ESPERADOS (del cronograma/jornada)
    // ──────────────────────────────────────────────────────────────────
 
    /**
     * Hora de entrada esperada según la Jornada.
     * Se copia desde jornada.horaInicio al crear el registro
     * para tener un punto de comparación independiente.
     */
    @Column(name = "hora_entrada_esperada")
    private LocalTime horaEntradaEsperada;
 
    /** Hora de salida esperada según la Jornada. */
    @Column(name = "hora_salida_esperada")
    private LocalTime horaSalidaEsperada;
 
    // ──────────────────────────────────────────────────────────────────
    //  TIEMPOS REALES (lo que ocurrió)
    // ──────────────────────────────────────────────────────────────────
 
    /** Hora en que el interno marcó su entrada. Null si no se presentó. */
    @Column(name = "hora_entrada_real")
    private LocalTime horaEntradaReal;
 
    /** Hora en que el interno marcó su salida. Null si no registró. */
    @Column(name = "hora_salida_real")
    private LocalTime horaSalidaReal;
 
    // ──────────────────────────────────────────────────────────────────
    //  ESTADOS (calculados al registrar cada evento)
    // ──────────────────────────────────────────────────────────────────
 
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_entrada", length = 25)
    private EstadoEntrada estadoEntrada;
 
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_salida", length = 25)
    private EstadoSalida estadoSalida;
 
    // ──────────────────────────────────────────────────────────────────
    //  MÉTODO DE REGISTRO
    // ──────────────────────────────────────────────────────────────────
 
    /**
     * Cómo se registró la entrada.
     * QR: el supervisor escaneó el código del interno.
     * MANUAL: el administrador ingresó manualmente la hora.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_registro_entrada", length = 20)
    private MetodoRegistro metodoRegistroEntrada;
 
    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_registro_salida", length = 20)
    private MetodoRegistro metodoRegistroSalida;
 
    // ──────────────────────────────────────────────────────────────────
    //  GEOLOCALIZACIÓN (capturada al momento del registro)
    // ──────────────────────────────────────────────────────────────────
 
    /** Latitud GPS en el momento de registrar la entrada. */
    @Column(name = "lat_entrada", precision = 10, scale = 7)
    private BigDecimal latitudEntrada;
 
    /** Longitud GPS en el momento de registrar la entrada. */
    @Column(name = "lng_entrada", precision = 10, scale = 7)
    private BigDecimal longitudEntrada;
 
    @Column(name = "lat_salida", precision = 10, scale = 7)
    private BigDecimal latitudSalida;
 
    @Column(name = "lng_salida", precision = 10, scale = 7)
    private BigDecimal longitudSalida;
 
    // ──────────────────────────────────────────────────────────────────
    //  AUDITORÍA ADICIONAL
    // ──────────────────────────────────────────────────────────────────
 
    /**
     * Usuario que registró la entrada (puede ser el supervisor que escaneó el QR
     * o el admin que lo ingresó manualmente).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_registrado_por_entrada",
                foreignKey = @ForeignKey(name = "fk_reg_asist_usuario_entrada"))
    private Usuario registradoPorEntrada;
 
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_registrado_por_salida",
                foreignKey = @ForeignKey(name = "fk_reg_asist_usuario_salida"))
    private Usuario registradoPorSalida;
 
    @Column(length = 500)
    private String observaciones;
 
    // ──────────────────────────────────────────────────────────────────
    //  MÉTODOS DE DOMINIO
    // ──────────────────────────────────────────────────────────────────
 
    /** True si el interno ya registró su entrada. */
    @Transient
    public boolean tieneEntrada() {
        return horaEntradaReal != null;
    }
 
    /** True si el interno ya registró su salida. */
    @Transient
    public boolean tieneSalida() {
        return horaSalidaReal != null;
    }
 
    /** Minutos de tardanza (0 si llegó a tiempo o antes). */
    @Transient
    public long getMinutosTardanza() {
        if (horaEntradaReal == null || horaEntradaEsperada == null) return 0;
        long minutos = java.time.Duration.between(horaEntradaEsperada, horaEntradaReal).toMinutes();
        return Math.max(0, minutos);
    }

    @Transient
    public boolean esDeInterno()  { return internoJornada  != null; }
    @Transient
    public boolean esDeTitular()  { return titularJornada  != null; }

}
