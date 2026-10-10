package com.uap.controlMedico.model.entity;

import jakarta.persistence.Entity;
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

/** Vista base incluida por un rol (conjunto base del modelo híbrido). */
@Entity
@Table(
    name = "rol_vista",
    uniqueConstraints = @UniqueConstraint(name = "uk_rol_vista", columnNames = {"id_rol", "id_vista"})
)
@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
public class RolVista {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idRolVista;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_rol", nullable = false, foreignKey = @ForeignKey(name = "fk_rol_vista_rol"))
    private Rol rol;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_vista", nullable = false, foreignKey = @ForeignKey(name = "fk_rol_vista_vista"))
    private Vista vista;
}
