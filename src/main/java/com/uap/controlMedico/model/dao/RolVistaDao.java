package com.uap.controlMedico.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uap.controlMedico.model.entity.RolVista;

public interface RolVistaDao extends JpaRepository<RolVista, Long> {

    List<RolVista> findByRol_IdRol(Long idRol);
    boolean existsByRol_IdRol(Long idRol);
    void deleteByRol_IdRol(Long idRol);

    /** Códigos de vista base de un rol (por nombre de rol). */
    @Query("""
        SELECT rv.vista.codigo FROM RolVista rv
        WHERE rv.rol.nombre = :rolNombre AND rv.vista.activo = true
        """)
    List<String> findCodigosByRolNombre(@Param("rolNombre") String rolNombre);
}
