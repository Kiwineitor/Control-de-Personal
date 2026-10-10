package com.uap.controlMedico.model.service.personal;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.uap.controlMedico.model.dto.personalDto.PersonalHospitalDto;
import com.uap.controlMedico.model.dto.personalDto.RegistrarPersonalRequest;
import com.uap.controlMedico.model.dto.personalDto.TipoPersonalDto;
import com.uap.controlMedico.model.dto.personalDto.TipoPersonalRequest;

public interface PersonalService {

    // ── TipoPersonal CRUD ──────────────────────────────────────────────
    List<TipoPersonalDto>         listarTipos();
    TipoPersonalDto               findTipoById(Long id);
    TipoPersonalDto               crearTipo(TipoPersonalRequest request);
    TipoPersonalDto               actualizarTipo(Long id, TipoPersonalRequest request);
    void                          toggleTipo(Long id);

    // ── Personal (Titular + Persona) ───────────────────────────────────
    Page<PersonalHospitalDto>     listarPersonal(String q, Long idTipoPersonal,
                                                  Boolean activo, Pageable pageable);
    PersonalHospitalDto           findPersonalById(Long idTitular);
    PersonalHospitalDto           registrar(RegistrarPersonalRequest request);
    PersonalHospitalDto           actualizar(Long idTitular, RegistrarPersonalRequest request);
    void                          togglePersonal(Long idTitular);
}
