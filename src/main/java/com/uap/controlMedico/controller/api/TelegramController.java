package com.uap.controlMedico.controller.api;

import java.security.Principal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.uap.controlMedico.exception.BusinessException;
import com.uap.controlMedico.model.dao.UsuarioDao;
import com.uap.controlMedico.model.dto.notificacionDto.EnviarNotificacionRequest;
import com.uap.controlMedico.model.dto.response.ApiResponse;
import com.uap.controlMedico.model.service.notificacion.NotificacionService;
import com.uap.controlMedico.model.service.telegram.TelegramEnvioResultado;
import com.uap.controlMedico.model.service.telegram.TelegramEstado;
import com.uap.controlMedico.model.service.telegram.TelegramPlantillaService;
import com.uap.controlMedico.model.enums.TipoMensajeTelegram;
import com.uap.controlMedico.model.service.telegram.TelegramNotificationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/** Diagnóstico administrativo. Nunca crea sesiones ni incidencias de monitoreo. */
@RestController
@RequestMapping("/api/v1/telegram")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class TelegramController {
    private final TelegramNotificationService telegramService;
    private final UsuarioDao usuarioDao;
    private final NotificacionService notificacionService;
    private final TelegramPlantillaService plantillas;

    public enum TipoPrueba { PERSONALIZADO, SENAL_PERDIDA }

    public record PruebaRequest(
        @NotNull TipoPrueba tipo,
        @Positive Long idUsuario,
        @Size(max = 2000) String mensaje,
        boolean enviarEnSicope
    ) {}

    public record ConfiguracionRequest(@NotBlank @Size(max = 30) String chatId) {}
    public record PruebaResultado(TelegramEnvioResultado telegram, String texto,
                                 String sicope, Long idNotificacion) {}

    @GetMapping("/admin/estado")
    public ApiResponse<TelegramEstado> estado() {
        return ApiResponse.ok(telegramService.getEstado());
    }

    @PutMapping("/admin/configuracion")
    public ApiResponse<TelegramEstado> guardarConfiguracion(@Valid @RequestBody ConfiguracionRequest request) {
        return ApiResponse.ok(telegramService.guardarChatId(request.chatId()));
    }

    /** Conserva el comando de diagnóstico anterior, ahora con confirmación real. */
    @PostMapping("/admin/test")
    public ApiResponse<PruebaResultado> enviarPrueba(
            @RequestParam(required = false) String mensaje) {
        String contenido = mensaje == null || mensaje.isBlank() ? "Prueba de conexión SICOPE" : mensaje;
        if (contenido.length() > 2000) throw new BusinessException("Máximo 2000 caracteres.");
        String texto = "[PRUEBA SICOPE]\n" + contenido;
        return ApiResponse.ok(new PruebaResultado(
            telegramService.enviarConConfirmacion(texto, false), texto, "NO_SOLICITADO", null));
    }

    @PostMapping("/admin/pruebas")
    public ApiResponse<PruebaResultado> probar(@Valid @RequestBody PruebaRequest request,
                                              Principal principal) {
        if (request.enviarEnSicope() && request.idUsuario() == null) {
            throw new BusinessException("Selecciona un usuario para enviar en SICOPE.");
        }
        if (request.tipo() == TipoPrueba.PERSONALIZADO
                && (request.mensaje() == null || request.mensaje().isBlank())) {
            throw new BusinessException("Escribe el mensaje de prueba.");
        }
        var usuario = request.idUsuario() == null ? null : usuarioDao.findById(request.idUsuario())
            .filter(u -> u.isActivo())
            .orElseThrow(() -> new BusinessException("El usuario no existe o está desactivado."));
        String nombre = usuario == null ? "Usuario de prueba" : usuario.getNombre() + " " + usuario.getApellido();
        boolean simular = request.tipo() == TipoPrueba.SENAL_PERDIDA;
        String hora = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        String contenido = simular
            ? plantillas.renderizar(TipoMensajeTelegram.SENAL_PERDIDA, java.util.Map.of(
                "nombre", nombre, "contexto", "Área de prueba — Turno de prueba",
                "momento", "Última señal: hace 4 minutos",
                "dispositivo", "Batería: 80% · Conexión: sin dato (simulado)",
                "detalle", "Dejó de emitir ubicación. Su última posición conocida estaba DENTRO del área."))
            : "Usuario de referencia: " + nombre + "\n" + request.mensaje().strip();
        // La prueba utiliza la misma plantilla editable que las alertas, siempre en texto plano.
        String texto = "PRUEBA SICOPE — SIN INCIDENCIA REAL\n" + hora + "\n\n" + contenido
            + (simular ? "\n\nDatos simulados. La mención del aviso al funcionario forma parte de la plantilla." : "");

        var telegram = telegramService.enviarConConfirmacion(texto, false);
        String sicope = "NO_SOLICITADO";
        Long idNotificacion = null;
        if (request.enviarEnSicope()) {
            EnviarNotificacionRequest notificacion = new EnviarNotificacionRequest();
            notificacion.setUsernameDestinatario(usuario.getUsername());
            notificacion.setTipo("CUSTOM");
            notificacion.setTitulo("PRUEBA SICOPE — " + (simular ? "Señal perdida" : "Mensaje personalizado"));
            notificacion.setMensaje(simular
                ? "Prueba de señal perdida: se simulan 4 minutos sin ubicación. No es una incidencia real."
                : "PRUEBA: " + request.mensaje().strip());
            try {
                idNotificacion = notificacionService.enviarPersonalizado(notificacion, principal.getName())
                    .getIdNotificacion();
                sicope = "REGISTRADO";
            } catch (RuntimeException e) {
                // No ocultar que Telegram pudo haber aceptado el mensaje: evita duplicados al reintentar.
                sicope = "NO_CONFIRMADO";
            }
        }
        return ApiResponse.ok(new PruebaResultado(telegram, texto, sicope, idNotificacion));
    }
}
