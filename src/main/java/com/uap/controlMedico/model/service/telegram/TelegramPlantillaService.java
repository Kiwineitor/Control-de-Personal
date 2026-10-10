package com.uap.controlMedico.model.service.telegram;

import com.uap.controlMedico.model.enums.TipoMensajeTelegram;

import java.util.List;
import java.util.Map;

public interface TelegramPlantillaService {
    List<String> VARIABLES = List.of("nombre", "contexto", "momento", "dispositivo", "detalle");
    record Plantilla(TipoMensajeTelegram tipo, String nombre, String texto, boolean automatico) {}
    List<Plantilla> listar();
    Plantilla guardar(TipoMensajeTelegram tipo, String texto, boolean automatico);
    String previsualizar(String texto, Map<String, String> valores);
    String renderizar(TipoMensajeTelegram tipo, Map<String, String> valores);
    void enviarAutomatico(TipoMensajeTelegram tipo, Map<String, String> valores);
    TelegramEnvioResultado enviarManual(TipoMensajeTelegram tipo, Map<String, String> valores, String textoEsperado);
}
