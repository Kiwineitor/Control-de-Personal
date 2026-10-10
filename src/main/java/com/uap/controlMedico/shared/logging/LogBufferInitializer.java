package com.uap.controlMedico.shared.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Adjunta el {@link MemoryLogAppender} al logger raíz de Logback al arrancar, para
 * que el buffer en memoria reciba los logs del backend. Se suma al appender de
 * consola existente (no lo reemplaza). Captura desde que el contexto está listo;
 * los errores en tiempo de ejecución (p. ej. un 500) sí quedan registrados.
 */
@Component
public class LogBufferInitializer {

    @PostConstruct
    public void init() {
        try {
            Logger raiz = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
            if (raiz instanceof ch.qos.logback.classic.Logger logbackRoot) {
                // Evitar doble registro si el bean se inicializa más de una vez.
                if (logbackRoot.getAppender("memoryLogAppender") != null) return;
                MemoryLogAppender appender = new MemoryLogAppender();
                appender.setContext(logbackRoot.getLoggerContext());
                appender.setName("memoryLogAppender");
                appender.start();
                logbackRoot.addAppender(appender);
                LogBuffer.add("BACKEND", "INFO", "LogBufferInitializer", "main",
                    "Consola del sistema activa: capturando logs del backend.", null, null);
            }
        } catch (Exception ignore) {
            // Si el logging no es Logback, simplemente no capturamos backend (no rompe nada).
        }
    }
}
