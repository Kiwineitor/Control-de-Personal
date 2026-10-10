package com.uap.controlMedico.model.dto.titularDto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TitularRequest {

    /**
     * Si se informa, el titular se vincula a esta persona ya existente
     * en lugar de crear una nueva. Los campos nombre/apellido/ci/email/telefono
     * se ignoran en ese caso.
     */
    private Long idPersona;

        // ── Referencias Londra ────────────────────────────────────────────
    @NotNull(message = "El perId de Londra es obligatorio")
    @Positive
    private Long perId;
 
    @NotNull(message = "El contratoId de Londra es obligatorio")
    @Positive
    private Long contratoId;
 
    // ── Datos de Persona (manual hasta integrar Londra) ───────────────
    @Size(max = 100)
    private String nombre;
 
    @Size(max = 100)
    private String apellido;
 
    @Size(max = 20)
    private String ci;
 
    @Email(message = "Email inválido")
    @Size(max = 150)
    private String email;
 
    @Size(max = 20)
    private String telefono;
 
    // ── Datos propios del Titular ────────────────────────────────────
    @Size(max = 150)
    private String cargo;
 
    @Size(max = 150)
    private String especialidad;
 
    private boolean activo = true;
 
    /**
     * Si true → al guardar se intenta sincronizar desde Londra.
     * Default false: entrada manual de datos.
     */
    private boolean sincronizarDesdeLondra = false;
}