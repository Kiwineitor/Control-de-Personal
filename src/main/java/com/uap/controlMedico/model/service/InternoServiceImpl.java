package com.uap.controlMedico.model.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uap.controlMedico.exception.BusinessException;
import com.uap.controlMedico.exception.ResourceNotFoundException;
import com.uap.controlMedico.model.dao.CodigoQrDao;
import com.uap.controlMedico.model.dao.InternoDao;
import com.uap.controlMedico.model.dao.PersonaDao;
import com.uap.controlMedico.model.dao.ResponsableInternoDao;
import com.uap.controlMedico.model.dao.RolDao;
import com.uap.controlMedico.model.dao.TitularDao;
import com.uap.controlMedico.model.dao.UsuarioDao;
import com.uap.controlMedico.model.dao.UsuarioRolDao;
import com.uap.controlMedico.model.dto.internosDto.InternoRequest;
import com.uap.controlMedico.model.dto.internosDto.InternoResponse;
import com.uap.controlMedico.model.dto.internosDto.InternoResumenResponse;
import com.uap.controlMedico.model.entity.CodigoQr;
import com.uap.controlMedico.model.entity.Interno;
import com.uap.controlMedico.model.entity.Lugar;
import com.uap.controlMedico.model.entity.Persona;
import com.uap.controlMedico.model.entity.ResponsableInterno;
import com.uap.controlMedico.model.entity.Rol;
import com.uap.controlMedico.model.entity.Titular;
import com.uap.controlMedico.model.entity.Usuario;
import com.uap.controlMedico.model.entity.UsuarioRol;
import com.uap.controlMedico.model.enums.EstadoAsignacion;
import com.uap.controlMedico.model.enums.EstadoJornada;
import com.uap.controlMedico.model.enums.RolNombre;
import com.uap.controlMedico.model.service.londra.LondraService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class InternoServiceImpl implements InternoService {

    private final InternoDao            internoDao;
    private final TitularDao            titularDao;
    private final PersonaDao            personaDao;
    private final CodigoQrDao           codigoQrDao;
    private final ResponsableInternoDao responsableInternoDao;
    private final SisuMapper            mapper;
    private final LondraService         londraService;
    private final UsuarioDao            usuarioDao;
    private final UsuarioRolDao         usuarioRolDao;
    private final RolDao                rolDao;
    private final PasswordEncoder       passwordEncoder;
 
    @Override
    @Transactional
    public InternoResponse crear(InternoRequest request) {
        if (request.getFechaInicioRotacion() != null && request.getFechaFinRotacion() != null
                && request.getFechaFinRotacion().isBefore(request.getFechaInicioRotacion()))
            throw new BusinessException(
                "La fecha de fin de rotación no puede ser anterior a la de inicio");

        // ① Obtener o crear la entidad Persona
        Persona persona;
        if (request.getIdPersona() != null) {
            // Usar persona existente
            persona = personaDao.findById(request.getIdPersona())
                .orElseThrow(() -> new ResourceNotFoundException("Persona", request.getIdPersona()));
        } else {
            // Crear nueva persona con datos manuales
            persona = Persona.builder()
                    .nombre(request.getNombre())
                    .apellido(request.getApellido())
                    .ci(request.getCi())
                    .email(request.getEmail())
                    .telefono(request.getTelefono())
                    .build();
            persona = personaDao.save(persona);
        }

        // ② Crear el Interno vinculado a Persona
        Interno interno = Interno.builder()
                .persona(persona)
                .universidad(request.getUniversidad())
                .carrera(request.getCarrera())
                .anoFormacion(request.getAnoFormacion())
                .nroMatricula(request.getNroMatricula())
                .fechaInicioRotacion(request.getFechaInicioRotacion())
                .fechaFinRotacion(request.getFechaFinRotacion())
                .activo(request.isActivo())
                .build();
        interno = internoDao.save(interno);

        // ③ Crear cuenta de usuario si se solicitó
        if (request.isCrearUsuario()) {
            crearUsuarioParaInterno(persona, request);
        }

        return mapper.toInternoResponse(
                internoDao.findById(interno.getIdInterno()).orElseThrow());
    }

    /**
     * Crea la cuenta de Usuario de un interno y le asigna el rol (idRol o INTERNO
     * por defecto). El perId del usuario se setea al idPersona porque la resolución
     * de jornadas del interno (findActivasByPersona) filtra por persona.idPersona.
     */
    private void crearUsuarioParaInterno(Persona persona, InternoRequest request) {
        Long idPersona = persona.getIdPersona();
        if (usuarioDao.existsByPersonaIdPersona(idPersona)) {
            log.warn("Persona id={} ya tiene usuario, se omite creación", idPersona);
            return;
        }

        String username = request.getUsername() != null && !request.getUsername().isBlank()
            ? request.getUsername().trim()
            : generarUsername(persona);
        String rawPwd = request.getPassword() != null && !request.getPassword().isBlank()
            ? request.getPassword()
            : UUID.randomUUID().toString().substring(0, 8);

        // Garantizar unicidad del username
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
        usuario.setPerId(idPersona);     // resolución GPS del interno usa idPersona
        usuario.setActivo(true);
        usuario.setRequiereReset(true);  // forzar cambio de contraseña al primer login
        usuario = usuarioDao.save(usuario);

        // Rol: el indicado o INTERNO por defecto
        Rol rol = request.getIdRol() != null
            ? rolDao.findById(request.getIdRol()).orElse(null)
            : rolDao.findByNombre(RolNombre.INTERNO).orElse(null);
        if (rol != null) {
            UsuarioRol ur = new UsuarioRol();
            ur.setUsuario(usuario);
            ur.setRol(rol);
            usuarioRolDao.save(ur);
        } else {
            log.warn("No se pudo resolver rol para interno (idRol={}), usuario sin rol", request.getIdRol());
        }

        log.info("Usuario de interno creado: {} (contraseña omitida por seguridad)", username);
    }

    private String generarUsername(Persona p) {
        String base = (p.getNombre() != null ? p.getNombre().substring(0, 1) : "i")
            + (p.getApellido() != null ? p.getApellido().replaceAll("\\s+", "").toLowerCase() : "interno");
        return base.replaceAll("[^a-z0-9]", "").toLowerCase();
    }

 
    @Override
    @Transactional
    public InternoResponse actualizar(Long id, InternoRequest request) {
        Interno interno = buscarEntidadPorId(id);
 
        if (request.getFechaInicioRotacion() != null && request.getFechaFinRotacion() != null
                && request.getFechaFinRotacion().isBefore(request.getFechaInicioRotacion()))
            throw new BusinessException("La fecha de fin no puede ser anterior a la de inicio");
 
        // ① Actualizar Persona (campos no nulos)
        Persona persona = interno.getPersona();
        if (request.getNombre()   != null) persona.setNombre(request.getNombre());
        if (request.getApellido() != null) persona.setApellido(request.getApellido());
        if (request.getCi()       != null) persona.setCi(request.getCi());
        if (request.getEmail()    != null) persona.setEmail(request.getEmail());
        if (request.getTelefono() != null) persona.setTelefono(request.getTelefono());
        personaDao.save(persona);
 
        // ② Actualizar campos propios del Interno
        interno.setUniversidad(request.getUniversidad());
        interno.setCarrera(request.getCarrera());
        interno.setAnoFormacion(request.getAnoFormacion());
        interno.setNroMatricula(request.getNroMatricula());
        interno.setFechaInicioRotacion(request.getFechaInicioRotacion());
        interno.setFechaFinRotacion(request.getFechaFinRotacion());
        interno.setActivo(request.isActivo());
        internoDao.save(interno);
 
        return mapper.toInternoResponse(internoDao.findById(id).orElseThrow());
    }

 
    @Override
    @Transactional(readOnly = true)
    public InternoResponse buscarPorId(Long id) {
        return mapper.toInternoResponse(buscarEntidadPorId(id));
    }
 
    @Override
    @Transactional(readOnly = true)
    public Page<InternoResponse> listarActivos(Pageable pageable) {
        return internoDao.findByActivoTrue(pageable)
                .map(mapper::toInternoResponse);
    }
 
    @Override
    @Transactional(readOnly = true)
    public List<InternoResumenResponse> buscarPorTermino(String termino) {
        return internoDao.buscarPorTermino(termino).stream()
                .map(mapper::toInternoResumen)
                .collect(Collectors.toList());
    }
 
    @Override
    @Transactional(readOnly = true)
    public List<InternoResumenResponse> findEnRotacionEnFecha(LocalDate fecha) {
        return internoDao.findEnRotacionEnFecha(fecha).stream()
                .map(mapper::toInternoResumen)
                .collect(Collectors.toList());
    }
 
    @Override
    @Transactional(readOnly = true)
    public List<InternoResumenResponse> findEnTurnoActual() {
        return internoDao.findInternosEnTurnoActual(
                    EstadoAsignacion.EN_CURSO,
                    EstadoAsignacion.CONFIRMADO,
                    EstadoJornada.ACTIVA
        ).stream().map(mapper::toInternoResumen).collect(Collectors.toList());
    }
 
    @Override
    @Transactional(readOnly = true)
    public List<InternoResumenResponse> findSinResponsableActivo() {
        return internoDao.findSinResponsableActivo().stream()
                .map(mapper::toInternoResumen)
                .collect(Collectors.toList());
    }
 
    @Override
    @Transactional(readOnly = true)
    public List<InternoResumenResponse> findByTitularResponsable(Long idTitular) {
        return internoDao.findByTitularResponsable(idTitular).stream()
                .map(mapper::toInternoResumen)
                .collect(Collectors.toList());
    }
 
    @Override
    @Transactional
    public void sincronizarDesdeLondra(Long id) {
        sincronizarDatos(buscarEntidadPorId(id));
    }
 
    @Override
    @Transactional
    public void desactivar(Long id) {
        Interno i = buscarEntidadPorId(id);
        i.setActivo(false);
        internoDao.save(i);
    }
 
    // ── Helpers privados ───────────────────────────────────────────────
 
    private Interno buscarEntidadPorId(Long id) {
        return internoDao.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Interno", id));
    }

 
    private void asignarResponsable(Interno interno, Long idTitular) {
        Titular titular = titularDao.findById(idTitular)
                .orElseThrow(() -> new ResourceNotFoundException("Titular", idTitular));
 
        ResponsableInterno resp = ResponsableInterno.builder()
                .interno(interno)
                .titular(titular)
                .fechaAsignacion(LocalDate.now())
                .activo(true)
                .build();
        responsableInternoDao.save(resp);
    }
 
    private void sincronizarDatos(Interno interno) {
        try {
            londraService.sincronizarInterno(interno);
            internoDao.save(interno);
        } catch (Exception e) {
            // Londra no disponible → continúa sin datos cacheados
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InternoResponse> listarConFiltros(
            String q, String carrera, String universidad,
            Boolean enRotacion, Boolean activo, Pageable pageable) {
 
        // ✅ "" en lugar de null
        String qN = (q           != null && !q.isBlank())           ? q.toLowerCase()           : "";
        String cN = (carrera     != null && !carrera.isBlank())     ? carrera.toLowerCase()     : "";
        String uN = (universidad != null && !universidad.isBlank()) ? universidad.toLowerCase() : "";
 
        return internoDao.findConFiltros(qN, cN, uN, enRotacion, activo, pageable)
                .map(mapper::toInternoResponse);
    }

    @Override
    @Transactional
    public InternoResponse toggleActivo(Long id) {
        Interno interno = internoDao.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Interno", id));
        interno.setActivo(!interno.isActivo());
        return mapper.toInternoResponse(internoDao.save(interno));
    }
 
        @Override
    @Transactional(readOnly = true)
    public List<InternoResponse> findAllActivos() {
        return internoDao.findActivosOrdenados()
                .stream()
                .map(mapper::toInternoResponse)
                .collect(Collectors.toList());
    }
 
    @Override
    public List<String> getDistinctCarreras() {
        return internoDao.findDistinctCarreras();
    }
 
    @Override
    public List<String> getDistinctUniversidades() {
        return internoDao.findDistinctUniversidades();
    }


    @Override
    public String regenerarCodigoQr(Long idInterno) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'regenerarCodigoQr'");
    }

}
