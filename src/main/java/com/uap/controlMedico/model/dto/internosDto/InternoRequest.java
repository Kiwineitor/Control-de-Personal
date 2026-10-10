package com.uap.controlMedico.model.dto.internosDto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InternoRequest {

    /**
     * Si se informa, el interno se vincula a esta persona ya existente
     * en lugar de crear una nueva. Los campos nombre/apellido/ci/email/telefono
     * se ignoran en ese caso.
     */
    private Long idPersona;

    // ── Datos de Persona (todo manual — internos no están en Londra) ──
 
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100)
    private String nombre;
 
    @NotBlank(message = "El apellido es obligatorio")
    @Size(max = 100)
    private String apellido;
 
    @Size(max = 20)
    private String ci;
 
    @Email(message = "Email inválido")
    @Size(max = 150)
    private String email;
 
    @Size(max = 20)
    private String telefono;
 
    // ── Datos académicos propios del interno ──────────────────────────
 
    @NotBlank(message = "La universidad es obligatoria")
    @Size(max = 150)
    private String universidad;
 
    @NotBlank(message = "La carrera es obligatoria")
    @Size(max = 150)
    private String carrera;
 
    @Min(value = 1, message = "El año debe ser mayor a 0")
    @Max(value = 10, message = "El año no puede ser mayor a 10")
    private Integer anoFormacion;
 
    @Size(max = 50)
    private String nroMatricula;
 
    private LocalDate fechaInicioRotacion;

    private LocalDate fechaFinRotacion;

    private boolean activo = true;

    // ── Cuenta de usuario (opcional) ──────────────────────────────────
    /** Si es true, se crea una cuenta de usuario para que el interno acceda a la app. */
    private boolean crearUsuario = false;

    /** Username deseado. Si está vacío y crearUsuario=true, se autogenera. */
    @Size(max = 50)
    private String username;

    /** Contraseña en claro. Si está vacía y crearUsuario=true, se autogenera. */
    @Size(max = 100)
    private String password;

    /** Rol a asignar. Si es null, se usa el rol INTERNO por defecto. */
    private Long idRol;
}
