package com.uap.controlMedico.model.entity;

import java.util.ArrayList;
import java.util.List;

import com.uap.controlMedico.config.AuditoriaConfig;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "rol")
@Setter
@Getter
@NoArgsConstructor
@SuperBuilder
public class Rol extends AuditoriaConfig{
    private static final long serialVersionUID = 2629195288020321924L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idRol;
    private String nombre;
    private String estado;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "rol")
    @Builder.Default
    private List<UsuarioRol> usuariosRol = new ArrayList<>();

}
