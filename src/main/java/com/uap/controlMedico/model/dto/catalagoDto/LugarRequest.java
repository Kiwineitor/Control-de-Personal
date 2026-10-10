package com.uap.controlMedico.model.dto.catalagoDto;

import java.math.BigDecimal;

import com.uap.controlMedico.model.enums.TipoLugar;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LugarRequest {
    
    @NotBlank(message = "El nombre del lugar es obligatorio")
    @Size(max = 150)
    private String nombre;
 
    @Size(max = 300)
    private String descripcion;
 
    private TipoLugar tipoLugar;
 
    private boolean activo = true;

    private BigDecimal latitudCentro;

    private BigDecimal longitudCentro;

    @Positive(message = "El radio debe ser un valor positivo en metros")
    private Integer radioMetros;

}
