package com.uap.controlMedico.model.service.telegram;

/** CONFIRMADO significa aceptado por Telegram, no leído por una persona. */
public record TelegramEnvioResultado(
    String estado, String chatId, String chatTitulo, Long messageId, String detalle
) {}
