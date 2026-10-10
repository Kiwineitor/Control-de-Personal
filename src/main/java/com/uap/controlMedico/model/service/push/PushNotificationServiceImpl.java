package com.uap.controlMedico.model.service.push;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// import com.fasterxml.jackson.databind.ObjectMapper;
import com.uap.controlMedico.model.dao.PushSubscripcionDao;
import com.uap.controlMedico.model.dao.UsuarioDao;
import com.uap.controlMedico.model.dto.push.PushSubscripcionRequest;
import com.uap.controlMedico.model.entity.PushSubscripcion;
import com.uap.controlMedico.model.entity.Usuario;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Utils;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class PushNotificationServiceImpl implements PushNotificationService {

    private static final String PLACEHOLDER = "GENERATE_ME";

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Value("${vapid.public-key:GENERATE_ME}")
    private String vapidPublicKey;

    @Value("${vapid.private-key:GENERATE_ME}")
    private String vapidPrivateKey;

    @Value("${vapid.subject:mailto:admin@hospital.uap.edu}")
    private String vapidSubject;

    private final PushSubscripcionDao subscripcionDao;
    private final UsuarioDao          usuarioDao;
    private final ObjectMapper        objectMapper;
    private final FcmService          fcmService;

    private PushService pushService;
    private boolean     enabled = false;

    @PostConstruct
    void init() {
        if (PLACEHOLDER.equals(vapidPublicKey) || PLACEHOLDER.equals(vapidPrivateKey)) {
            logGeneratedKeys();
            return;
        }
        try {
            pushService = new PushService(vapidPublicKey, vapidPrivateKey, vapidSubject);
            enabled = true;
            log.info("Push notifications habilitadas (VAPID configurado).");
        } catch (GeneralSecurityException e) {
            log.error("Error inicializando PushService: {} — push deshabilitado.", e.getMessage());
        }
    }

    /** Genera un par VAPID, los loguea y le indica al admin cómo configurarlos. */
    private void logGeneratedKeys() {
        try {
            VapidKeyPair keys = doGenerateKeys();
            log.warn("");
            log.warn("╔═══════════════════════════════════════════════════════════════╗");
            log.warn("║  PUSH NOTIFICATIONS: VAPID KEYS NO CONFIGURADAS               ║");
            log.warn("║  Copia los valores en application.properties y reinicia.      ║");
            log.warn("╠═══════════════════════════════════════════════════════════════╣");
            log.warn("║  vapid.public-key  = {}", keys.publicKey());
            log.warn("║  vapid.private-key = {}", keys.privateKey());
            log.warn("╚═══════════════════════════════════════════════════════════════╝");
            log.warn("");
        } catch (Exception e) {
            log.error("No se pudieron generar claves VAPID: {}", e.getMessage());
        }
    }

    // ── API ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void guardarSubscripcion(String username, PushSubscripcionRequest req) {
        Usuario usuario = usuarioDao.findByUsername(username)
            .orElseThrow(() -> new EntityNotFoundException("Usuario: " + username));

        // Determinar canal: explícito por platform, o inferido (FCM = sin keys).
        boolean sinKeys = isBlank(req.getP256dh()) || isBlank(req.getAuth());
        String platform = !isBlank(req.getPlatform())
            ? req.getPlatform().toUpperCase()
            : (sinKeys ? "FCM" : "WEBPUSH");

        // Upsert atómico por endpoint (ver PushSubscripcionDao.upsertSubscripcion):
        // el dispositivo queda reasignado al usuario que acaba de iniciar sesión, y
        // los register() concurrentes de la app dejan de chocar contra el índice único.
        subscripcionDao.upsertSubscripcion(
            usuario.getIdUsuario(),
            platform,
            req.getEndpoint(),
            // Las columnas son NOT NULL: en FCM guardamos cadena vacía.
            req.getP256dh() != null ? req.getP256dh() : "",
            req.getAuth()   != null ? req.getAuth()   : "",
            truncar(req.getUserAgent(), 300)
        );
        log.debug("Push subscripción guardada para usuario={} (platform={})", username, platform);
    }

    /** Recorta al ancho de la columna: el insert nativo no pasa por validación de JPA. */
    private static String truncar(String valor, int max) {
        if (valor == null) return null;
        return valor.length() <= max ? valor : valor.substring(0, max);
    }

    @Override
    @Transactional
    public void eliminarSubscripcion(String username, String endpoint) {
        subscripcionDao.findByEndpoint(endpoint).ifPresent(sub -> {
            if (sub.getUsuario().getUsername().equals(username)) {
                subscripcionDao.delete(sub);
                log.debug("Push subscripción eliminada para usuario={}", username);
            }
        });
    }

    @Override
    public void enviarAUsuario(String username,
                               String titulo, String mensaje,
                               String tipo, String url, boolean urgente) {
        // Si ni Web Push (VAPID) ni FCM están activos, no hay nada que hacer.
        if (!enabled && !fcmService.isEnabled()) return;

        List<PushSubscripcion> subs = subscripcionDao.findByUsuario_Username(username);
        if (subs.isEmpty()) {
            log.info("Push omitido: usuario={} sin suscripciones registradas "
                + "(token FCM no registrado → no llegará push fuera de la app)", username);
            return;
        }

        // Payload Web Push (solo se serializa si hay alguna suscripción de navegador).
        String payload = null;

        for (PushSubscripcion sub : subs) {
            if (esFcm(sub)) {
                boolean entregado = fcmService.enviar(
                    sub.getEndpoint(), titulo, mensaje, tipo, url, urgente);
                if (!entregado) {
                    subscripcionDao.delete(sub);
                }
            } else {
                if (!enabled) continue; // suscripción Web Push pero VAPID no configurado
                if (payload == null) {
                    try {
                        payload = objectMapper.writeValueAsString(Map.of(
                            "titulo",  titulo,
                            "mensaje", mensaje,
                            "tipo",    tipo,
                            "url",     url != null ? url : "/app/home",
                            "urgente", urgente
                        ));
                    } catch (Exception e) {
                        log.warn("Error serializando push payload: {}", e.getMessage());
                        return;
                    }
                }
                enviarAlEndpoint(sub, payload);
            }
        }
    }

    /** Una suscripción es FCM si su platform lo indica o si no tiene keys de cifrado. */
    private boolean esFcm(PushSubscripcion sub) {
        return "FCM".equalsIgnoreCase(sub.getPlatform())
            || isBlank(sub.getP256dh()) || isBlank(sub.getAuthKey());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @Override
    public String getPublicKey() {
        return enabled ? vapidPublicKey : null;
    }

    @Override
    public boolean isEnabled() {
        // Habilitado si cualquiera de los dos canales está disponible.
        return enabled || fcmService.isEnabled();
    }

    // ── Envío al endpoint ────────────────────────────────────────────────────

    private void enviarAlEndpoint(PushSubscripcion sub, String payload) {
        try {
            // Notification acepta p256dh y auth como strings base64url directamente
            Notification notification = new Notification(
                sub.getEndpoint(),
                sub.getP256dh(),
                sub.getAuthKey(),
                payload.getBytes(StandardCharsets.UTF_8)
            );

            org.apache.http.HttpResponse response = pushService.send(notification);
            int status = response.getStatusLine().getStatusCode();

            if (status == HttpStatus.GONE.value() || status == HttpStatus.NOT_FOUND.value()) {
                subscripcionDao.delete(sub);
                log.info("Push subscripción expirada eliminada: {}…",
                    sub.getEndpoint().substring(0, Math.min(50, sub.getEndpoint().length())));
            } else if (status >= 400) {
                log.warn("Push delivery falló (status={}): {}…", status,
                    sub.getEndpoint().substring(0, Math.min(50, sub.getEndpoint().length())));
            }
        } catch (Exception e) {
            log.warn("Error enviando push: {}", e.getMessage());
        }
    }

    // ── Generación de claves VAPID ────────────────────────────────────────────

    public record VapidKeyPair(String publicKey, String privateKey) {}

    /**
     * Genera un par de claves VAPID (EC P-256) y las devuelve en base64url.
     * El endpoint admin llama a este método para mostrarlas al administrador.
     */
    public VapidKeyPair generateKeys() {
        try {
            return doGenerateKeys();
        } catch (Exception e) {
            throw new RuntimeException("Error generando VAPID keys", e);
        }
    }

    private static VapidKeyPair doGenerateKeys() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
        keyGen.initialize(new ECGenParameterSpec("prime256v1"));
        KeyPair kp = keyGen.generateKeyPair();

        // Utils.encode() devuelve el punto no comprimido (65 bytes) para la clave pública
        // y el valor raw d (32 bytes) para la clave privada
        byte[] pubBytes  = Utils.encode((ECPublicKey) kp.getPublic());
        byte[] privBytes = Utils.encode((ECPrivateKey) kp.getPrivate());

        String pubB64  = Base64.getUrlEncoder().withoutPadding().encodeToString(pubBytes);
        String privB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(privBytes);

        return new VapidKeyPair(pubB64, privB64);
    }
}
