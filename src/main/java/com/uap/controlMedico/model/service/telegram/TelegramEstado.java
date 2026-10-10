package com.uap.controlMedico.model.service.telegram;

public record TelegramEstado(boolean habilitado, String chatId, String origen,
                             boolean botConfigurado, boolean activadoEnServidor) {}
