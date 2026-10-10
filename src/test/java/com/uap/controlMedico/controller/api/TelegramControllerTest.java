package com.uap.controlMedico.controller.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;

import com.uap.controlMedico.exception.BusinessException;
import com.uap.controlMedico.model.dao.UsuarioDao;
import com.uap.controlMedico.model.dto.notificacionDto.NotificacionResponse;
import com.uap.controlMedico.model.entity.Usuario;
import com.uap.controlMedico.model.service.notificacion.NotificacionService;
import com.uap.controlMedico.model.service.telegram.TelegramEnvioResultado;
import com.uap.controlMedico.model.service.telegram.TelegramNotificationService;
import com.uap.controlMedico.model.service.telegram.TelegramPlantillaService;
import com.uap.controlMedico.controller.api.TelegramController.PruebaRequest;
import com.uap.controlMedico.controller.api.TelegramController.TipoPrueba;

class TelegramControllerTest {
    private AnnotationConfigApplicationContext context;
    private TelegramController controller;
    private TelegramNotificationService telegram;
    private UsuarioDao usuarios;
    private NotificacionService notificaciones;

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean TelegramNotificationService telegram() { return mock(TelegramNotificationService.class); }
        @Bean UsuarioDao usuarios() { return mock(UsuarioDao.class); }
        @Bean NotificacionService notificaciones() { return mock(NotificacionService.class); }
        @Bean TelegramPlantillaService plantillas() { return mock(TelegramPlantillaService.class); }
        @Bean TelegramPlantillaController plantillaController(TelegramPlantillaService plantillas) {
            return new TelegramPlantillaController(plantillas);
        }
        @Bean TelegramController controller(TelegramNotificationService telegram, UsuarioDao usuarios,
                NotificacionService notificaciones, TelegramPlantillaService plantillas) {
            return new TelegramController(telegram, usuarios, notificaciones, plantillas);
        }
    }

    @BeforeEach
    void setup() {
        context = new AnnotationConfigApplicationContext(Config.class);
        controller = context.getBean(TelegramController.class);
        when(context.getBean(TelegramPlantillaService.class).renderizar(any(), any()))
            .thenReturn("SEÑAL PERDIDA — prueba de plantilla guardada");
        telegram = context.getBean(TelegramNotificationService.class);
        usuarios = context.getBean(UsuarioDao.class);
        notificaciones = context.getBean(NotificacionService.class);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("admin", "", "ROLE_ADMIN"));
        when(telegram.enviarConConfirmacion(anyString(), anyBoolean())).thenReturn(
            new TelegramEnvioResultado("CONFIRMADO", "-100123", "Prueba", 42L, "Aceptado"));
    }

    @AfterEach
    void close() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void nonAdminCannotReadDestinationOrSend() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("supervisor", "", "ROLE_SUPERVISOR"));
        assertThrows(AccessDeniedException.class, () -> controller.estado());
        assertThrows(AccessDeniedException.class, () -> controller.guardarConfiguracion(
            new TelegramController.ConfiguracionRequest("-100999")));
        assertThrows(AccessDeniedException.class, () -> controller.enviarPrueba("mensaje"));
        assertThrows(AccessDeniedException.class, () -> controller.probar(
            new PruebaRequest(TipoPrueba.SENAL_PERDIDA, null, null, false), () -> "supervisor"));
        var gestion = context.getBean(TelegramPlantillaController.class);
        assertThrows(AccessDeniedException.class, gestion::listar);
        assertThrows(AccessDeniedException.class, () -> gestion.guardar(
            com.uap.controlMedico.model.enums.TipoMensajeTelegram.FALTA,
            new TelegramPlantillaController.GuardarRequest("Texto", false)));
        assertThrows(AccessDeniedException.class, () -> gestion.previsualizar(
            new TelegramPlantillaController.PreviewRequest("Texto", java.util.Map.of())));
        assertThrows(AccessDeniedException.class, () -> gestion.enviar(
            com.uap.controlMedico.model.enums.TipoMensajeTelegram.FALTA,
            new TelegramPlantillaController.EnviarRequest(java.util.Map.of(), "Texto")));
        verifyNoInteractions(telegram, usuarios, notificaciones);
    }

    @Test
    void adminCanPersistDestinationWithoutSendingMessages() {
        var estado = new com.uap.controlMedico.model.service.telegram.TelegramEstado(
            true, "-100999", "SISTEMA", true, true);
        when(telegram.guardarChatId("-100999")).thenReturn(estado);
        assertEquals(estado, controller.guardarConfiguracion(
            new TelegramController.ConfiguracionRequest("-100999")).getData());
        verify(telegram).guardarChatId("-100999");
        verify(telegram, never()).enviarConConfirmacion(anyString(), anyBoolean());
        verifyNoInteractions(usuarios, notificaciones);
    }

    @Test
    void simulationDoesNotCreateNotificationsOrIncidentsByDefault() {
        var result = controller.probar(new PruebaRequest(TipoPrueba.SENAL_PERDIDA, null, null, false), () -> "admin").getData();
        assertEquals("CONFIRMADO", result.telegram().estado());
        assertTrue(result.texto().contains("SIN INCIDENCIA REAL"));
        assertTrue(result.texto().contains("SEÑAL PERDIDA"));
        assertEquals("NO_SOLICITADO", result.sicope());
        verifyNoInteractions(usuarios, notificaciones);
        verify(telegram).enviarConConfirmacion(result.texto(), false);
    }

    @Test
    void sicopeSendRequiresSpecificUserAndCannotBroadcast() {
        assertThrows(BusinessException.class, () -> controller.probar(
            new PruebaRequest(TipoPrueba.PERSONALIZADO, null, "Hola", true), () -> "admin"));
        verifyNoInteractions(telegram, usuarios, notificaciones);
    }

    @Test
    void rejectsInactiveUserBeforeAnySend() {
        when(usuarios.findById(7L)).thenReturn(Optional.of(Usuario.builder().activo(false).build()));
        assertThrows(BusinessException.class, () -> controller.probar(
            new PruebaRequest(TipoPrueba.PERSONALIZADO, 7L, "Hola", false), () -> "admin"));
        verifyNoInteractions(telegram, notificaciones);
    }

    @Test
    void blankCustomMessageCannotBeSent() {
        assertThrows(BusinessException.class, () -> controller.probar(
            new PruebaRequest(TipoPrueba.PERSONALIZADO, null, "  ", false), () -> "admin"));
        verifyNoInteractions(telegram, notificaciones);
    }

    @Test
    void optionalSicopeNotificationIsPersonalAndClearlyMarkedAsTest() {
        when(usuarios.findById(7L)).thenReturn(Optional.of(Usuario.builder()
            .nombre("Ana").apellido("Prueba").username("ana").activo(true).build()));
        var saved = mock(NotificacionResponse.class);
        when(saved.getIdNotificacion()).thenReturn(12L);
        when(notificaciones.enviarPersonalizado(any(), eq("admin"))).thenReturn(saved);
        var result = controller.probar(new PruebaRequest(TipoPrueba.PERSONALIZADO, 7L, "Hola", true), () -> "admin").getData();
        assertEquals("REGISTRADO", result.sicope());
        assertEquals(12L, result.idNotificacion());
        verify(notificaciones).enviarPersonalizado(argThat(r -> "ana".equals(r.getUsernameDestinatario())
            && r.getTitulo().startsWith("PRUEBA") && "CUSTOM".equals(r.getTipo())), eq("admin"));
    }

    @Test
    void reportsTelegramSuccessSeparatelyWhenSicopeFails() {
        when(usuarios.findById(7L)).thenReturn(Optional.of(Usuario.builder()
            .nombre("Ana").apellido("Prueba").username("ana").activo(true).build()));
        when(notificaciones.enviarPersonalizado(any(), anyString())).thenThrow(new RuntimeException("db"));
        var result = controller.probar(new PruebaRequest(TipoPrueba.PERSONALIZADO, 7L, "Hola", true), () -> "admin").getData();
        assertEquals("CONFIRMADO", result.telegram().estado());
        assertEquals("NO_CONFIRMADO", result.sicope());
        verify(telegram, times(1)).enviarConConfirmacion(anyString(), eq(false));
    }
}
