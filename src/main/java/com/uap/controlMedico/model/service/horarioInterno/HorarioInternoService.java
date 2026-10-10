package com.uap.controlMedico.model.service.horarioInterno;

import java.util.List;

import com.uap.controlMedico.model.dto.horarioInternoDto.HorarioInternoDto;
import com.uap.controlMedico.model.dto.horarioInternoDto.HorarioInternoRequest;

public interface HorarioInternoService {
    HorarioInternoDto guardar(HorarioInternoRequest request);
    HorarioInternoDto findById(Long id);
    List<HorarioInternoDto> findByJornada(Long idJornada);
    List<HorarioInternoDto> findByInternoJornada(Long idInternoJornada);
    void eliminar(Long id);
}
