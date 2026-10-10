package com.uap.controlMedico.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Singleton de configuración global del módulo de monitoreo GPS.
 * Siempre tiene id = 1. Se inicializa con valores por defecto al arrancar.
 */
@Entity
@Table(name = "configuracion_monitoreo")
@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
public class ConfiguracionMonitoreo {

    @Id
    private Long id;

    /** Minutos sin retornar a la zona antes de registrar abandono. */
    @Column(name = "timeout_abandono_minutos", nullable = false)
    @Builder.Default
    private Integer timeoutAbandonoMinutos = 20;

    /**
     * Minutos sin recibir ubicación antes de registrar ausencia por pérdida de señal.
     *
     * Separado de {@code timeoutAbandonoMinutos} a propósito: no son el mismo hecho.
     * En el abandono se SABE dónde está la persona (fuera de la geocerca) y se le da
     * margen para volver; en la pérdida de señal no se sabe nada, así que esperar lo
     * mismo solo alarga la ceguera. Por eso este umbral es más corto.
     */
    @Column(name = "timeout_sin_senal_minutos", nullable = false,
            columnDefinition = "integer not null default 15")
    @Builder.Default
    private Integer timeoutSinSenalMinutos = 15;

    /** Intervalo de envío GPS (segundos) cuando la persona está DENTRO de la zona. */
    @Column(name = "intervalo_gps_activo_seg", nullable = false)
    @Builder.Default
    private Integer intervaloGpsActivoSeg = 120;

    /** Intervalo de envío GPS (segundos) cuando la persona está FUERA de la zona. */
    @Column(name = "intervalo_gps_fuera_zona_seg", nullable = false)
    @Builder.Default
    private Integer intervaloGpsFueraZonaSeg = 30;

    /**
     * Desplazamiento mínimo en metros para considerar que la persona se movió.
     * Si el dispositivo no se movió más de este valor, el cliente no debe enviar.
     */
    @Column(name = "delta_min_movimiento_metros", nullable = false)
    @Builder.Default
    private Integer deltaMinMovimientoMetros = 30;

    /**
     * Metros adicionales de margen antes del radio absoluto del lugar
     * para disparar alerta previa ("estás cerca del límite").
     * 0 = sin alerta previa.
     */
    @Column(name = "radio_alerta_extra_metros", nullable = false)
    @Builder.Default
    private Integer radioAlertaExtraMetros = 0;

    /**
     * Minutos de tolerancia tras la hora de inicio del turno antes de contar ATRASO.
     * Si la primera presencia (primer fix DENTRO) llega después de inicio + este margen,
     * se registra un atraso con su minutaje.
     */
    // columnDefinition con DEFAULT: necesario para que ddl-auto=update pueda AGREGAR
    // la columna a una tabla que YA tiene la fila id=1 (PostgreSQL rechaza un NOT NULL
    // sin default sobre datos existentes → el ALTER falla y la columna no se crea →
    // toda lectura de la config revienta con 500). Mismo patrón que cerradaPorLogout.
    @Column(name = "tolerancia_atraso_min", nullable = false, columnDefinition = "integer not null default 10")
    @Builder.Default
    private Integer toleranciaAtrasoMin = 10;

    /**
     * Minutos tras el inicio del turno sin un solo reporte GPS antes de registrar FALTA
     * (no se presentó). Debe ser >= que el umbral de cierre por inactividad.
     */
    @Column(name = "umbral_falta_min", nullable = false, columnDefinition = "integer not null default 30")
    @Builder.Default
    private Integer umbralFaltaMin = 20;

    /**
     * Piso (en segundos) que un fix FUERA debe sostenerse antes de confirmar la salida
     * de zona (debounce). Evita que un salto aislado de GPS por multipath en interiores
     * dispare un falso "fuera de zona". El valor efectivo es max(este piso, 2× intervaloGpsFueraZonaSeg).
     */
    @Column(name = "confirmacion_fuera_seg", nullable = false, columnDefinition = "integer not null default 60")
    @Builder.Default
    private Integer confirmacionFueraSeg = 60;

    /**
     * Máxima incertidumbre (en metros) que la geocerca "perdona" al decidir si un fix
     * está dentro de la zona. Un fix impreciso cuya nube de error toca la zona se considera
     * DENTRO, pero la indulgencia se acota a este tope para que un fix con precisión absurda
     * (NETWORK/IP, miles de m) NO enmascare una salida real.
     */
    @Column(name = "precision_max_confiable_metros", nullable = false, columnDefinition = "integer not null default 100")
    @Builder.Default
    private Integer precisionMaxConfiableMetros = 100;

    public static final Long ID = 1L;
}
