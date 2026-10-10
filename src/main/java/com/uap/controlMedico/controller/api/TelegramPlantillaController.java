package com.uap.controlMedico.controller.api;

import com.uap.controlMedico.model.enums.TipoMensajeTelegram;

import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.uap.controlMedico.model.dto.response.ApiResponse;
import com.uap.controlMedico.model.service.telegram.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/telegram/admin/plantillas")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class TelegramPlantillaController {
    private final TelegramPlantillaService plantillas;

    public record GuardarRequest(@NotBlank @Size(max = 3000) String texto, @NotNull Boolean automatico) {}
    public record PreviewRequest(@NotBlank @Size(max = 3000) String texto,
                                 @NotNull @Size(max = 5) Map<String, String> valores) {}
    public record EnviarRequest(@NotNull @Size(max = 5) Map<String, String> valores,
                                @NotBlank @Size(max = 4096) String textoEsperado) {}

    @GetMapping
    public ApiResponse<List<TelegramPlantillaService.Plantilla>> listar() {
        return ApiResponse.ok(plantillas.listar());
    }

    @PutMapping("/{tipo}")
    public ApiResponse<TelegramPlantillaService.Plantilla> guardar(@PathVariable TipoMensajeTelegram tipo,
            @Valid @RequestBody GuardarRequest request) {
        return ApiResponse.ok(plantillas.guardar(tipo, request.texto(), request.automatico()));
    }

    @PostMapping("/previsualizar")
    public ApiResponse<String> previsualizar(@Valid @RequestBody PreviewRequest request) {
        // Usar la sobrecarga con datos: ApiResponse.ok(String) sólo establece el mensaje.
        return ApiResponse.ok("Vista previa", plantillas.previsualizar(request.texto(), request.valores()));
    }

    @PostMapping("/{tipo}/enviar")
    public ApiResponse<TelegramEnvioResultado> enviar(@PathVariable TipoMensajeTelegram tipo,
            @Valid @RequestBody EnviarRequest request) {
        return ApiResponse.ok(plantillas.enviarManual(tipo, request.valores(), request.textoEsperado()));
    }
}
