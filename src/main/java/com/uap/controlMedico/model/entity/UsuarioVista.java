package com.uap.controlMedico.model.entity;

import com.uap.controlMedico.model.enums.TipoOverrideVista;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ajuste por usuario sobre las vistas de su rol: GRANT agrega una vista extra,
 * DENY quita una vista que el rol sí incluye. Es la capa "por usuario" del modelo híbrido.
 */
@Entity
@Table(
    name = "usuario_vista",
    uniqueConstraints = @UniqueConstraint(name = "uk_usuario_vista", columnNames = {"id_usuario", "id_vista"})
)
@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
public class UsuarioVista {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idUsuarioVista;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false, foreignKey = @ForeignKey(name = "fk_usuario_vista_usuario"))
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_vista", nullable = false, foreignKey = @ForeignKey(name = "fk_usuario_vista_vista"))
    private Vista vista;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoOverrideVista tipo;
}
