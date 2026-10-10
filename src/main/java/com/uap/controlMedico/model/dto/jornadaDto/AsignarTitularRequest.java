package com.uap.controlMedico.model.dto.jornadaDto;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class AsignarTitularRequest {
    
    @NotEmpty(message = "Debe seleccionar al menos un titular")
    private List<Long> idTitulares;

    private Long idLugar;

    @Size(max = 500)
    private String observaciones;

    /** Override de horario individual por titular: idTitular → {horaInicio, horaFin} */
    private Map<Long, HorarioIndividual> horariosIndividuales;
}
