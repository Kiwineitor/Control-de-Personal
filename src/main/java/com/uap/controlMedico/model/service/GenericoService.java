package com.uap.controlMedico.model.service;

import java.util.List;

public interface GenericoService <T, K>{
    List<T> findAll();

    T findById(K idEntidad);

    void save(T entidad);

    void deleteById(K idEntidad);
}
