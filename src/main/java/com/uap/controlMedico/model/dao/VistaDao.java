package com.uap.controlMedico.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uap.controlMedico.model.entity.Vista;

public interface VistaDao extends JpaRepository<Vista, Long> {
    Optional<Vista> findByCodigo(String codigo);
    boolean existsByCodigo(String codigo);
    List<Vista> findByActivoTrueOrderByOrdenAsc();
}
