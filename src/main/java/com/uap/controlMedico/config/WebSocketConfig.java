package com.uap.controlMedico.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import lombok.RequiredArgsConstructor;


@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer{
    
    private final JwtChannelInterceptor jwtChannelInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Broker en memoria — reemplazar por RabbitMQ/ActiveMQ en producción
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns(
                "http://localhost:*",       // Vue dev / preview en cualquier puerto
                "https://localhost:*",
                "http://127.0.0.1:*",
                "http://172.16.21.5:*",
                "http://virtual.uap.edu.bo:*",
                "http://192.168.*.*:*",     // cualquier IP/puerto de la red local (build por IP LAN)
                "http://localhost",         // Capacitor Android (androidScheme: http)
                "https://localhost",        // Capacitor Android (androidScheme: https)
                "capacitor://localhost",    // Capacitor nativo algunas versiones
                "null"                      // WebViews Android que envían origen null
            )
            .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtChannelInterceptor);
    }
}
