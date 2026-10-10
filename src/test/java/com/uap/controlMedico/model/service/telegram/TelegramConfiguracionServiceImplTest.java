package com.uap.controlMedico.model.service.telegram;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.uap.controlMedico.exception.BusinessException;
import com.uap.controlMedico.model.dao.ConfiguracionTelegramDao;
import com.uap.controlMedico.model.entity.ConfiguracionTelegram;

class TelegramConfiguracionServiceImplTest {
    @Test
    void existingServerDestinationIsUsedUntilAnOverrideIsSaved() {
        var dao = mock(ConfiguracionTelegramDao.class);
        when(dao.findById(1L)).thenReturn(Optional.empty());
        var service = new TelegramConfiguracionServiceImpl(dao, "-100123");
        assertEquals("-100123", service.obtenerDestino().chatId());
        assertEquals("SERVIDOR", service.obtenerDestino().origen());
        assertNull(new TelegramConfiguracionServiceImpl(dao, "GENERATE_ME").obtenerDestino().chatId());
        verify(dao, never()).saveAndFlush(any());
    }

    @Test
    void persistedDestinationSurvivesServiceRecreationAndOverridesOldEnvironment() {
        var dao = mock(ConfiguracionTelegramDao.class);
        var stored = new AtomicReference<ConfiguracionTelegram>();
        when(dao.findById(1L)).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(dao.saveAndFlush(any())).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return stored.get();
        });
        new TelegramConfiguracionServiceImpl(dao, "-100123").guardarChatId("  -100999  ");
        var restarted = new TelegramConfiguracionServiceImpl(dao, "-100123");
        assertEquals(1L, stored.get().getId());
        assertEquals("-100999", restarted.obtenerDestino().chatId());
        assertEquals("SISTEMA", restarted.obtenerDestino().origen());
    }

    @Test
    void invalidOrPrivateChatIdsAreRejectedWithoutWriting() {
        var dao = mock(ConfiguracionTelegramDao.class);
        var service = new TelegramConfiguracionServiceImpl(dao, "-100123");
        for (String invalid : new String[] {null, "", "123", "0", "-0", "-01", "@grupo", "-1.5", "-9223372036854775809"}) {
            assertThrows(BusinessException.class, () -> service.guardarChatId(invalid));
        }
        verifyNoInteractions(dao);
    }

    @Test
    void failedPersistenceDoesNotReplaceActiveDestination() {
        var dao = mock(ConfiguracionTelegramDao.class);
        when(dao.findById(1L)).thenReturn(Optional.of(new ConfiguracionTelegram(1L, "-100123")));
        when(dao.saveAndFlush(any())).thenThrow(new IllegalStateException("storage unavailable"));
        var service = new TelegramConfiguracionServiceImpl(dao, "-100555");
        assertThrows(IllegalStateException.class, () -> service.guardarChatId("-100999"));
        assertEquals("-100123", service.obtenerDestino().chatId());
    }
}
