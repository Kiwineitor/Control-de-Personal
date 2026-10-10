package com.uap.controlMedico.model.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uap.controlMedico.exception.BusinessException;
import com.uap.controlMedico.exception.ResourceNotFoundException;
import com.uap.controlMedico.model.dao.RolDao;
import com.uap.controlMedico.model.dto.rol.RolRequest;
import com.uap.controlMedico.model.dto.rol.RolResponse;
import com.uap.controlMedico.model.entity.Rol;
import com.uap.controlMedico.model.enums.RolNombre;

@Service
public class RolServiceImpl implements RolService {

    @Autowired
    private RolDao dao;

    @Override
    public Optional<Rol> findByNombre(RolNombre nombre) {
        return dao.findByNombre(nombre);
    }

    @Override
    public List<Rol> listarRolesPorUsuario(Long idUsuario) {
        return dao.listarRolesPorUsuario(idUsuario);
    }

    @Override
    public List<Rol> findAll() {
        return dao.findAll();
    }

    @Override
    public Rol findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public void save(Rol entidad) {
        dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        Rol rol = dao.findById(idEntidad).orElse(null);
        if (rol != null) {
            rol.setEstado("ELIMINADO");
            dao.save(rol);
        }
    }

    @Override
    public Optional<Rol> buscarRolPorUsuarioYRol(Long idUsuario, Long idRol) {
        return dao.buscarRolPorUsuarioYRol(idUsuario, idRol);
    }

    // ── Lecturas ────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<RolResponse> getAll(String nombre, String estado, Pageable pageable) {
        return dao.findWithFiltersAndCount(
            nombre != null ? nombre : "",   // Decision #4: nunca null
            estado != null ? estado : "",
            pageable
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<RolResponse> getAllActivos() {
        return dao.findAllActivos().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public RolResponse getById(Long id) {
        return toResponse(findEntityById(id));
    }

    // ── Escrituras ──────────────────────────────────────────────────────

    @Override
    public RolResponse crear(RolRequest request) {
        String nombreNormalizado = request.nombre().toUpperCase().trim();

        if (dao.existsByNombreIgnoreCase(nombreNormalizado)) {
            throw new BusinessException(
                "Ya existe un rol con el nombre '" + nombreNormalizado + "'");
        }

        Rol rol = Rol.builder()
                .nombre(nombreNormalizado)
                .estado("ACTIVO")
                .build();

        return toResponse(dao.save(rol));
    }

    @Override
    public RolResponse actualizar(Long id, RolRequest request) {
        Rol rol = findEntityById(id);
        String nombreNormalizado = request.nombre().toUpperCase().trim();

        // Validar unicidad solo si el nombre cambió
        if (!rol.getNombre().equalsIgnoreCase(nombreNormalizado)
                && dao.existsByNombreIgnoreCase(nombreNormalizado)) {
            throw new BusinessException(
                "Ya existe un rol con el nombre '" + nombreNormalizado + "'");
        }

        rol.setNombre(nombreNormalizado);
        return toResponse(dao.save(rol));
    }

    @Override
    public void activar(Long id) {
        Rol rol = findEntityById(id);
        rol.setEstado("ACTIVO");
        dao.save(rol);
    }

    @Override
    public void desactivar(Long id) {
        Rol rol = findEntityById(id);

        // Guardia de negocio: no desactivar si hay usuarios activos con este rol
        long usuariosActivos = rol.getUsuariosRol().stream()
                .filter(ur -> ur.getUsuario().isActivo())
                .count();

        if (usuariosActivos > 0) {
            throw new BusinessException(
                "No se puede desactivar el rol '" + rol.getNombre() +
                "': tiene " + usuariosActivos + " usuario(s) activo(s) asignado(s).");
        }

        rol.setEstado("INACTIVO");
        dao.save(rol);
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private Rol findEntityById(Long id) {
        return dao.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Rol no encontrado con id: " + id));
    }

    private RolResponse toResponse(Rol rol) {
        long cantidad = rol.getUsuariosRol() == null ? 0L : rol.getUsuariosRol().stream()
                .filter(ur -> ur.getUsuario().isActivo())
                .count();
        return new RolResponse(
            rol.getIdRol(),
            rol.getNombre(),
            rol.getEstado(),
            cantidad,
            rol.getFechaCreacion()
        );
    }
    
}
