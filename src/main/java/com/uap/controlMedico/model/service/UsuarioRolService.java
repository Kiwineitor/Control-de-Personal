package com.uap.controlMedico.model.service;

import com.uap.controlMedico.model.entity.UsuarioRol;

public interface UsuarioRolService extends GenericoService <UsuarioRol, Long>{
    UsuarioRol buscarPorIdUsuarioYRol(Long idUsuario, Long idRol);
}
