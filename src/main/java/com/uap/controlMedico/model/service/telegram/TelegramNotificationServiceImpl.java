package com.uap.controlMedico.model.service.telegram;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Service
@Slf4j
public class TelegramNotificationServiceImpl implements TelegramNotificationService {

    private static final String  PLACEHOLDER        = "GENERATE_ME";
    private static final Duration CONNECT_TIMEOUT    = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT    = Duration.ofSeconds(5);

    @Value("${sisu.telegram.enabled:true}")
    private boolean enabledFlag;

    @Value("${sisu.telegram.bot-token:GENERATE_ME}")
    private String botToken;

    private final ObjectMapper objectMapper;
    private final TelegramConfiguracionService configuracionService;

    // HttpClient es thread-safe e inmutable: se crea una sola vez con timeout de conexión.
    private final HttpClient httpClient;

    private String  sendMessageUrl;
    private boolean botConfigurado = false;

    @Autowired
    public TelegramNotificationServiceImpl(ObjectMapper objectMapper, TelegramConfiguracionService configuracionService) {
        this(objectMapper, HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(), configuracionService);
    }

    TelegramNotificationServiceImpl(ObjectMapper objectMapper, HttpClient httpClient,
                                    TelegramConfiguracionService configuracionService) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.configuracionService = configuracionService;
    }

    @PostConstruct
    void init() {
        botConfigurado = botToken != null && !botToken.isBlank() && !PLACEHOLDER.equals(botToken);
        if (botConfigurado) sendMessageUrl = "https://api.telegram.org/bot" + botToken + "/sendMessage";
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registrarEstadoInicial() {
        // La tabla ya existe: Hibernate terminó de aplicar ddl-auto antes de este evento.
        TelegramEstado estado = getEstado();
        log.info("Telegram notifications {} (chat-id={}, origen={}).",
            estado.habilitado() ? "habilitadas" : "deshabilitadas", estado.chatId(), estado.origen());
    }

    @Override
    public boolean isEnabled() {
        return getEstado().habilitado();
    }

    @Override
    public String getChatId() {
        return configuracionService.obtenerDestino().chatId();
    }

    @Override
    public TelegramEstado getEstado() {
        var destino = configuracionService.obtenerDestino();
        return new TelegramEstado(enabledFlag && botConfigurado && destino.chatId() != null,
            destino.chatId(), destino.origen(), botConfigurado, enabledFlag);
    }

    @Override
    public TelegramEstado guardarChatId(String chatId) {
        configuracionService.guardarChatId(chatId);
        return getEstado();
    }

    /**
     * Envía el mensaje en un thread aparte (pool "telegramExecutor"): si la red hacia
     * api.telegram.org está bloqueada o caída, el timeout de conexión/lectura la corta
     * en pocos segundos y nunca bloquea el thread que llamó (request HTTP o @Scheduled).
     * Silencioso: nunca propaga excepción al llamador.
     */
    @Override
    @Async("telegramExecutor")
    public void enviarMensaje(String texto) {
        TelegramEnvioResultado resultado = enviarConConfirmacion(texto, true);
        if (!"CONFIRMADO".equals(resultado.estado())) {
            log.warn("Telegram: {} — {}", resultado.estado(), resultado.detalle());
        }
    }

    @Override
    public TelegramEnvioResultado enviarConConfirmacion(String texto, boolean markdown) {
        TelegramEstado configuracion = getEstado();
        // Un solo destino por solicitud, incluso si otro administrador lo cambia durante el envío.
        String chatId = configuracion.chatId();
        if (!configuracion.habilitado()) return resultado(chatId, "DESHABILITADO", "Telegram no está configurado o está deshabilitado.");
        if (texto == null || texto.isBlank() || texto.length() > 4096) {
            return resultado(chatId, "FALLIDO", "El mensaje debe tener entre 1 y 4096 caracteres.");
        }
        try {
            Map<String, String> payload = new HashMap<>();
            payload.put("chat_id", chatId);
            payload.put("text", texto);
            if (markdown) payload.put("parse_mode", "Markdown");
            String json = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(sendMessageUrl))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            var body = objectMapper.readTree(response.body());
            if (response.statusCode() >= 200 && response.statusCode() < 300
                    && body.path("ok").asBoolean(false)
                    && body.path("result").path("message_id").isNumber()
                    && body.path("result").path("chat").path("id").isNumber()) {
                var mensaje = body.path("result");
                return new TelegramEnvioResultado("CONFIRMADO",
                    mensaje.path("chat").path("id").asText(),
                    mensaje.path("chat").path("title").asText(""),
                    mensaje.path("message_id").asLong(),
                    "Telegram aceptó el mensaje. Comprueba su recepción en el grupo.");
            }
            String detalle = body.path("description").asText("Respuesta sin confirmación de entrega.");
            // No registrar cuerpos completos, URLs con token ni mensajes de excepciones de red.
            detalle = detalle.replace(botToken, "[token oculto]");
            if (detalle.length() > 300) detalle = detalle.substring(0, 300);
            String estado = response.statusCode() >= 500 || body.path("ok").asBoolean(false)
                ? "INCIERTO" : "FALLIDO";
            return resultado(chatId, estado, "Telegram HTTP " + response.statusCode() + ": " + detalle);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return resultado(chatId, "INCIERTO", "Envío interrumpido. Comprueba el grupo antes de repetir.");
        } catch (Exception e) {
            return resultado(chatId, "INCIERTO", "No se pudo confirmar la respuesta de Telegram. "
                + "Comprueba la conexión del servidor y el grupo antes de repetir.");
        }
    }

    private TelegramEnvioResultado resultado(String chatId, String estado, String detalle) {
        return new TelegramEnvioResultado(estado, chatId, null, null, detalle);
    }
}
