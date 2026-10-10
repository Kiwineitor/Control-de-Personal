package com.uap.controlMedico.model.dto.gestionDto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.uap.controlMedico.model.enums.TipoPermiso;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SolicitudPermisoRequest {
    /**
     * Solo uno de los dos debe venir con valor.
     * El servicio valida que no vengan ambos o ninguno.
     */
    private Long idTitular;
    private Long idInterno;
 
    @NotNull(message = "El tipo de permiso es obligatorio")
    private TipoPermiso tipoPermiso;
 
    @NotNull(message = "La fecha de inicio es obligatoria")
    private LocalDate fechaInicio;
 
    @NotNull(message = "La fecha de fin es obligatoria")
    private LocalDate fechaFin;
 
    private LocalTime horaInicio;
    private LocalTime horaFin;
 
    @NotNull(message = "El motivo es obligatorio")
    @Size(max = 500)
    private String motivo;
 
    /** URL del documento ya subido al servidor SMB. */
    private String documentoUrl;

}
