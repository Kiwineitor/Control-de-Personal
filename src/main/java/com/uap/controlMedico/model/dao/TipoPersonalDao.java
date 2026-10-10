package com.uap.controlMedico.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uap.controlMedico.model.entity.TipoPersonal;

public interface TipoPersonalDao extends JpaRepository<TipoPersonal, Long> {

    List<TipoPersonal> findByActivoTrueOrderByNombreAsc();

    Optional<TipoPersonal> findByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdTipoPersonalNot(String nombre, Long id);
}
