package com.uap.controlMedico.model.service.telegram;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.uap.controlMedico.exception.BusinessException;
import com.uap.controlMedico.model.dao.PlantillaTelegramDao;
import com.uap.controlMedico.model.entity.PlantillaTelegram;
import com.uap.controlMedico.model.enums.TipoMensajeTelegram;

class TelegramPlantillaServiceImplTest {
    private PlantillaTelegramDao dao;
    private TelegramNotificationService telegram;
    private TelegramPlantillaServiceImpl service;
    private final TipoMensajeTelegram tipo = TipoMensajeTelegram.SENAL_PERDIDA;

    @BeforeEach
    void setup() {
        dao = mock(PlantillaTelegramDao.class);
        telegram = mock(TelegramNotificationService.class);
        service = new TelegramPlantillaServiceImpl(dao, telegram);
        when(telegram.enviarConConfirmacion(anyString(), eq(false)))
            .thenReturn(new TelegramEnvioResultado("CONFIRMADO", "-100123", "Grupo", 1L, "Aceptado"));
    }

    @Test
    void defaultsCoverEveryTelegramEventWithoutSeedingOrSending() {
        var listado = service.listar();
        assertEquals(TipoMensajeTelegram.values().length, listado.size());
        assertTrue(listado.stream().allMatch(TelegramPlantillaService.Plantilla::automatico));
        for (var plantilla : listado) {
            assertFalse(service.previsualizar(plantilla.texto(), Map.of("nombre", "Ana")).isBlank());
        }
        verify(dao, never()).saveAndFlush(any());
        verifyNoInteractions(telegram);
    }

    @Test
    void savedTextAndModeSurviveServiceRecreation() {
        var stored = new AtomicReference<PlantillaTelegram>();
        when(dao.findById(tipo)).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(dao.saveAndFlush(any())).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0)); return stored.get();
        });
        service.guardar(tipo, "Aviso para {nombre}: {detalle}", false);
        var recreated = new TelegramPlantillaServiceImpl(dao, telegram);
        assertEquals("Aviso para Ana: Sin conexión", recreated.renderizar(tipo, Map.of("nombre", "Ana", "detalle", "Sin conexión")));
        recreated.enviarAutomatico(tipo, Map.of("nombre", "Ana"));
        verifyNoInteractions(telegram);
    }

    @Test
    void automaticDeliveryUsesEditedTextAndPlainTextTransport() {
        when(dao.findById(tipo)).thenReturn(Optional.of(new PlantillaTelegram(tipo, "Atención: {nombre} - {detalle}", true)));
        service.enviarAutomatico(tipo, Map.of("nombre", "Ana_1", "detalle", "GPS sin señal"));
        verify(telegram).enviarConConfirmacion("Atención: Ana_1 - GPS sin señal", false);
    }

    @Test
    void manualOnlySuppressesAutomaticButAllowsExplicitSend() {
        when(dao.findById(tipo)).thenReturn(Optional.of(new PlantillaTelegram(tipo, "Aviso: {nombre}", false)));
        service.enviarAutomatico(tipo, Map.of("nombre", "Ana"));
        verifyNoInteractions(telegram);
        assertEquals("CONFIRMADO", service.enviarManual(tipo, Map.of("nombre", "Ana"), "Aviso: Ana").estado());
        verify(telegram, times(1)).enviarConConfirmacion("Aviso: Ana", false);
    }

    @Test
    void previewDoesNotSendAndNeverInterpretsUserDataAsVariables() {
        assertEquals("Ana {detalle} $1 \\ | Sin señal", service.previsualizar("{nombre} | {detalle}",
            Map.of("nombre", "Ana {detalle} $1 \\", "detalle", "Sin señal")));
        verifyNoInteractions(telegram, dao);
    }

    @Test
    void invalidTemplatesAndOversizedRenderedMessagesAreRejected() {
        for (String invalid : new String[] {"", "{password}", "Hola {nombre", "Hola nombre}", "x".repeat(3001)}) {
            assertThrows(BusinessException.class, () -> service.guardar(tipo, invalid, false));
        }
        assertThrows(BusinessException.class, () -> service.previsualizar("{nombre}".repeat(5), Map.of("nombre", "a".repeat(1000))));
        assertThrows(BusinessException.class, () -> service.previsualizar("{nombre}", Map.of("nombre", "a".repeat(1001))));
        verifyNoInteractions(telegram, dao);
    }

    @Test
    void concurrentEditCannotSendDifferentTextThanPreview() {
        when(dao.findById(tipo)).thenReturn(Optional.of(new PlantillaTelegram(tipo, "Texto cambiado", true)));
        assertThrows(BusinessException.class, () -> service.enviarManual(tipo, Map.of(), "Texto anterior"));
        verifyNoInteractions(telegram);
    }

    @Test
    void storageFailureDoesNotFallBackToAnUncontrolledAutomaticMessage() {
        when(dao.findById(tipo)).thenThrow(new IllegalStateException("BD no disponible"));
        assertDoesNotThrow(() -> service.enviarAutomatico(tipo, Map.of("nombre", "Ana")));
        verifyNoInteractions(telegram);
    }
}
