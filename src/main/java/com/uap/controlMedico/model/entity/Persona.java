package com.uap.controlMedico.model.entity;

import com.uap.controlMedico.config.AuditoriaConfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "persona", indexes = {
    @Index(name = "idx_persona_ci", columnList = "ci")
})
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Persona extends AuditoriaConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_persona")
    private Long idPersona;

    @Column(length = 100)
    private String nombre;

    @Column(length = 100)
    private String apellido;

    @Column(length = 20)
    private String ci;

    @Column(length = 150)
    private String email;

    @Column(length = 20)
    private String telefono;

    @Transient
    public String getNombreCompleto() {
        if (nombre == null && apellido == null) return "Sin nombre registrado";
        return (apellido != null ? apellido : "") + " " + (nombre != null ? nombre : "");
    }
}
