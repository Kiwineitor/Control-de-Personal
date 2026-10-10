package com.uap.controlMedico.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.uap.controlMedico.model.entity.UsuarioVista;

public interface UsuarioVistaDao extends JpaRepository<UsuarioVista, Long> {
    List<UsuarioVista> findByUsuario_IdUsuario(Long idUsuario);
    List<UsuarioVista> findByUsuario_Username(String username);
    void deleteByUsuario_IdUsuario(Long idUsuario);
}
