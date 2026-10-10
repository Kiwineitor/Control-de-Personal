package com.uap.controlMedico.model.dto.monitoreo;

import lombok.Data;

@Data
public class UbicacionRequest {
    private double latitud;
    private double longitud;
    /**
     * Precisión del fix en metros (radio de incertidumbre, Location.getAccuracy()).
     * Opcional/retrocompatible: si el cliente no la envía (APK viejo) llega null y
     * la geocerca se comporta como antes. Cuando llega, el backend la usa para
     * evitar falsos "fuera de zona" por fixes imprecisos (típico de NETWORK_PROVIDER
     * en interiores): un fix muy impreciso no provoca transición de zona.
     */
    private Double precision;

    // ── Señales de contexto (Fase 2). Todas opcionales/retrocompatibles: un APK
    //    viejo no las envía → llegan null y el comportamiento es el de antes. ──────

    /** Nivel de batería del dispositivo 0–100. Permite distinguir "se le acabó la
     *  batería" (incidencia no punible) de "apagó el GPS adrede". */
    private Integer bateria;

    /** True si el dispositivo está cargando. */
    private Boolean cargando;

    /** Tipo de conexión: "WIFI" | "CELULAR" | "NINGUNA". Permite saber si un hueco
     *  fue por estar offline (no es abandono) en vez de silencio sospechoso. */
    private String conectividad;

    /** True si el fix proviene de una ubicación simulada (mock). Anti-fraude. */
    private Boolean esMock;

    // ── Store-and-forward (Fase 3). Opcionales: solo los usa el envío en lote. ──────

    /**
     * Momento real del fix en el dispositivo (epoch millis). Permite ordenar un lote
     * sincronizado tras recuperar internet. Epoch en vez de fecha-texto para evitar
     * ambigüedad de zona horaria. Null en el envío en tiempo real (se usa "ahora").
     */
    private Long timestampDispositivoMs;

    /**
     * ID único del fix generado en el cliente. Da idempotencia: si un lote se reintenta
     * (la respuesta se perdió pero el servidor ya lo procesó), no se vuelve a contar.
     */
    private String clientEventId;
}
