package com.uap.controlMedico.shared.logging;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Buffer circular en memoria con los últimos logs del sistema (backend y frontend).
 * Lo alimentan: {@link MemoryLogAppender} (logs del backend vía Logback) y el endpoint
 * de logs de cliente (consola del frontend, enviada por la app). Lo consume la vista
 * "Consola del sistema" del admin.
 *
 * No persiste: es un anillo de los últimos {@code MAX} eventos. Suficiente para
 * diagnosticar incidentes en vivo sin acceso a la consola del servidor.
 */
public final class LogBuffer {

    /** Una línea de log. */
    public record Entry(
        long id,
        String timestamp,
        String origin,    // BACKEND | FRONTEND
        String level,     // ERROR | WARN | INFO | DEBUG | TRACE
        String logger,
        String thread,
        String message,
        String detalle,   // stacktrace u otro detalle (puede ser null)
        String usuario    // solo en FRONTEND: quién lo envió
    ) {}

    private static final int MAX = 3000;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final ArrayDeque<Entry> BUF = new ArrayDeque<>();
    private static final AtomicLong SEQ = new AtomicLong(0);

    private LogBuffer() {}

    public static synchronized void add(String origin, String level, String logger,
                                        String thread, String message, String detalle, String usuario) {
        Entry e = new Entry(
            SEQ.incrementAndGet(),
            LocalDateTime.now().format(FMT),
            origin != null ? origin : "BACKEND",
            level   != null ? level   : "INFO",
            logger, thread,
            message != null ? message : "",
            detalle, usuario);
        BUF.addLast(e);
        while (BUF.size() > MAX) BUF.removeFirst();
    }

    /** Copia inmutable de los eventos actuales (orden cronológico ascendente). */
    public static synchronized List<Entry> snapshot() {
        return new ArrayList<>(BUF);
    }

    public static synchronized void clear() {
        BUF.clear();
    }
}
