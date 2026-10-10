package com.uap.controlMedico.model.service.telegram;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class TelegramNotificationServiceImplTest {
    private HttpClient client;
    private TelegramNotificationServiceImpl service;
    private TelegramConfiguracionService configuracion;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        client = mock(HttpClient.class);
        configuracion = mock(TelegramConfiguracionService.class);
        when(configuracion.obtenerDestino()).thenReturn(new TelegramConfiguracionService.Destino("-100123", "SERVIDOR"));
        service = new TelegramNotificationServiceImpl(mapper, client, configuracion);
        ReflectionTestUtils.setField(service, "enabledFlag", true);
        ReflectionTestUtils.setField(service, "botToken", "token-de-prueba");
        service.init();
    }

    @SuppressWarnings("unchecked")
    private void response(int status, String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }

    @Test
    void confirmsDestinationAndSendsCustomTextWithoutMarkdown() throws Exception {
        response(200, "{\"ok\":true,\"result\":{\"message_id\":42,\"chat\":{\"id\":-100123,\"title\":\"Grupo nuevo\"}}}");
        var result = service.enviarConConfirmacion("Hola *Ana* [prueba] _texto_", false);
        assertEquals("CONFIRMADO", result.estado());
        assertEquals("-100123", result.chatId());
        assertEquals("Grupo nuevo", result.chatTitulo());
        assertEquals(42L, result.messageId());
        var captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(captor.capture(), any());
        var body = mapper.readTree(readBody(captor.getValue()));
        assertEquals("-100123", body.path("chat_id").asText());
        assertEquals("Hola *Ana* [prueba] _texto_", body.path("text").asText());
        assertFalse(body.has("parse_mode"));
    }

    @Test
    void rejectsApiFailureEvenWithHttp200AndRedactsToken() throws Exception {
        response(200, "{\"ok\":false,\"description\":\"invalid token-de-prueba\"}");
        var result = service.enviarConConfirmacion("Prueba", false);
        assertEquals("FALLIDO", result.estado());
        assertFalse(result.detalle().contains("token-de-prueba"));
        assertNull(result.messageId());
    }

    @Test
    void disabledAndOversizedMessagesNeverCallTelegram() {
        assertEquals("FALLIDO", service.enviarConConfirmacion("x".repeat(4097), false).estado());
        ReflectionTestUtils.setField(service, "enabledFlag", false);
        assertEquals("DESHABILITADO", service.enviarConConfirmacion("Prueba", false).estado());
        verifyNoInteractions(client);
    }

    @Test
    void timeoutsAreUncertainAndAreNotRetried() throws Exception {
        when(client.send(any(), any())).thenThrow(new HttpTimeoutException("secret URL token-de-prueba"));
        var result = service.enviarConConfirmacion("Prueba", false);
        assertEquals("INCIERTO", result.estado());
        assertFalse(result.detalle().contains("token-de-prueba"));
        verify(client, times(1)).send(any(), any());
    }

    @Test
    void malformedSuccessCannotBeReportedAsConfirmed() throws Exception {
        response(200, "{\"ok\":true,\"result\":{}}");
        assertEquals("INCIERTO", service.enviarConConfirmacion("Prueba", true).estado());
    }

    @Test
    void apiRejectionIsShownWithoutClaimingDelivery() throws Exception {
        response(403, "{\"ok\":false,\"description\":\"Forbidden: bot was kicked\"}");
        var result = service.enviarConConfirmacion("Prueba", true);
        assertEquals("FALLIDO", result.estado());
        assertTrue(result.detalle().contains("bot was kicked"));
    }

    @Test
    void nextRealNotificationUsesSavedGroupWithoutRestart() throws Exception {
        response(200, "{\"ok\":true,\"result\":{\"message_id\":43,\"chat\":{\"id\":-100999}}}");
        when(configuracion.obtenerDestino()).thenReturn(new TelegramConfiguracionService.Destino("-100999", "SISTEMA"));
        service.enviarMensaje("Alerta real");
        var captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(captor.capture(), any());
        assertEquals("-100999", mapper.readTree(readBody(captor.getValue())).path("chat_id").asText());
        assertEquals("-100999", service.getEstado().chatId());
    }

    @Test
    void savingGroupCanEnableMissingDestinationButNeverMissingToken() {
        when(configuracion.obtenerDestino()).thenReturn(new TelegramConfiguracionService.Destino(null, "SERVIDOR"));
        assertFalse(service.isEnabled());
        doAnswer(invocation -> {
            when(configuracion.obtenerDestino()).thenReturn(new TelegramConfiguracionService.Destino("-100999", "SISTEMA"));
            return null;
        }).when(configuracion).guardarChatId("-100999");
        assertTrue(service.guardarChatId("-100999").habilitado());
        ReflectionTestUtils.setField(service, "botToken", "GENERATE_ME");
        service.init();
        assertFalse(service.getEstado().botConfigurado());
        assertFalse(service.isEnabled());
        verifyNoInteractions(client);
    }

    @Test
    void failedSendReportsTheDestinationUsedEvenIfConfigChangesInFlight() throws Exception {
        response(403, "{\"ok\":false,\"description\":\"Forbidden\"}");
        when(configuracion.obtenerDestino()).thenReturn(
            new TelegramConfiguracionService.Destino("-100123", "SERVIDOR"),
            new TelegramConfiguracionService.Destino("-100999", "SISTEMA"));
        assertEquals("-100123", service.enviarConConfirmacion("Prueba", false).chatId());
        assertEquals("-100999", service.getChatId());
    }

    private String readBody(HttpRequest request) {
        CompletableFuture<String> body = new CompletableFuture<>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer buffer) {
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            public void onError(Throwable error) { body.completeExceptionally(error); }
            public void onComplete() { body.complete(bytes.toString(StandardCharsets.UTF_8)); }
        });
        return body.join();
    }
}
