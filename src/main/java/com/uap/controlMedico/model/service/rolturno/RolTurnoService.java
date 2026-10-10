package com.uap.controlMedico.model.service.rolturno;

import java.util.List;

import com.uap.controlMedico.model.dto.monitoreo.PersonalEnTurnoDto;
import com.uap.controlMedico.model.dto.rolturnoDto.RolTurnoDto;
import com.uap.controlMedico.model.dto.rolturnoDto.RolTurnoRequest;

public interface RolTurnoService {
    RolTurnoDto guardar(RolTurnoRequest request);
    RolTurnoDto findById(Long id);
    List<RolTurnoDto> findByJornada(Long idJornada);
    List<RolTurnoDto> findByTitularJornada(Long idTitularJornada);
    void eliminar(Long id);
    /** Retorna todo el personal (titulares + internos) con turno activo en este momento. */
    List<PersonalEnTurnoDto> getPersonalEnTurnoAhora();
}
