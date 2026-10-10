package com.uap.controlMedico.model.service.telegram;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uap.controlMedico.exception.BusinessException;
import com.uap.controlMedico.model.dao.ConfiguracionTelegramDao;
import com.uap.controlMedico.model.entity.ConfiguracionTelegram;

@Service
public class TelegramConfiguracionServiceImpl implements TelegramConfiguracionService {
    private final ConfiguracionTelegramDao dao;
    private final String chatIdServidor;

    public TelegramConfiguracionServiceImpl(ConfiguracionTelegramDao dao,
            @Value("${sisu.telegram.chat-id:GENERATE_ME}") String chatIdServidor) {
        this.dao = dao;
        this.chatIdServidor = chatIdServidor;
    }

    @Override
    @Transactional(readOnly = true)
    public Destino obtenerDestino() {
        // Sin caché: los próximos envíos usan el destino persistido, incluso tras un reinicio.
        return dao.findById(ConfiguracionTelegram.ID)
            .map(config -> new Destino(config.getChatId(), "SISTEMA"))
            .orElseGet(() -> new Destino(
                chatIdServidor == null || chatIdServidor.isBlank() || "GENERATE_ME".equals(chatIdServidor)
                    ? null : chatIdServidor.strip(), "SERVIDOR"));
    }

    @Override
    @Transactional
    public void guardarChatId(String chatId) {
        String normalizado = chatId == null ? "" : chatId.strip();
        if (!normalizado.matches("-[1-9][0-9]{0,18}")) {
            throw new BusinessException("Introduce el ID numérico del grupo, incluyendo el signo negativo.");
        }
        try {
            Long.parseLong(normalizado);
        } catch (NumberFormatException e) {
            throw new BusinessException("El ID del grupo está fuera del rango permitido.");
        }
        dao.saveAndFlush(new ConfiguracionTelegram(ConfiguracionTelegram.ID, normalizado));
    }
}
