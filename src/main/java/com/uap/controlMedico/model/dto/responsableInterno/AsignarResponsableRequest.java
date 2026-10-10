package com.uap.controlMedico.model.dto.responsableInterno;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class AsignarResponsableRequest {
    
    @NotNull(message = "El ID del titular es obligatorio")
    @Positive
    private Long idTitular;
 
    /**
     * IDs de internos a asignar.
     * Si asignarTodos = true, este campo se ignora.
     * Si asignarTodos = false y la lista está vacía → error de validación.
     */
    private List<Long> idInternos;
 
    /**
     * Si true → asigna el titular a TODOS los internos activos.
     * Si false → asigna solo a los internos en idInternos.
     */
    private boolean asignarTodos = false;
 
    /** Fecha fin de la asignación (opcional, null = indefinida). */
    private LocalDate fechaFinAsignacion;
 
    /** Observación opcional. */
    private String observaciones;

}
