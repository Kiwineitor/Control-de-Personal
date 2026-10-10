package com.uap.controlMedico.model.dto.personalDto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request unificado para registrar un funcionario del hospital:
 * Crea Persona + Titular (+ opcionalmente Usuario) en un solo paso.
 */
@Data
public class RegistrarPersonalRequest {

    // ── Datos de Persona ──────────────────────────────────────────────
    @NotBlank @Size(max = 100)
    private String nombre;

    @NotBlank @Size(max = 100)
    private String apellido;

    @Size(max = 20)
    private String ci;

    @Size(max = 150)
    private String email;

    @Size(max = 20)
    private String telefono;

    // ── Datos de Titular ──────────────────────────────────────────────
    @NotNull
    private Long idTipoPersonal;

    @Size(max = 150)
    private String cargo;

    @Size(max = 150)
    private String especialidad;

    /** Lugar de trabajo habitual (opcional, para asignación por defecto). */
    private Long idLugar;

    // ── Cuenta de usuario (opcional) ─────────────────────────────────
    private boolean crearUsuario = false;

    /** Username si se crea usuario. Si vacío, se auto-genera. */
    @Size(max = 50)
    private String username;

    /** Password inicial. Si vacío, se auto-genera. */
    @Size(max = 100)
    private String password;

    /** Rol para el usuario. Si null, se infiere del TipoPersonal. */
    private Long idRol;
}
