package com.uap.controlMedico.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import com.uap.controlMedico.model.enums.EstadoEvento;
import com.uap.controlMedico.model.enums.EstadoJustificacion;
import com.uap.controlMedico.model.enums.TipoIncidencia;

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
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Registra un evento de salida de zona geográfica durante un turno activo.
 * Se crea automáticamente cuando UbicacionService detecta una transición DENTRO→FUERA.
 * Se resuelve cuando la persona vuelve a la zona o el admin lo cierra manualmente.
 */
@Entity
@Table(
    name = "evento_abandono",
    indexes = {
        @Index(name = "idx_ea_sesion",  columnList = "id_sesion"),
        @Index(name = "idx_ea_resuelto", columnList = "resuelto"),
        @Index(name = "idx_ea_estado",  columnList = "estado")
    }
)
@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
public class EventoAbandono {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idEvento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_sesion", nullable = false, foreignKey = @ForeignKey(name = "fk_ea_sesion"))
    private SesionMonitoreo sesion;

    /** Momento en que se detectó que la persona salió de la zona. */
    @Column(name = "timestamp_salida", nullable = false)
    private LocalDateTime timestampSalida;

    /** Momento en que se confirmó el abandono (timeout superado). Null si aún no confirmado. */
    @Column(name = "timestamp_abandono")
    private LocalDateTime timestampAbandono;

    /** Momento en que la persona regresó a la zona. Null si aún fuera. */
    @Column(name = "timestamp_retorno")
    private LocalDateTime timestampRetorno;

    /** Distancia al centro del lugar en el momento de salir. */
    @Column(name = "distancia_al_salir_metros", precision = 10, scale = 2)
    private BigDecimal distanciaAlSalirMetros;

    @Enumerated(EnumType.STRING)
    @Column(length = 25, nullable = false)
    @Builder.Default
    private EstadoEvento estado = EstadoEvento.FUERA_ZONA;

    @Column(name = "notificado_admin", nullable = false)
    @Builder.Default
    private boolean notificadoAdmin = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean resuelto = false;

    @Column(length = 500)
    private String observaciones;

    // ── Tipo de incidencia ─────────────────────────────────────────────────
    /** Tipo de incidencia. Null en filas antiguas → se interpreta como ABANDONO_ZONA. */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 20)
    @Builder.Default
    private TipoIncidencia tipo = TipoIncidencia.ABANDONO_ZONA;

    // ── Flujo de justificación (espejo de los permisos) ─────────────────────
    /** Estado de justificación. Null en filas antiguas → NO_REQUERIDA. */
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_justificacion", length = 20)
    @Builder.Default
    private EstadoJustificacion estadoJustificacion = EstadoJustificacion.NO_REQUERIDA;

    /** Motivo que el funcionario escribe al solicitar la justificación. */
    @Column(name = "motivo_justificacion", length = 500)
    private String motivoJustificacion;

    /** URL del justificante (documento) que adjunta el funcionario, si lo hay. */
    @Column(name = "justificante_url", length = 500)
    private String justificanteUrl;

    /** Momento en que el funcionario envió la justificación. */
    @Column(name = "fecha_solicitud_justificacion")
    private LocalDateTime fechaSolicitudJustificacion;

    /** Admin/supervisor que revisó la justificación. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_revisado_por", foreignKey = @ForeignKey(name = "fk_ea_revisado_por"))
    private Usuario revisadoPor;

    @Column(name = "fecha_revision")
    private LocalDateTime fechaRevision;

    @Column(name = "observacion_revision", length = 500)
    private String observacionRevision;

    /**
     * Magnitud en minutos de la incidencia cuando aplica (ATRASO: minutos tarde;
     * SALIDA_TEMPRANA: minutos antes). Null para abandonos por zona/señal.
     */
    @Column(name = "minutos_incidencia")
    private Integer minutosIncidencia;

    @PrePersist
    private void prePersist() {
        if (timestampSalida == null) timestampSalida = LocalDateTime.now();
    }

    // ── Helpers de dominio (toleran filas antiguas con null) ─────────────────

    /** Tipo efectivo: filas antiguas (null) se tratan como abandono de zona. */
    @Transient
    public TipoIncidencia getTipoEfectivo() {
        return tipo != null ? tipo : TipoIncidencia.ABANDONO_ZONA;
    }

    /** Estado de justificación efectivo: filas antiguas (null) → NO_REQUERIDA. */
    @Transient
    public EstadoJustificacion getEstadoJustificacionEfectivo() {
        return estadoJustificacion != null ? estadoJustificacion : EstadoJustificacion.NO_REQUERIDA;
    }

    /**
     * True si el funcionario todavía puede enviar una justificación de esta incidencia.
     * Se decide por el estado de justificación (PENDIENTE), no por el tipo de evento, de
     * modo que abandonos, atrasos y faltas son todos justificables por igual.
     */
    @Transient
    public boolean isPuedeJustificar() {
        return getEstadoJustificacionEfectivo() == EstadoJustificacion.PENDIENTE;
    }

    /** Minutos transcurridos desde la salida de zona hasta ahora (o hasta el retorno). */
    @Transient
    public long getTiempoFueraMinutos() {
        LocalDateTime fin = timestampRetorno != null ? timestampRetorno : LocalDateTime.now();
        return ChronoUnit.MINUTES.between(timestampSalida, fin);
    }

    /** Nombre del personal asociado a la sesión. */
    @Transient
    public String getNombrePersonal() {
        if (sesion == null) return "Desconocido";
        if (sesion.getTitular() != null && sesion.getTitular().getPersona() != null) {
            var p = sesion.getTitular().getPersona();
            return p.getNombre() + " " + p.getApellido();
        }
        if (sesion.getInterno() != null && sesion.getInterno().getPersona() != null) {
            var p = sesion.getInterno().getPersona();
            return p.getNombre() + " " + p.getApellido();
        }
        return "Desconocido";
    }
}
