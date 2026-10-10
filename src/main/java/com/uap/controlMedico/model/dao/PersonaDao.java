package com.uap.controlMedico.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uap.controlMedico.model.entity.Persona;

public interface PersonaDao extends JpaRepository<Persona, Long> {

    Optional<Persona> findByCi(String ci);
    boolean existsByCi(String ci);

    /** Personas que aún no tienen un Usuario asignado. */
    @Query("""
        SELECT p FROM Persona p
        WHERE NOT EXISTS (
            SELECT u FROM Usuario u WHERE u.persona = p
        )
        ORDER BY p.apellido, p.nombre
        """)
    List<Persona> findSinUsuario();

    /** Todas las personas con su usuario asociado (null si no tiene). */
    @Query("""
        SELECT p, u FROM Persona p
        LEFT JOIN Usuario u ON u.persona = p
        ORDER BY p.apellido, p.nombre
        """)
    List<Object[]> findAllConUsuario();

    /** Búsqueda libre por nombre, apellido o CI — para selector en formularios. */
    @Query("""
        SELECT p FROM Persona p
        WHERE :q = ''
           OR LOWER(p.nombre)   LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(p.apellido) LIKE LOWER(CONCAT('%', :q, '%'))
           OR p.ci LIKE CONCAT('%', :q, '%')
        ORDER BY p.apellido, p.nombre
        """)
    List<Persona> buscarPorTermino(@Param("q") String q, Pageable pageable);
}
