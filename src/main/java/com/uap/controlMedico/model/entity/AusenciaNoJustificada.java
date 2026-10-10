package com.uap.controlMedico.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ausencia_no_justificada")
@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
public class AusenciaNoJustificada {
    
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idAusencia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false,
                foreignKey = @ForeignKey(name = "fk_anj_usuario"))
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_jornada",
                foreignKey = @ForeignKey(name = "fk_anj_jornada"))
    private Jornada jornada;

    @Column(name = "inicio_ausencia", nullable = false)
    private LocalDateTime inicioAusencia;

    @Column(name = "fin_ausencia")
    private LocalDateTime finAusencia;

    @Column(name = "notificaciones_enviadas", nullable = false)
    @Builder.Default
    private int notificacionesEnviadas = 0;

    @Column(name = "registrado_por", length = 100)
    private String registradoPor;

    @Column(nullable = false)
    @Builder.Default
    private boolean resuelta = false;
}
