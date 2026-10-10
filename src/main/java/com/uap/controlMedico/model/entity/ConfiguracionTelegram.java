package com.uap.controlMedico.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Destino global de Telegram. El token continúa exclusivamente en el servidor. */
@Entity
@Table(name = "configuracion_telegram")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ConfiguracionTelegram {
    public static final Long ID = 1L;

    @Id
    private Long id;

    @Column(name = "chat_id", nullable = false, length = 20)
    private String chatId;
}
