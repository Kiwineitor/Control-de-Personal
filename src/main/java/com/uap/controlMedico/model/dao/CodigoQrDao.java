package com.uap.controlMedico.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uap.controlMedico.model.entity.CodigoQr;
import com.uap.controlMedico.model.entity.Lugar;

public interface CodigoQrDao extends JpaRepository<CodigoQr, Long> {
 
    Optional<CodigoQr> findByCodigo(String codigo);

    Optional<CodigoQr> findByCodigoAndActivoTrue(String codigo);
 
    boolean existsByCodigo(String codigo);

    boolean existsByLugar_IdLugar(Long lugar);

    Optional<CodigoQr> findByLugar_IdLugar(Long idLugar);
    List<CodigoQr>     findAllByOrderByLugar_NombreAsc();
    List<CodigoQr>     findByActivoTrue();
}