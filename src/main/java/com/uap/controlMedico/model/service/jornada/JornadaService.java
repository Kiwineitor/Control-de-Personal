package com.uap.controlMedico.model.service.jornada;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.uap.controlMedico.model.dto.internosDto.InternoJornadaDto;
import com.uap.controlMedico.model.dto.jornadaDto.AsignarInternoRequest;
import com.uap.controlMedico.model.dto.jornadaDto.AsignarTitularRequest;
import com.uap.controlMedico.model.dto.jornadaDto.CambiarEstadoAsignacionRequest;
import com.uap.controlMedico.model.dto.jornadaDto.InternoSelectDto;
import com.uap.controlMedico.model.dto.jornadaDto.JornadaDto;
import com.uap.controlMedico.model.dto.jornadaDto.JornadaRequest;
import com.uap.controlMedico.model.dto.jornadaDto.JornadaTitularDto;
import com.uap.controlMedico.model.dto.jornadaDto.LugarSelectDto;
import com.uap.controlMedico.model.dto.jornadaDto.TitularSelectDto;
import com.uap.controlMedico.model.dto.titularDto.TitularJornadaDto;
import com.uap.controlMedico.model.enums.EstadoJornada;
import com.uap.controlMedico.model.enums.TipoJornada;

public interface JornadaService {
    
    Page<JornadaDto> listarConFiltros(String q, EstadoJornada estado, TipoJornada tipo,
                                       LocalDate fechaIni, LocalDate fechaFin, Pageable pageable);
    JornadaDto      findById(Long id);
    JornadaDto      crear(JornadaRequest request);
    JornadaDto      actualizar(Long id, JornadaRequest request);
    JornadaDto      cerrar(Long id);
    JornadaDto      cancelar(Long id);

    /** Suspende (true) o reanuda (false) el control GPS del turno, sin alterar su programación. */
    JornadaDto      cambiarMonitoreo(Long id, boolean pausado);
    List<JornadaDto> listarActivas();

    List<TitularJornadaDto> listarTitulares(Long idJornada);
    List<TitularJornadaDto> asignarTitulares(Long idJornada, AsignarTitularRequest request);
    TitularJornadaDto       cambiarEstadoTitular(Long idTitularJornada, CambiarEstadoAsignacionRequest req);

    /**
     * Cambia el horario individual de un titular YA asignado.
     *
     * Hasta ahora el horario solo podía fijarse en el momento de asignar: para
     * corregirlo había que quitar al titular de la jornada y volver a agregarlo,
     * lo que arrastra sus actividades y su bitácora.
     */
    TitularJornadaDto       actualizarHorarioTitular(Long idTitularJornada,
                                                     com.uap.controlMedico.model.dto.jornadaDto.HorarioIndividual horario);
    void                    removerTitular(Long idTitularJornada);

    List<InternoJornadaDto> listarInternos(Long idJornada);
    List<InternoJornadaDto> asignarInternos(Long idJornada, AsignarInternoRequest request);
    InternoJornadaDto       cambiarEstadoInterno(Long idInternoJornada, CambiarEstadoAsignacionRequest req);
    void                    removerInterno(Long idInternoJornada);

    List<TitularSelectDto> getTitularesDisponibles();
    List<InternoSelectDto> getInternosDisponibles();
    List<LugarSelectDto>   getLugaresDisponibles();

    List<JornadaTitularDto> getJornadasPorTitular(Long idTitular);
}
