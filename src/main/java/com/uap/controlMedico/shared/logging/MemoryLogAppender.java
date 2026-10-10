package com.uap.controlMedico.shared.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;

/**
 * Appender de Logback que vuelca cada evento del backend al {@link LogBuffer} en
 * memoria. Se adjunta al logger raíz en {@link LogBufferInitializer} (no reemplaza
 * el appender de consola; se suma a él).
 */
public class MemoryLogAppender extends AppenderBase<ILoggingEvent> {

    private static final int MAX_STACK_LINES = 20;

    @Override
    protected void append(ILoggingEvent e) {
        try {
            String detalle = formatThrowable(e.getThrowableProxy());
            LogBuffer.add(
                "BACKEND",
                e.getLevel() != null ? e.getLevel().toString() : "INFO",
                e.getLoggerName(),
                e.getThreadName(),
                e.getFormattedMessage(),
                detalle,
                null);
        } catch (Exception ignore) {
            // Nunca dejar que el logging rompa la app.
        }
    }

    private static String formatThrowable(IThrowableProxy tp) {
        if (tp == null) return null;
        StringBuilder sb = new StringBuilder();
        sb.append(tp.getClassName());
        if (tp.getMessage() != null) sb.append(": ").append(tp.getMessage());
        StackTraceElementProxy[] arr = tp.getStackTraceElementProxyArray();
        if (arr != null) {
            int n = Math.min(arr.length, MAX_STACK_LINES);
            for (int i = 0; i < n; i++) {
                sb.append("\n    at ").append(arr[i].getSTEAsString());
            }
            if (arr.length > n) sb.append("\n    ... ").append(arr.length - n).append(" más");
        }
        // Causa raíz (un nivel) para no perder el "Caused by".
        IThrowableProxy cause = tp.getCause();
        if (cause != null) {
            sb.append("\nCaused by: ").append(cause.getClassName());
            if (cause.getMessage() != null) sb.append(": ").append(cause.getMessage());
        }
        return sb.toString();
    }
}
