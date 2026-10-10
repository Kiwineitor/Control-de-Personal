package com.uap.controlMedico.model.enums;

public enum TipoMensajeTelegram {
    FALTA("🚫 FALTA — NO SE PRESENTÓ"),
    ATRASO("⏰ ATRASO"),
    ABANDONO_ZONA("📍 ABANDONO DE ÁREA"),
    AUSENCIA_SENAL("📡 SIN SEÑAL — AUSENCIA REGISTRADA"),
    LOGOUT_EN_TURNO("🚪 CERRÓ SESIÓN EN TURNO"),
    SALIDA_TEMPRANA("↩️ SALIDA TEMPRANA"),
    RETORNO_ZONA("✅ REGRESÓ AL ÁREA"),
    SENAL_PERDIDA("📡 SEÑAL PERDIDA"),
    SENAL_RECUPERADA("✅ SEÑAL RECUPERADA"),
    GPS_NO_ACTIVADO("📡 GPS NO ACTIVADO");

    private final String titulo;
    TipoMensajeTelegram(String titulo) { this.titulo = titulo; }
    public String getTitulo() { return titulo; }
    public String plantillaInicial() {
        return titulo + "\n👤 {nombre}\n📍 {contexto}\n🕐 {momento}\n{dispositivo}\n{detalle}";
    }
}
