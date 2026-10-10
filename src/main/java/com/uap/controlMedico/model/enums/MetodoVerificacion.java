package com.uap.controlMedico.model.enums;

public enum MetodoVerificacion {
    GPS,    // Verificación automática por geolocalización
    QR,     // Escaneo del código QR del lugar
    FOTO,   // Foto con token, lat/lng y marca del sistema
    MANUAL  // Confirmación manual por administrador
}
