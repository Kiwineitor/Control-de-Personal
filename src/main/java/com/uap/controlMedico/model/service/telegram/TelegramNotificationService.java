package com.uap.controlMedico.model.service.telegram;

public interface TelegramNotificationService {

    /**
     * Envía un mensaje de texto (formato Markdown de Telegram) al grupo configurado
     * en SICOPE, con {@code sisu.telegram.chat-id} como valor inicial del servidor.
     * Los fallos del envío HTTP se registran sin propagarse al llamador.
     */
    void enviarMensaje(String texto);

    /** Indica si el canal está configurado y listo para enviar. */
    boolean isEnabled();

    /** Destino configurado, nunca contiene el token del bot. */
    String getChatId();

    TelegramEstado getEstado();

    /** Persiste el destino para todas las notificaciones; no envía un mensaje. */
    TelegramEstado guardarChatId(String chatId);

    /** Espera la respuesta de Telegram, con timeout y sin reintentar envíos inciertos. */
    TelegramEnvioResultado enviarConConfirmacion(String texto, boolean markdown);
}
