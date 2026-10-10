package com.uap.controlMedico.model.service.usuario;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uap.controlMedico.exception.BusinessException;
import com.uap.controlMedico.exception.ResourceNotFoundException;
import com.uap.controlMedico.model.dao.PersonaDao;
import com.uap.controlMedico.model.dao.RolDao;
import com.uap.controlMedico.model.dao.UsuarioDao;
import com.uap.controlMedico.model.dao.UsuarioRolDao;
import com.uap.controlMedico.model.dto.usuarioDto.CambiarPasswordRequest;
import com.uap.controlMedico.model.dto.usuarioDto.LoginRequest;
import com.uap.controlMedico.model.dto.usuarioDto.LoginResponse;
import com.uap.controlMedico.model.dto.usuarioDto.ResetPasswordRequest;
import com.uap.controlMedico.model.dto.usuarioDto.TokenResetResponse;
import com.uap.controlMedico.model.dto.usuarioDto.UsuarioRequest;
import com.uap.controlMedico.model.dto.usuarioDto.UsuarioResponse;
import com.uap.controlMedico.model.dto.usuarioDto.UsuarioSesion;
import com.uap.controlMedico.model.entity.Persona;
import com.uap.controlMedico.model.entity.Rol;
import com.uap.controlMedico.model.entity.Usuario;
import com.uap.controlMedico.model.entity.UsuarioRol;
import com.uap.controlMedico.model.service.SisuMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

        private final UsuarioDao      usuarioDao;
    private final UsuarioRolDao   usuarioRolDao;
    private final RolDao          rolDao;
    private final PersonaDao      personaDao;
    private final PasswordEncoder passwordEncoder;
    private final SisuMapper      mapper;
 
    // ── CRUD ───────────────────────────────────────────────────────────
 
    @Override
    @Transactional
    public UsuarioResponse crear(UsuarioRequest request) {
        if (usuarioDao.existsByUsername(request.getUsername()))
            throw new BusinessException(
                "El username '" + request.getUsername() + "' ya está en uso");

        Persona persona = resolverPersona(request, null);

        String nombre   = persona != null ? persona.getNombre()   : request.getNombre();
        String apellido = persona != null ? persona.getApellido() : request.getApellido();

        if (nombre == null || nombre.isBlank())
            throw new BusinessException("El nombre es obligatorio");
        if (apellido == null || apellido.isBlank())
            throw new BusinessException("El apellido es obligatorio");

        Usuario usuario = Usuario.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .nombre(nombre)
                .apellido(apellido)
                .activo(request.isActivo())
                .perId(request.getPerId())
                .persona(persona)
                .build();

        usuario = usuarioDao.save(usuario);
        asignarRoles(usuario, request);

        return mapper.toUsuarioResponse(
            usuarioDao.findById(usuario.getIdUsuario()).orElseThrow());
    }

    @Override
    @Transactional
    public UsuarioResponse actualizar(Long id, UsuarioRequest request) {
        Usuario usuario = buscarEntidadPorId(id);

        usuarioDao.findByUsername(request.getUsername()).ifPresent(u -> {
            if (!u.getIdUsuario().equals(id))
                throw new BusinessException("El username ya está en uso");
        });

        Persona persona = resolverPersona(request, usuario.getPersona());

        if (persona != null) {
            usuario.setPersona(persona);
            usuario.setNombre(persona.getNombre());
            usuario.setApellido(persona.getApellido());
        } else {
            if (request.getNombre() != null) usuario.setNombre(request.getNombre());
            if (request.getApellido() != null) usuario.setApellido(request.getApellido());
        }

        usuario.setActivo(request.isActivo());

        if (request.getPassword() != null && !request.getPassword().isBlank())
            usuario.setPassword(passwordEncoder.encode(request.getPassword()));

        usuarioDao.save(usuario);
        usuarioRolDao.deleteByUsuarioId(id);
        asignarRoles(usuario, request);

        return mapper.toUsuarioResponse(usuarioDao.findById(id).orElseThrow());
    }
 
    @Override
    @Transactional(readOnly = true)
    public UsuarioResponse buscarPorId(Long id) {
        return mapper.toUsuarioResponse(buscarEntidadPorId(id));
    }
 
    @Override
    @Transactional(readOnly = true)
    public UsuarioResponse buscarPorUsername(String username) {
        return mapper.toUsuarioResponse(
            usuarioDao.findByUsername(username)
                .orElseThrow(() ->
                    new ResourceNotFoundException("Usuario", "username", username)));
    }
 
    @Override
    @Transactional(readOnly = true)
    public List<UsuarioResponse> listarActivos() {
        return usuarioDao.findByActivoTrue().stream()
                .map(mapper::toUsuarioResponse)
                .collect(Collectors.toList());
    }
 
    @Override
    @Transactional(readOnly = true)
    public List<UsuarioResponse> buscarPorTermino(String termino) {
        return usuarioDao.buscarPorTermino(termino).stream()
                .map(mapper::toUsuarioResponse)
                .collect(Collectors.toList());
    }
 
    @Override
    @Transactional
    public void desactivar(Long id) {
        Usuario u = buscarEntidadPorId(id);
        u.setActivo(false);
        usuarioDao.save(u);
    }
 
    @Override
    @Transactional
    public void activar(Long id) {
        Usuario u = buscarEntidadPorId(id);
        u.setActivo(true);
        usuarioDao.save(u);
    }
 
    // ── Login REST ─────────────────────────────────────────────────────
 
    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        Usuario usuario = usuarioDao.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException("Credenciales incorrectas"));

        if (!usuario.isActivo())
            throw new BusinessException("La cuenta está desactivada");

        if (!passwordEncoder.matches(request.getPassword(), usuario.getPassword()))
            throw new BusinessException("Credenciales incorrectas");

        usuarioDao.actualizarUltimoAcceso(usuario.getIdUsuario(), LocalDateTime.now());

        // findNombresRolByUsuarioId ya está en usuarioDao y retorna List<String>
        List<String> roles = usuarioDao.findNombresRolByUsuarioId(usuario.getIdUsuario());

        return LoginResponse.builder()
                .idUsuario(usuario.getIdUsuario())
                .username(usuario.getUsername())
                .nombre(usuario.getNombre())
                .apellido(usuario.getApellido())
                .roles(roles)           // ← List<String> directo, sin conversión
                .tipo("Bearer")
                .build();
    }
 
    // ── Sesión única ───────────────────────────────────────────────────

    @Override
    @Transactional
    public String abrirSesionUnica(Long idUsuario) {
        String sesionId = UUID.randomUUID().toString();
        usuarioDao.actualizarSesionActual(idUsuario, sesionId);
        return sesionId;
    }

    @Override
    @Transactional
    public void cerrarSesionUnica(String username) {
        usuarioDao.findByUsername(username).ifPresent(u ->
            usuarioDao.actualizarSesionActual(u.getIdUsuario(), null));
    }

    // ── Login Web — carga sesión ───────────────────────────────────────
 
    /**
     * FIX: método @Transactional llamado desde SisuAuthSuccessHandler.
     * El @Modifying de actualizarUltimoAcceso REQUIERE transacción activa.
     * Al correr aquí, Spring gestiona la transacción correctamente.
     */
    @Override
    @Transactional
    public UsuarioSesion registrarLoginExitoso(String username) {
        return usuarioDao.findByUsername(username)
                .map(usuario -> {

                    usuarioDao.actualizarUltimoAcceso(
                        usuario.getIdUsuario(), LocalDateTime.now());

                    // ANTES: usuarioRolDao.findNombres...stream().map(r -> r.name().replace(...))
                    // AHORA: ya es List<String>, sin .name() ni .replace()
                    // Los valores en BD son "ADMIN", "SUPERVISOR"... sin prefijo ROLE_
                    List<String> roles = usuarioDao
                            .findNombresRolByUsuarioId(usuario.getIdUsuario());

                    return UsuarioSesion.builder()
                            .idUsuario(usuario.getIdUsuario())
                            .username(usuario.getUsername())
                            .nombre(usuario.getNombre())
                            .apellido(usuario.getApellido())
                            .email(usuario.getEmail())
                            .rolPrincipal(roles.isEmpty() ? "USUARIO" : roles.get(0))
                            .roles(roles)
                            .build();
                })
                .orElse(null);
    }
 
    // ── Helpers privados ───────────────────────────────────────────────
 
    /**
     * Resuelve la Persona a vincular según el request:
     * - Si viene idPersona → busca la existente y valida que no tenga otro usuario.
     * - Si viene personaNueva → crea la Persona en BD.
     * - Si ninguno → retorna la persona actual (para edición) o null (admin puro).
     */
    private Persona resolverPersona(UsuarioRequest request, Persona personaActual) {
        if (request.getIdPersona() != null) {
            Persona p = personaDao.findById(request.getIdPersona())
                    .orElseThrow(() -> new ResourceNotFoundException("Persona", request.getIdPersona()));
            if (personaActual == null || !p.getIdPersona().equals(personaActual.getIdPersona())) {
                if (usuarioDao.existsByPersonaIdPersona(p.getIdPersona()))
                    throw new BusinessException("Esta persona ya tiene un usuario asignado");
            }
            return p;
        }
        if (request.getPersonaNueva() != null) {
            var req = request.getPersonaNueva();
            if (req.getCi() != null && !req.getCi().isBlank() && personaDao.existsByCi(req.getCi()))
                throw new BusinessException("Ya existe una persona con CI: " + req.getCi());
            Persona nueva = Persona.builder()
                    .nombre(req.getNombre())
                    .apellido(req.getApellido())
                    .ci(req.getCi())
                    .email(req.getEmail())
                    .telefono(req.getTelefono())
                    .build();
            return personaDao.save(nueva);
        }
        return personaActual;
    }

    private Usuario buscarEntidadPorId(Long id) {
        return usuarioDao.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
    }
 
    private void asignarRoles(Usuario usuario, UsuarioRequest request) {
        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            // flujo original (compatibilidad hacia atrás)
            request.getRoles().forEach(rolNombre -> {
                Rol rol = rolDao.findByNombre(rolNombre)
                        .orElseThrow(() ->
                            new ResourceNotFoundException("Rol", "nombre", rolNombre));
                usuarioRolDao.save(UsuarioRol.builder()
                        .usuario(usuario).rol(rol).build());
            });
        } else if (request.getIdRol() != null) {
            // flujo nuevo — frontend envía idRol (Long)
            Rol rol = rolDao.findById(request.getIdRol())
                    .orElseThrow(() ->
                        new ResourceNotFoundException("Rol", request.getIdRol()));
            usuarioRolDao.save(UsuarioRol.builder()
                    .usuario(usuario).rol(rol).build());
        }
    }

    // ── Paginado para el grid admin ────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<UsuarioResponse> getAll(String termino, String rolNombre,
                                        Boolean activo, Pageable pageable) {
        return usuarioDao.findWithFilters(
            termino   != null ? termino   : "",
            rolNombre != null ? rolNombre : "",
            activo,
            pageable
        ).map(mapper::toUsuarioResponse);   // usa SisuMapper ya existente
    }

    // ── Password reset — Admin genera token ───────────────────────────────

    @Override
    @Transactional
    public TokenResetResponse generarTokenReset(Long id) {
        Usuario u      = buscarEntidadPorId(id);
        String token   = UUID.randomUUID().toString();
        LocalDateTime expiry = LocalDateTime.now().plusHours(24);

        u.setTokenReset(token);
        u.setTokenResetExpiry(expiry);
        u.setRequiereReset(true);
        usuarioDao.save(u);

        return TokenResetResponse.builder()
                .token(token)
                .expiry(expiry)
                .mensaje("Token válido 24h. Entrégalo a: "
                    + u.getNombre() + " " + u.getApellido())
                .build();
    }

    // ── Forzar cambio en próximo login ────────────────────────────────────

    @Override
    @Transactional
    public void forzarCambioPassword(Long id) {
        Usuario u = buscarEntidadPorId(id);
        u.setRequiereReset(true);
        usuarioDao.save(u);
    }

    // ── Reset con token (endpoint público) ────────────────────────────────

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.getNuevaPassword().equals(request.getConfirmarPassword()))
            throw new BusinessException("Las contraseñas no coinciden");

        Usuario u = usuarioDao.findByTokenResetValido(request.getToken())
                .orElseThrow(() -> new BusinessException(
                    "Token inválido o expirado. Solicita uno nuevo al administrador."));

        u.setPassword(passwordEncoder.encode(request.getNuevaPassword()));
        u.setTokenReset(null);
        u.setTokenResetExpiry(null);
        u.setRequiereReset(false);
        usuarioDao.save(u);
    }

    // ── Cambiar propia contraseña (usuario autenticado) ───────────────────

    @Override
    @Transactional
    public void cambiarPassword(String username, CambiarPasswordRequest request) {
        if (!request.getNuevaPassword().equals(request.getConfirmarPassword()))
            throw new BusinessException("Las contraseñas no coinciden");

        Usuario u = usuarioDao.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", "username", username));

        if (!passwordEncoder.matches(request.getPasswordActual(), u.getPassword()))
            throw new BusinessException("La contraseña actual es incorrecta");

        if (passwordEncoder.matches(request.getNuevaPassword(), u.getPassword()))
            throw new BusinessException(
                "La nueva contraseña no puede ser igual a la actual");

        u.setPassword(passwordEncoder.encode(request.getNuevaPassword()));
        u.setRequiereReset(false);
        usuarioDao.save(u);
    }
}