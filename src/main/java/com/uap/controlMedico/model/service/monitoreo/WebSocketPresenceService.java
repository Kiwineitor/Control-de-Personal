package com.uap.controlMedico.model.service.monitoreo;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * Rastrea usuarios conectados vía STOMP WebSocket usando eventos de aplicación.
 * Notifica a admins en /topic/admin/presencia cada vez que alguien entra o sale.
 */
@Service
@Slf4j
public class WebSocketPresenceService {

    /**
     * Inyección por constructor con @Lazy: este bean se instancia temprano (tiene
     * métodos @EventListener), antes de que SimpMessagingTemplate esté disponible.
     * @Lazy inyecta un proxy y resuelve el template real en el primer uso, evitando
     * que quede null y se pierdan los avisos de presencia en tiempo real.
     */
    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketPresenceService(@Lazy SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /** username → timestamp de conexión */
    private final ConcurrentHashMap<String, LocalDateTime> connectedUsers = new ConcurrentHashMap<>();

    @EventListener
    public void onConnect(SessionConnectEvent event) {
        Principal principal = event.getUser();
        if (principal != null && principal.getName() != null) {
            connectedUsers.put(principal.getName(), LocalDateTime.now());
            log.debug("WS conectado: {}", principal.getName());
            notificarPresencia("CONECTADO", principal.getName());
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = accessor.getUser();
        if (principal != null && principal.getName() != null) {
            String username = principal.getName();
            connectedUsers.remove(username);
            log.debug("WS desconectado: {}", username);

            // NO cerramos la sesión GPS aquí: con GPS en segundo plano la app sigue
            // rastreando aunque el WebSocket se caiga por un blip de red o por estar
            // minimizada. El cierre se maneja por:
            //   1) Logout explícito → AuthRestController llama cerrarSesionActiva.
            //   2) Staleness → el scheduler cierra sesiones sin GPS reciente, y el
            //      panel admin muestra "Sin señal" si no hay señal en pocos minutos.

            notificarPresencia("DESCONECTADO", username);
        }
    }

    private void notificarPresencia(String tipo, String username) {
        try {
            Map<String, String> payload = new HashMap<>();
            payload.put("tipo", tipo);
            payload.put("username", username);
            payload.put("timestamp", LocalDateTime.now().toString());
            messagingTemplate.convertAndSend("/topic/admin/presencia", payload);
        } catch (Exception e) {
            log.warn("No se pudo publicar evento de presencia: {}", e.getMessage());
        }
    }

    public Set<String> getConnectedUsernames() {
        return Collections.unmodifiableSet(connectedUsers.keySet());
    }

    public boolean isConnected(String username) {
        return connectedUsers.containsKey(username);
    }

    public int totalConectados() {
        return connectedUsers.size();
    }
}
