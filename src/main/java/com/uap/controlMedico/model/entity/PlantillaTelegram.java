package com.uap.controlMedico.model.entity;

import jakarta.persistence.*;
import lombok.*;
import com.uap.controlMedico.model.enums.TipoMensajeTelegram;

@Entity
@Table(name = "plantilla_telegram")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PlantillaTelegram {
    @Id @Enumerated(EnumType.STRING) @Column(length = 40)
    private TipoMensajeTelegram tipo;
    @Column(nullable = false, length = 3000)
    private String texto;
    @Column(nullable = false)
    private boolean automatico;
}
