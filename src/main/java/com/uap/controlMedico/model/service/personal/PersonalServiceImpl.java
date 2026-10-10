package com.uap.controlMedico.model.service.personal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uap.controlMedico.model.dao.LugarDao;
import com.uap.controlMedico.model.dao.PersonaDao;
import com.uap.controlMedico.model.dao.RolDao;
import com.uap.controlMedico.model.dao.TipoPersonalDao;
import com.uap.controlMedico.model.dao.TitularDao;
import com.uap.controlMedico.model.dao.UsuarioDao;
import com.uap.controlMedico.model.dao.UsuarioRolDao;
import com.uap.controlMedico.model.dto.personalDto.PersonalHospitalDto;
import com.uap.controlMedico.model.dto.personalDto.RegistrarPersonalRequest;
import com.uap.controlMedico.model.dto.personalDto.TipoPersonalDto;
import com.uap.controlMedico.model.dto.personalDto.TipoPersonalRequest;
import com.uap.controlMedico.model.entity.Lugar;
import com.uap.controlMedico.model.entity.Persona;
import com.uap.controlMedico.model.entity.Rol;
import com.uap.controlMedico.model.entity.TipoPersonal;
import com.uap.controlMedico.model.entity.Titular;
import com.uap.controlMedico.model.entity.Usuario;
import com.uap.controlMedico.model.entity.UsuarioRol;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PersonalServiceImpl implements PersonalService {

    private final TipoPersonalDao  tipoPersonalDao;
    private final TitularDao       titularDao;
    private final PersonaDao       personaDao;
    private final UsuarioDao       usuarioDao;
    private final UsuarioRolDao    usuarioRolDao;
    private final RolDao           rolDao;
    private final LugarDao         lugarDao;
    private final PasswordEncoder  passwordEncoder;

    // ══ TipoPersonal CRUD ══════════════════════════════════════════════

    @Override
    public List<TipoPersonalDto> listarTipos() {
        return tipoPersonalDao.findAll().stream().map(this::toTipoDto).toList();
    }

    @Override
    public TipoPersonalDto findTipoById(Long id) {
        return toTipoDto(findTipoOrThrow(id));
    }

    @Override
    @Transactional
    public TipoPersonalDto crearTipo(TipoPersonalRequest request) {
        if (tipoPersonalDao.findByNombreIgnoreCase(request.getNombre()).isPresent())
            throw new IllegalArgumentException("Ya existe un tipo con el nombre: " + request.getNombre());
        TipoPersonal tipo = TipoPersonal.builder()
            .nombre(request.getNombre().toUpperCase().trim())
            .descripcion(request.getDescripcion())
            .color(request.getColor() != null ? request.getColor() : "#64748b")
            .esPersonalMedico(request.isEsPersonalMedico())
            .puedeGestionarInternos(request.isPuedeGestionarInternos())
            .activo(request.isActivo())
            .build();
        return toTipoDto(tipoPersonalDao.save(tipo));
    }

    @Override
    @Transactional
    public TipoPersonalDto actualizarTipo(Long id, TipoPersonalRequest request) {
        TipoPersonal tipo = findTipoOrThrow(id);
        if (tipoPersonalDao.existsByNombreIgnoreCaseAndIdTipoPersonalNot(request.getNombre(), id))
            throw new IllegalArgumentException("Ya existe un tipo con el nombre: " + request.getNombre());
        tipo.setNombre(request.getNombre().toUpperCase().trim());
        tipo.setDescripcion(request.getDescripcion());
        if (request.getColor() != null) tipo.setColor(request.getColor());
        tipo.setEsPersonalMedico(request.isEsPersonalMedico());
        tipo.setPuedeGestionarInternos(request.isPuedeGestionarInternos());
        tipo.setActivo(request.isActivo());
        return toTipoDto(tipoPersonalDao.save(tipo));
    }

    @Override
    @Transactional
    public void toggleTipo(Long id) {
        TipoPersonal tipo = findTipoOrThrow(id);
        tipo.setActivo(!tipo.isActivo());
        tipoPersonalDao.save(tipo);
    }

    // ══ Personal (Titular + Persona) ═══════════════════════════════════

    @Override
    public Page<PersonalHospitalDto> listarPersonal(String q, Long idTipoPersonal,
                                                     Boolean activo, Pageable pageable) {
        return titularDao.findPersonalConFiltros(
            q != null ? q.toLowerCase() : "", idTipoPersonal, activo, pageable
        ).map(this::toPersonalDto);
    }

    @Override
    public PersonalHospitalDto findPersonalById(Long idTitular) {
        return toPersonalDto(titularDao.findById(idTitular)
            .orElseThrow(() -> new EntityNotFoundException("Titular id=" + idTitular)));
    }

    @Override
    @Transactional
    public PersonalHospitalDto registrar(RegistrarPersonalRequest request) {
        TipoPersonal tipo = findTipoOrThrow(request.getIdTipoPersonal());

        // Crear o usar persona existente (por CI)
        Persona persona;
        if (request.getCi() != null && !request.getCi().isBlank()) {
            persona = personaDao.findByCi(request.getCi()).orElseGet(() ->
                personaDao.save(buildPersona(request)));
        } else {
            persona = personaDao.save(buildPersona(request));
        }

        Lugar lugar = request.getIdLugar() != null
            ? lugarDao.findById(request.getIdLugar()).orElse(null) : null;

        Titular titular = Titular.builder()
            .persona(persona)
            .tipoPersonal(tipo)
            .perId(persona.getIdPersona())
            .contratoId(persona.getIdPersona())   // para registros internos SISU, usar idPersona
            .cargo(request.getCargo() != null ? request.getCargo() : tipo.getNombre())
            .especialidad(request.getEspecialidad())
            .activo(true)
            .build();
        titular = titularDao.save(titular);

        // Crear usuario si se solicitó
        if (request.isCrearUsuario()) {
            crearUsuarioParaTitular(titular, persona, tipo, lugar, request);
        }

        log.info("Personal registrado: {} {} (tipo={})",
            persona.getNombre(), persona.getApellido(), tipo.getNombre());
        return toPersonalDto(titular);
    }

    @Override
    @Transactional
    public PersonalHospitalDto actualizar(Long idTitular, RegistrarPersonalRequest request) {
        Titular titular = titularDao.findById(idTitular)
            .orElseThrow(() -> new EntityNotFoundException("Titular id=" + idTitular));

        Persona persona = titular.getPersona();
        persona.setNombre(request.getNombre());
        persona.setApellido(request.getApellido());
        if (request.getCi()       != null) persona.setCi(request.getCi());
        if (request.getEmail()    != null) persona.setEmail(request.getEmail());
        if (request.getTelefono() != null) persona.setTelefono(request.getTelefono());
        personaDao.save(persona);

        if (request.getIdTipoPersonal() != null)
            titular.setTipoPersonal(findTipoOrThrow(request.getIdTipoPersonal()));
        if (request.getCargo() != null) titular.setCargo(request.getCargo());
        if (request.getEspecialidad() != null) titular.setEspecialidad(request.getEspecialidad());

        return toPersonalDto(titularDao.save(titular));
    }

    @Override
    @Transactional
    public void togglePersonal(Long idTitular) {
        Titular t = titularDao.findById(idTitular)
            .orElseThrow(() -> new EntityNotFoundException("Titular id=" + idTitular));
        t.setActivo(!t.isActivo());
        titularDao.save(t);
    }

    // ── Helpers privados ──────────────────────────────────────────────

    private TipoPersonal findTipoOrThrow(Long id) {
        return tipoPersonalDao.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("TipoPersonal id=" + id));
    }

    private Persona buildPersona(RegistrarPersonalRequest r) {
        // Persona extends AuditoriaConfig with @SuperBuilder — usar reflexión manual
        Persona p = new Persona();
        p.setNombre(r.getNombre());
        p.setApellido(r.getApellido());
        p.setCi(r.getCi());
        p.setEmail(r.getEmail());
        p.setTelefono(r.getTelefono());
        return p;
    }

    private void crearUsuarioParaTitular(Titular titular, Persona persona,
                                          TipoPersonal tipo, Lugar lugar,
                                          RegistrarPersonalRequest request) {
        Long idPersona = persona.getIdPersona();
        if (usuarioDao.existsByPersonaIdPersona(idPersona)) {
            log.warn("Persona id={} ya tiene usuario, se omite creación", idPersona);
            return;
        }

        String username = request.getUsername() != null && !request.getUsername().isBlank()
            ? request.getUsername()
            : generarUsername(persona);
        String rawPwd = request.getPassword() != null && !request.getPassword().isBlank()
            ? request.getPassword()
            : UUID.randomUUID().toString().substring(0, 8);

        // Asegurarse de unicidad del username
        String baseUsername = username;
        int suffix = 1;
        while (usuarioDao.existsByUsername(username)) {
            username = baseUsername + suffix++;
        }

        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setPassword(passwordEncoder.encode(rawPwd));
        usuario.setNombre(persona.getNombre());
        usuario.setApellido(persona.getApellido());
        usuario.setEmail(persona.getEmail());
        usuario.setPersona(persona);
        usuario.setPerId(idPersona);
        usuario.setActivo(true);
        usuario.setRequiereReset(true);  // forzar cambio de contraseña al primer login
        usuario = usuarioDao.save(usuario);

        // Asignar rol según idRol o inferir del tipoPersonal
        Rol rol = resolverRol(request.getIdRol(), tipo);
        if (rol != null) {
            UsuarioRol ur = new UsuarioRol();
            ur.setUsuario(usuario);
            ur.setRol(rol);
            usuarioRolDao.save(ur);
        }

        log.info("Usuario creado: {} (raw_pwd omitida por seguridad)", username);
    }

    private String generarUsername(Persona p) {
        String base = (p.getNombre() != null ? p.getNombre().substring(0, 1) : "u")
            + (p.getApellido() != null ? p.getApellido().replaceAll("\\s+", "").toLowerCase() : "user");
        return base.replaceAll("[^a-z0-9]", "").toLowerCase();
    }

    private Rol resolverRol(Long idRol, TipoPersonal tipo) {
        if (idRol != null) return rolDao.findById(idRol).orElse(null);
        // Inferir rol por nombre del tipo
        String nombreRol = "ROLE_" + tipo.getNombre().toUpperCase().replaceAll("\\s+", "_");
        return rolDao.findByNombreIgnoreCase(nombreRol).orElse(null);
    }

    // ── Mappers ──────────────────────────────────────────────────────

    private TipoPersonalDto toTipoDto(TipoPersonal tp) {
        int total = (int) titularDao.findAll().stream()
            .filter(t -> t.getTipoPersonal() != null
                      && t.getTipoPersonal().getIdTipoPersonal().equals(tp.getIdTipoPersonal()))
            .count();
        return TipoPersonalDto.builder()
            .idTipoPersonal(tp.getIdTipoPersonal())
            .nombre(tp.getNombre())
            .descripcion(tp.getDescripcion())
            .color(tp.getColor())
            .esPersonalMedico(tp.isEsPersonalMedico())
            .puedeGestionarInternos(tp.isPuedeGestionarInternos())
            .activo(tp.isActivo())
            .totalPersonal(total)
            .build();
    }

    private PersonalHospitalDto toPersonalDto(Titular t) {
        Persona p   = t.getPersona();
        TipoPersonal tp = t.getTipoPersonal();
        Optional<Usuario> usuOpt = usuarioDao.findByPersonaIdPersona(
            p != null ? p.getIdPersona() : -1L);

        return PersonalHospitalDto.builder()
            .idTitular(t.getIdTitular())
            .idPersona(p != null ? p.getIdPersona() : null)
            .nombreCompleto(p != null ? p.getApellido() + " " + p.getNombre() : "—")
            .ci(p != null ? p.getCi() : null)
            .email(p != null ? p.getEmail() : null)
            .telefono(p != null ? p.getTelefono() : null)
            .cargo(t.getCargo())
            .especialidad(t.getEspecialidad())
            .idTipoPersonal(tp != null ? tp.getIdTipoPersonal() : null)
            .tipoPersonalNombre(tp != null ? tp.getNombre() : null)
            .tipoPersonalColor(tp != null ? tp.getColor() : null)
            .esPersonalMedico(tp != null && tp.isEsPersonalMedico())
            .puedeGestionarInternos(tp != null && tp.isPuedeGestionarInternos())
            .activo(t.isActivo())
            .idUsuario(usuOpt.map(Usuario::getIdUsuario).orElse(null))
            .username(usuOpt.map(Usuario::getUsername).orElse(null))
            .tieneUsuario(usuOpt.isPresent())
            .build();
    }
}
