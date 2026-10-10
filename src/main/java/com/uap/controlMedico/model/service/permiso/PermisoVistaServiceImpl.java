package com.uap.controlMedico.model.service.permiso;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uap.controlMedico.model.dao.RolDao;
import com.uap.controlMedico.model.dao.RolVistaDao;
import com.uap.controlMedico.model.dao.UsuarioDao;
import com.uap.controlMedico.model.dao.UsuarioVistaDao;
import com.uap.controlMedico.model.dao.VistaDao;
import com.uap.controlMedico.model.dto.permisovista.OverrideVistaDto;
import com.uap.controlMedico.model.dto.permisovista.UsuarioVistasResumen;
import com.uap.controlMedico.model.entity.Rol;
import com.uap.controlMedico.model.entity.RolVista;
import com.uap.controlMedico.model.entity.Usuario;
import com.uap.controlMedico.model.entity.UsuarioVista;
import com.uap.controlMedico.model.entity.Vista;
import com.uap.controlMedico.model.enums.TipoOverrideVista;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PermisoVistaServiceImpl implements PermisoVistaService {

    /** Nombre del rol super-usuario (Rol.nombre = RolNombre.name()). */
    private static final String ROL_ADMIN    = "ADMIN";
    /** Vista base "pública" que siempre ve todo usuario. */
    private static final String VISTA_INICIO = "INICIO";

    private final UsuarioDao       usuarioDao;
    private final VistaDao         vistaDao;
    private final RolDao           rolDao;
    private final RolVistaDao      rolVistaDao;
    private final UsuarioVistaDao  usuarioVistaDao;

    // ── Cálculo de efectivas ──────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Set<String> getVistasEfectivas(String username) {
        Usuario u = usuarioDao.findByUsername(username).orElse(null);
        return u == null ? Set.of() : calcularEfectivas(u);
    }

    private Set<String> calcularEfectivas(Usuario u) {
        List<String> roles = u.getUsuariosRol().stream()
            .map(ur -> ur.getRol() != null ? ur.getRol().getNombre() : null)
            .filter(Objects::nonNull)
            .toList();

        // ADMIN = super-usuario: todas las vistas activas (nunca se le bloquea).
        if (roles.stream().anyMatch(ROL_ADMIN::equalsIgnoreCase)) {
            Set<String> todas = vistaDao.findByActivoTrueOrderByOrdenAsc().stream()
                .map(Vista::getCodigo)
                .collect(Collectors.toCollection(HashSet::new));
            todas.add(VISTA_INICIO);
            return todas;
        }

        // Base = unión de las vistas de sus roles.
        Set<String> efectivas = new HashSet<>();
        for (String rol : roles) {
            efectivas.addAll(rolVistaDao.findCodigosByRolNombre(rol));
        }

        // Overrides por usuario: GRANT agrega, DENY quita.
        for (UsuarioVista uv : usuarioVistaDao.findByUsuario_IdUsuario(u.getIdUsuario())) {
            if (uv.getVista() == null || !uv.getVista().isActivo()) continue;
            String codigo = uv.getVista().getCodigo();
            if (uv.getTipo() == TipoOverrideVista.GRANT)      efectivas.add(codigo);
            else if (uv.getTipo() == TipoOverrideVista.DENY)  efectivas.remove(codigo);
        }

        efectivas.add(VISTA_INICIO);   // "vista pública" por defecto
        return efectivas;
    }

    // ── Administración ────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<Vista> getCatalogo() {
        return vistaDao.findByActivoTrueOrderByOrdenAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getCodigosDeRol(Long idRol) {
        return rolVistaDao.findByRol_IdRol(idRol).stream()
            .filter(rv -> rv.getVista() != null)
            .map(rv -> rv.getVista().getCodigo())
            .toList();
    }

    @Override
    @Transactional
    public void setVistasDeRol(Long idRol, List<String> codigos) {
        Rol rol = rolDao.findById(idRol)
            .orElseThrow(() -> new EntityNotFoundException("Rol no encontrado: " + idRol));
        Set<String> objetivo = new HashSet<>(codigos != null ? codigos : List.of());

        // Diff (evita borrar-todo + reinsertar, que chocaría con el unique).
        Set<String> actuales = new HashSet<>();
        for (RolVista rv : rolVistaDao.findByRol_IdRol(idRol)) {
            String cod = rv.getVista() != null ? rv.getVista().getCodigo() : null;
            if (cod == null || !objetivo.contains(cod)) {
                rolVistaDao.delete(rv);              // sobra → quitar
            } else {
                actuales.add(cod);
            }
        }
        for (String cod : objetivo) {
            if (!actuales.contains(cod)) {
                vistaDao.findByCodigo(cod).ifPresent(v ->
                    rolVistaDao.save(RolVista.builder().rol(rol).vista(v).build()));
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioVistasResumen getResumenUsuario(Long idUsuario) {
        Usuario u = usuarioDao.findById(idUsuario)
            .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + idUsuario));

        Set<String> base = new LinkedHashSet<>();
        for (var ur : u.getUsuariosRol()) {
            if (ur.getRol() != null) base.addAll(rolVistaDao.findCodigosByRolNombre(ur.getRol().getNombre()));
        }

        List<OverrideVistaDto> overrides = usuarioVistaDao.findByUsuario_IdUsuario(idUsuario).stream()
            .filter(uv -> uv.getVista() != null)
            .map(uv -> new OverrideVistaDto(uv.getVista().getCodigo(), uv.getTipo().name()))
            .toList();

        return UsuarioVistasResumen.builder()
            .baseRol(new ArrayList<>(base))
            .overrides(overrides)
            .efectivas(new ArrayList<>(calcularEfectivas(u)))
            .build();
    }

    @Override
    @Transactional
    public void setOverridesDeUsuario(Long idUsuario, List<OverrideVistaDto> overrides) {
        Usuario u = usuarioDao.findById(idUsuario)
            .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + idUsuario));

        // Objetivo: codigo → tipo (ignora entradas inválidas).
        Map<String, TipoOverrideVista> objetivo = new HashMap<>();
        if (overrides != null) {
            for (OverrideVistaDto o : overrides) {
                if (o.getCodigo() == null || o.getTipo() == null) continue;
                try {
                    objetivo.put(o.getCodigo(), TipoOverrideVista.valueOf(o.getTipo().trim().toUpperCase()));
                } catch (IllegalArgumentException ignore) { /* tipo inválido → omitir */ }
            }
        }

        Set<String> vistos = new HashSet<>();
        for (UsuarioVista uv : usuarioVistaDao.findByUsuario_IdUsuario(idUsuario)) {
            String cod = uv.getVista() != null ? uv.getVista().getCodigo() : null;
            if (cod == null || !objetivo.containsKey(cod)) {
                usuarioVistaDao.delete(uv);
            } else {
                uv.setTipo(objetivo.get(cod));       // actualizar tipo si cambió
                usuarioVistaDao.save(uv);
                vistos.add(cod);
            }
        }
        for (Map.Entry<String, TipoOverrideVista> e : objetivo.entrySet()) {
            if (!vistos.contains(e.getKey())) {
                vistaDao.findByCodigo(e.getKey()).ifPresent(v ->
                    usuarioVistaDao.save(UsuarioVista.builder()
                        .usuario(u).vista(v).tipo(e.getValue()).build()));
            }
        }
    }
}
