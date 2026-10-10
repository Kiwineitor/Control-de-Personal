package com.uap.controlMedico.model.dto.catalagoDto;

import com.uap.controlMedico.model.enums.TipoActividad;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data @NoArgsConstructor @AllArgsConstructor
public class ActividadRequest {
    
    @NotBlank(message = "El nombre de la actividad es obligatorio")
    @Size(max = 150)
    private String nombre;
 
    @Size(max = 500)
    private String descripcion;

    private TipoActividad tipoActividad = TipoActividad.CLINICA;
 
    private boolean activo = true;

}
