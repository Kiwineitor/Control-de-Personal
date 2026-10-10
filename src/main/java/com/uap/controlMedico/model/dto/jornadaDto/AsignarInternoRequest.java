package com.uap.controlMedico.model.dto.jornadaDto;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class AsignarInternoRequest {
    
    @NotEmpty(message = "Debe seleccionar al menos un interno")
    private List<Long> idInternos;

    private Long idLugar;

    @Size(max = 500)
    private String observaciones;

    /** Override de horario individual por interno: idInterno → {horaInicio, horaFin} */
    private Map<Long, HorarioIndividual> horariosIndividuales;
}
