package com.uap.controlMedico.config;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Component;

import com.uap.controlMedico.model.dao.UsuarioDao;
import com.uap.controlMedico.model.entity.Usuario;
import com.uap.controlMedico.security.SesionUnicaPolicy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor{

    @Value("${sisu.jwt.secret}")
    private String secret;

    private final UsuarioDao         usuarioDao;
    private final SesionUnicaPolicy  sesionUnicaPolicy;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
            message, StompHeaderAccessor.class);

        // Solo procesar el frame CONNECT
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand()))
            return message;

        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer "))
            return message;

        String token = authHeader.substring(7);
        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

            String username = claims.getSubject();

            // Sesión única: si el usuario ya inició sesión en otro dispositivo, este
            // token quedó invalidado. Sin esta comprobación el dispositivo desplazado
            // conservaba el WebSocket abierto y seguía recibiendo alertas y enviando
            // GPS, aunque sus peticiones REST ya salieran 401.
            //
            // findByUsernameWithRoles trae usuario + roles en una sola consulta, y hacen
            // falta ambos: la sesión vigente y los roles, porque la política exime a los
            // perfiles de escritorio. Debe decidir igual que JwtAuthFilter.
            Usuario usuario = usuarioDao.findByUsernameWithRoles(username).orElse(null);
            String sesionEnBd = usuario != null ? usuario.getSesionActual() : null;
            List<String> roles = usuario == null ? List.of()
                : usuario.getUsuariosRol().stream()
                    .filter(ur -> "ACTIVO".equals(ur.getRol().getEstado()))
                    .map(ur -> "ROLE_" + ur.getRol().getNombre())
                    .toList();

            if (!sesionUnicaPolicy.tokenVigente(token, sesionEnBd, roles)) {
                log.warn("WebSocket CONNECT rechazado: sesión desplazada por otro dispositivo (usuario={})",
                    username);
                return message;
            }

            accessor.setUser(new UsernamePasswordAuthenticationToken(
                username, null,
                AuthorityUtils.commaSeparatedStringToAuthorityList("")
            ));
            log.debug("WebSocket autenticado: {}", username);

        } catch (Exception e) {
            log.warn("WebSocket CONNECT con JWT inválido: {}", e.getMessage());
            // Permitir conexión sin principal (no recibirá mensajes de usuario)
        }
        return message;
    }
}
