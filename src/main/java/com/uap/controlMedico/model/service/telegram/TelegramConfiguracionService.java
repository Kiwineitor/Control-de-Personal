package com.uap.controlMedico.model.service.telegram;

public interface TelegramConfiguracionService {
    record Destino(String chatId, String origen) {}

    Destino obtenerDestino();

    void guardarChatId(String chatId);
}
