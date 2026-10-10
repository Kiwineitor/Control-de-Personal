package com.uap.controlMedico.model.service.telegram;

import com.uap.controlMedico.model.enums.TipoMensajeTelegram;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import com.uap.controlMedico.exception.BusinessException;
import com.uap.controlMedico.model.dao.PlantillaTelegramDao;
import com.uap.controlMedico.model.entity.PlantillaTelegram;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramPlantillaServiceImpl implements TelegramPlantillaService {
    private static final Pattern VARIABLE = Pattern.compile("\\{([^{}]+)\\}");
    private final PlantillaTelegramDao dao;
    private final TelegramNotificationService telegram;

    private Plantilla obtener(TipoMensajeTelegram tipo) {
        return dao.findById(tipo).map(p -> new Plantilla(tipo, tipo.getTitulo(), p.getTexto(), p.isAutomatico()))
            .orElseGet(() -> new Plantilla(tipo, tipo.getTitulo(), tipo.plantillaInicial(), true));
    }

    @Override
    public List<Plantilla> listar() {
        return Arrays.stream(TipoMensajeTelegram.values()).map(this::obtener).toList();
    }

    @Override
    public Plantilla guardar(TipoMensajeTelegram tipo, String texto, boolean automatico) {
        validarPlantilla(texto);
        dao.saveAndFlush(new PlantillaTelegram(tipo, texto.strip(), automatico));
        return obtener(tipo);
    }

    private void validarPlantilla(String texto) {
        if (texto == null || texto.isBlank() || texto.length() > 3000)
            throw new BusinessException("La plantilla debe tener entre 1 y 3000 caracteres.");
        var matcher = VARIABLE.matcher(texto);
        while (matcher.find()) {
            if (!VARIABLES.contains(matcher.group(1)))
                throw new BusinessException("Variable desconocida: " + matcher.group(1));
        }
        String literal = VARIABLE.matcher(texto).replaceAll("");
        if (literal.contains("{") || literal.contains("}"))
            throw new BusinessException("Revisa las llaves de las variables de la plantilla.");
    }

    @Override
    public String previsualizar(String texto, Map<String, String> valores) {
        validarPlantilla(texto);
        if (valores == null || valores.size() > VARIABLES.size())
            throw new BusinessException("Completa los datos del mensaje.");
        for (var entry : valores.entrySet()) {
            if (!VARIABLES.contains(entry.getKey()) || entry.getValue() == null || entry.getValue().length() > 1000)
                throw new BusinessException("Dato de mensaje inválido; máximo 1000 caracteres por campo.");
        }
        // Una sola pasada: los valores nunca se interpretan como nuevas variables ni como Markdown.
        var matcher = VARIABLE.matcher(texto);
        String resultado = matcher.replaceAll(match -> java.util.regex.Matcher.quoteReplacement(
            valores.getOrDefault(match.group(1), ""))).strip();
        if (resultado.isBlank() || resultado.length() > 4096)
            throw new BusinessException("El mensaje final debe tener entre 1 y 4096 caracteres.");
        return resultado;
    }

    @Override
    public String renderizar(TipoMensajeTelegram tipo, Map<String, String> valores) {
        return previsualizar(obtener(tipo).texto(), valores);
    }

    @Override
    @Async("telegramExecutor")
    public void enviarAutomatico(TipoMensajeTelegram tipo, Map<String, String> valores) {
        try {
            var plantilla = obtener(tipo);
            if (!plantilla.automatico()) return;
            var resultado = telegram.enviarConConfirmacion(previsualizar(plantilla.texto(), valores), false);
            if (!"CONFIRMADO".equals(resultado.estado()) && !"DESHABILITADO".equals(resultado.estado()))
                log.warn("Telegram {}: {}", tipo, resultado.estado());
        } catch (RuntimeException e) {
            // Un fallo de plantillas no debe interrumpir el detector ni disparar un envío al destino anterior.
            log.warn("No se pudo preparar el aviso Telegram {} ({})", tipo, e.getClass().getSimpleName());
        }
    }

    @Override
    public TelegramEnvioResultado enviarManual(TipoMensajeTelegram tipo, Map<String, String> valores, String textoEsperado) {
        String texto = renderizar(tipo, valores);
        if (!texto.equals(textoEsperado))
            throw new BusinessException("La plantilla cambió. Recarga y genera otra vista previa antes de enviar.");
        return telegram.enviarConConfirmacion(texto, false);
    }
}
