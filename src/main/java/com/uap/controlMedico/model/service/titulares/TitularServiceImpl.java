package com.uap.controlMedico.model.service.titulares;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uap.controlMedico.exception.BusinessException;
import com.uap.controlMedico.exception.ResourceNotFoundException;
import com.uap.controlMedico.model.dao.PersonaDao;
import com.uap.controlMedico.model.dao.TitularDao;
import com.uap.controlMedico.model.dto.titularDto.TitularRequest;
import com.uap.controlMedico.model.dto.titularDto.TitularResponse;
import com.uap.controlMedico.model.dto.titularDto.TitularResumenResponse;
import com.uap.controlMedico.model.entity.Persona;
import com.uap.controlMedico.model.entity.Titular;
import com.uap.controlMedico.model.enums.EstadoAsignacion;
import com.uap.controlMedico.model.enums.EstadoJornada;
import com.uap.controlMedico.model.service.SisuMapper;
import com.uap.controlMedico.model.service.londra.LondraService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TitularServiceImpl implements TitularService {
    private final TitularDao    titularDao;
    private final PersonaDao    personaDao;
    private final SisuMapper    mapper;
    private final LondraService londraService;
 
    @Override
    @Transactional
    public TitularResponse crear(TitularRequest request) {
        if (titularDao.existsByPerId(request.getPerId()))
            throw new BusinessException(
                "Ya existe un titular registrado con el perId " + request.getPerId());

        // ① Obtener o crear la entidad Persona
        Persona persona;
        if (request.getIdPersona() != null) {
            // Usar persona existente — validar que no sea ya titular
            persona = personaDao.findById(request.getIdPersona())
                .orElseThrow(() -> new ResourceNotFoundException("Persona", request.getIdPersona()));
            titularDao.findByPersona_IdPersona(request.getIdPersona()).ifPresent(t -> {
                throw new BusinessException(
                    "Esta persona ya está registrada como titular (id=" + t.getIdTitular() + ")");
            });
        } else {
            // Crear nueva persona con los datos manuales
            persona = Persona.builder()
                    .nombre(request.getNombre())
                    .apellido(request.getApellido())
                    .ci(request.getCi())
                    .email(request.getEmail())
                    .telefono(request.getTelefono())
                    .build();
            persona = personaDao.save(persona);
        }

        // ② Crear el Titular vinculado a la Persona
        Titular titular = Titular.builder()
                .perId(request.getPerId())
                .contratoId(request.getContratoId())
                .persona(persona)
                .cargo(request.getCargo())
                .especialidad(request.getEspecialidad())
                .activo(request.isActivo())
                .build();
        titular = titularDao.save(titular);

        // ③ Sincronizar desde Londra si se solicitó
        if (request.isSincronizarDesdeLondra())
            sincronizarDatos(titular);

        return mapper.toTitularResponse(
                titularDao.findById(titular.getIdTitular()).orElseThrow());
    }

 
    @Override
    @Transactional
    public TitularResponse actualizar(Long id, TitularRequest request) {
        Titular titular = buscarEntidadPorId(id);
 
        // ① Actualizar Persona (campos que vengan no nulos)
        Persona persona = titular.getPersona();
        if (request.getNombre()    != null) persona.setNombre(request.getNombre());
        if (request.getApellido()  != null) persona.setApellido(request.getApellido());
        if (request.getCi()        != null) persona.setCi(request.getCi());
        if (request.getEmail()     != null) persona.setEmail(request.getEmail());
        if (request.getTelefono()  != null) persona.setTelefono(request.getTelefono());
        personaDao.save(persona);
 
        // ② Actualizar campos propios del Titular
        titular.setContratoId(request.getContratoId());
        if (request.getCargo()       != null) titular.setCargo(request.getCargo());
        if (request.getEspecialidad()!= null) titular.setEspecialidad(request.getEspecialidad());
        titular.setActivo(request.isActivo());
        titularDao.save(titular);
 
        // ③ Sincronizar desde Londra si se solicitó
        if (request.isSincronizarDesdeLondra())
            sincronizarDatos(titular);
 
        return mapper.toTitularResponse(titularDao.findById(id).orElseThrow());
    }

 
    @Override
    @Transactional(readOnly = true)
    public TitularResponse buscarPorId(Long id) {
        return mapper.toTitularResponse(buscarEntidadPorId(id));
    }
 
    @Override
    @Transactional(readOnly = true)
    public TitularResponse buscarPorPerId(Long perId) {
        return mapper.toTitularResponse(
            titularDao.findByPerIdAndActivoTrue(perId)
                .orElseThrow(() -> new ResourceNotFoundException("Titular", "perId", perId))
        );
    }
 
    @Override
    @Transactional(readOnly = true)
    public Page<TitularResponse> listarActivos(Pageable pageable) {
        return titularDao.findByActivoTrue(pageable)
                .map(mapper::toTitularResponse);
    }
 
    @Override
    @Transactional(readOnly = true)
    public List<TitularResumenResponse> buscarPorTermino(String termino) {
        return titularDao.buscarPorTermino(termino).stream()
                .map(mapper::toTitularResumen)
                .collect(Collectors.toList());
    }
 
    @Override
    @Transactional(readOnly = true)
    public List<TitularResumenResponse> findEnTurnoActual() {
        return titularDao.findTitularesEnTurnoActual(
                EstadoAsignacion.EN_CURSO,
                EstadoAsignacion.CONFIRMADO,
                EstadoJornada.ACTIVA
        ).stream().map(mapper::toTitularResumen).collect(Collectors.toList());
    }
 
    @Override
    @Transactional(readOnly = true)
    public List<TitularResumenResponse> findResponsablesDeInterno(Long idInterno) {
        return titularDao.findResponsablesDeInterno(idInterno).stream()
                .map(mapper::toTitularResumen)
                .collect(Collectors.toList());
    }
 
    @Override
    @Transactional(readOnly = true)
    public List<TitularResumenResponse> findSinTurnoEnFecha(LocalDate fecha) {
        return titularDao.findSinTurnoEnFecha(fecha, EstadoAsignacion.CANCELADO).stream()
                .map(mapper::toTitularResumen)
                .collect(Collectors.toList());
    }
 
    @Override
    @Transactional
    public TitularResponse sincronizarDesdeLondra(Long id) {
        Titular titular = buscarEntidadPorId(id);
        sincronizarDatos(titular);
        // sincronizarDatos ya llama titularDao.save(titular) internamente
        return mapper.toTitularResponse(titularDao.findById(id).orElseThrow());
    }

 
    @Override
    @Transactional
    public void desactivar(Long id) {
        Titular t = buscarEntidadPorId(id);
        t.setActivo(false);
        titularDao.save(t);
    }
 
    // ── Helpers privados ───────────────────────────────────────────────
 
    private Titular buscarEntidadPorId(Long id) {
        return titularDao.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Titular", id));
    }
 
    /**
     * Consulta Londra y cachea los datos de la persona y su contrato.
     * Si Londra no responde, solo registra advertencia (no falla el flujo).
     */
    private void sincronizarDatos(Titular titular) {
        try {
            londraService.sincronizarTitular(titular);
            titularDao.save(titular);
        } catch (Exception e) {
            // Londra no disponible → continúa sin datos cacheados
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TitularResponse> listarConFiltros(
            String q, String cargo, String especialidad, Boolean activo, Pageable pageable) {
 
        // ✅ "" en lugar de null — evita lower(bytea) en PostgreSQL
        String qN   = (q            != null && !q.isBlank())            ? q.toLowerCase()            : "";
        String cN   = (cargo        != null && !cargo.isBlank())        ? cargo.toLowerCase()        : "";
        String espN = (especialidad != null && !especialidad.isBlank()) ? especialidad.toLowerCase() : "";
 
        return titularDao.findConFiltros(qN, cN, espN, activo, pageable)
                .map(mapper::toTitularResponse);
    }

 
    @Override
    @Transactional
    public TitularResponse toggleActivo(Long id) {
        Titular titular = buscarEntidadPorId(id);   // reusar helper existente
        titular.setActivo(!titular.isActivo());
        return mapper.toTitularResponse(titularDao.save(titular));
    }
 
        @Override
    @Transactional(readOnly = true)
    public List<TitularResponse> findAllActivos() {
        return titularDao.findActivosOrdenados()
                .stream()
                .map(mapper::toTitularResponse)
                .collect(Collectors.toList());
    }



    @Override
    public List<String> getDistinctCargos() {
        return titularDao.findDistinctCargos();
    }
 
    @Override
    public List<String> getDistinctEspecialidades() {
        return titularDao.findDistinctEspecialidades();
    }


}
