package com.uap.controlMedico.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtil {
    
    @Value("${sisu.jwt.secret}")
    private String secret;
 
    @Value("${sisu.jwt.expiration:86400000}")
    private long expiration;
 
    // ── Generación ─────────────────────────────────────────────────────
 
    /** Claim con el id de la sesión única. Ver {@link #sesionCoincide}. */
    public static final String CLAIM_SID = "sid";

    /**
     * @param sesionId UUID de la sesión que se está abriendo. Queda grabado en el
     *                 token y debe coincidir con {@code Usuario.sesionActual} para
     *                 que el token siga siendo aceptado: así, al loguearse en otro
     *                 dispositivo, este token queda invalidado.
     */
    public String generarToken(UserDetails userDetails, String sesionId) {
        Map<String, Object> claims = new HashMap<>();

        // ["ADMIN", "SUPERVISOR", ...]
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
        claims.put("roles", roles);
        claims.put(CLAIM_SID, sesionId);

        return crearToken(claims, userDetails.getUsername());
    }

    /** Id de sesión grabado en el token, o null si fue emitido antes de esta función. */
    public String extraerSid(String token) {
        try {
            return extraerClaim(token, claims -> claims.get(CLAIM_SID, String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * ¿El token pertenece a la sesión vigente del usuario? (sesión única)
     *
     * @param sesionActualEnBd valor de {@code Usuario.sesionActual}; null si el
     *        usuario todavía no ha iniciado sesión desde que existe esta función.
     *
     * Con {@code sesionActualEnBd == null} se acepta cualquier token válido: es la
     * migración suave que evita cerrar la sesión de todo el personal al desplegar.
     * En cuanto el usuario inicia sesión una vez, el campo deja de ser null y a
     * partir de ahí sólo vale el token de su último login.
     */
    public boolean sesionCoincide(String token, String sesionActualEnBd) {
        if (sesionActualEnBd == null) return true;
        return sesionActualEnBd.equals(extraerSid(token));
    }
 
    private String crearToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSecretKey())
                .compact();
    }
 
    // ── Validación ─────────────────────────────────────────────────────
 
    public boolean esValido(String token, UserDetails userDetails) {
        final String username = extraerUsername(token);
        return username.equals(userDetails.getUsername()) && !estaExpirado(token);
    }
 
    public boolean esValido(String token) {
        try {
            parsearToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
 
    // ── Extracción de datos ────────────────────────────────────────────
 
    public String extraerUsername(String token) {
        return extraerClaim(token, Claims::getSubject);
    }
 
    public Date extraerExpiracion(String token) {
        return extraerClaim(token, Claims::getExpiration);
    }

    @SuppressWarnings("unchecked")
    public List<String> extraerRoles(String token) {
        Object raw = extraerClaim(token, claims -> claims.get("roles"));
        if (raw instanceof List<?> list) {
            return list.stream()
                    .filter(o -> o instanceof String)
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }
        return List.of();
    }
 
    public <T> T extraerClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(parsearToken(token));
    }
 
    // ── Helpers privados ───────────────────────────────────────────────
 
    private boolean estaExpirado(String token) {
        return extraerExpiracion(token).before(new Date());
    }
 
    private Claims parsearToken(String token) {
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
 
    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

}
