package com.uap.controlMedico.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uap.controlMedico.model.entity.DiaHorario;

public interface DiaHorarioDao extends JpaRepository<DiaHorario, Long> {

    List<DiaHorario> findByHorarioInterno_IdHorarioInternoOrderByFechaAsc(Long idHorarioInterno);

    void deleteByHorarioInterno_IdHorarioInterno(Long idHorarioInterno);
}
