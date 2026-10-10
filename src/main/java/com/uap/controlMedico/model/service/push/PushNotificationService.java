package com.uap.controlMedico.model.service.push;

import com.uap.controlMedico.model.dto.push.PushSubscripcionRequest;

public interface PushNotificationService {

    /** Registra o actualiza una suscripción push para el usuario. */
    void guardarSubscripcion(String username, PushSubscripcionRequest request);

    /** Elimina la suscripción identificada por su endpoint. */
    void eliminarSubscripcion(String username, String endpoint);

    /**
     * Envía una notificación push al usuario indicado.
     *
     * @param username  username del destinatario
     * @param titulo    título de la notificación
     * @param mensaje   cuerpo del mensaje
     * @param tipo      tipo lógico (JORNADA_ASIGNADA, PERMISO_APROBADO, …)
     * @param url       ruta de la app a abrir al hacer click
     * @param urgente   si true, la notificación requiere interacción activa
     */
    void enviarAUsuario(String username,
                        String titulo, String mensaje,
                        String tipo, String url, boolean urgente);

    /** Clave pública VAPID en formato base64url para el frontend. */
    String getPublicKey();

    /** Indica si el servicio está listo (VAPID keys configuradas). */
    boolean isEnabled();
}
