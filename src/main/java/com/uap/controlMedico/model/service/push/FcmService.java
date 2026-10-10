package com.uap.controlMedico.model.service.push;

import java.io.InputStream;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Envío de notificaciones push nativas a dispositivos Android/iOS vía FCM
 * (Firebase Cloud Messaging). A diferencia del Web Push (VAPID), FCM entrega
 * la notificación aunque la app esté cerrada o el teléfono bloqueado: cuando el
 * mensaje incluye un bloque {@code notification}, el sistema operativo lo muestra
 * automáticamente en la bandeja.
 *
 * Requiere el archivo {@code serviceAccountKey.json} en resources (descargado de
 * Firebase Console → Configuración → Cuentas de servicio). Si no está presente,
 * FCM queda deshabilitado sin romper el arranque.
 */
@Service
@Slf4j
public class FcmService {

    private static final String CREDENTIALS = "serviceAccountKey.json";
    private static final String APP_NAME    = "sisu-fcm";
    // Canal de notificación con sonido personalizado. Debe coincidir EXACTAMENTE
    // con el id del canal creado en la app (app.vue → PushNotifications.createChannel)
    // y con el nombre del archivo en res/raw (sin extensión).
    private static final String CANAL_ALERTAS = "sisu_alertas";
    private static final String SONIDO_ALERTA = "notificacion_nutria";
    // Ícono pequeño de la barra de estado. Debe existir como drawable en la app:
    // res/drawable-nodpi/ic_notif.png (logo "cara blanca"). Android usa su silueta.
    private static final String ICONO_NOTIF   = "ic_notif";

    private boolean enabled = false;

    @PostConstruct
    void init() {
        try {
            ClassPathResource res = new ClassPathResource(CREDENTIALS);
            if (!res.exists()) {
                log.warn("FCM deshabilitado: no se encontró {} en resources. "
                    + "Las notificaciones con la app cerrada no funcionarán.", CREDENTIALS);
                return;
            }
            try (InputStream in = res.getInputStream()) {
                FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(in))
                    .build();

                // Evita doble inicialización si el contexto Spring se recarga (devtools).
                boolean yaInicializado = FirebaseApp.getApps().stream()
                    .anyMatch(a -> APP_NAME.equals(a.getName()));
                if (!yaInicializado) {
                    FirebaseApp.initializeApp(options, APP_NAME);
                }
                enabled = true;
                log.info("FCM habilitado (Firebase Admin inicializado).");
            }
        } catch (Exception e) {
            log.error("Error inicializando FCM: {} — push nativo deshabilitado.", e.getMessage());
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Envía un mensaje FCM a un token de dispositivo.
     *
     * @return {@code true} si se entregó (o FCM está inactivo); {@code false} si el
     *         token es inválido/expirado y el llamador debe eliminarlo de la BD.
     */
    public boolean enviar(String token, String titulo, String mensaje,
                          String tipo, String url, boolean urgente) {
        if (!enabled) return true; // FCM inactivo: no eliminar el token

        try {
            Message message = Message.builder()
                .setToken(token)
                // Bloque notification → el SO lo muestra en bandeja con la app cerrada
                .setNotification(Notification.builder()
                    .setTitle(titulo)
                    .setBody(mensaje)
                    .build())
                // data → disponible para la app cuando el usuario toca la notificación
                .putData("tipo",    tipo != null ? tipo : "")
                .putData("url",     url  != null ? url  : "/app/home")
                .putData("urgente", String.valueOf(urgente))
                .setAndroidConfig(AndroidConfig.builder()
                    .setPriority(urgente ? AndroidConfig.Priority.HIGH
                                         : AndroidConfig.Priority.NORMAL)
                    // Canal con sonido personalizado. DEBE existir en la app (se crea
                    // en app.vue iniciarPushNativo con PushNotifications.createChannel).
                    // El sonido se nombra SIN extensión (res/raw/notificacion_nutria.mp3).
                    .setNotification(AndroidNotification.builder()
                        .setChannelId(CANAL_ALERTAS)
                        .setSound(SONIDO_ALERTA)
                        .setIcon(ICONO_NOTIF)
                        .build())
                    .build())
                .build();

            FirebaseMessaging.getInstance(FirebaseApp.getInstance(APP_NAME)).send(message);
            log.info("FCM enviado [{}] a token …{} (urgente={})", tipo,
                token.length() > 12 ? token.substring(token.length() - 12) : token, urgente);
            return true;

        } catch (FirebaseMessagingException e) {
            MessagingErrorCode code = e.getMessagingErrorCode();
            if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                log.info("Token FCM inválido/expirado ({}), se eliminará.", code);
                return false;
            }
            log.warn("Error enviando FCM ({}): {}", code, e.getMessage());
            return true;
        } catch (Exception e) {
            log.warn("Error inesperado enviando FCM: {}", e.getMessage());
            return true;
        }
    }
}
