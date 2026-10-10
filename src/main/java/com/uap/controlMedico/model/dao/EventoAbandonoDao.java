package com.uap.controlMedico.model.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.uap.controlMedico.model.entity.EventoAbandono;
import com.uap.controlMedico.model.enums.EstadoEvento;
import com.uap.controlMedico.model.enums.EstadoJustificacion;
import com.uap.controlMedico.model.enums.TipoIncidencia;

public interface EventoAbandonoDao extends JpaRepository<EventoAbandono, Long> {

    /**
     * Evento abierto (no resuelto) de una sesión — el más reciente.
     * Usa findFirst...OrderBy para tolerar duplicados sin lanzar
     * IncorrectResultSizeDataAccessException si por concurrencia se crearon dos.
     */
    Optional<EventoAbandono> findFirstBySesion_IdSesionAndResueltoFalseOrderByIdEventoDesc(Long idSesion);

    /** Todos los eventos abiertos de una sesión (para resolver duplicados al cerrar). */
    List<EventoAbandono> findBySesion_IdSesionAndResueltoFalse(Long idSesion);

    /**
     * ¿Ya existe una incidencia de este tipo hoy para esta persona en esta jornada?
     *
     * La FALTA se registraba contra la SESIÓN, no contra la persona, y una sesión
     * cerrada por falta se recrea al minuto siguiente mientras el turno siga
     * corriendo: cada ciclo de ~30 min dejaba otra falta. En producción llegó a
     * haber 24 faltas de la misma persona el mismo día por un solo turno sin GPS.
     *
     * Se pasa idTitular O idInterno; el otro va en null y su comparación queda en
     * UNKNOWN, así que no aporta al OR.
     */
    @Query("""
        SELECT COUNT(e) FROM EventoAbandono e
        JOIN e.sesion s
        LEFT JOIN s.titular t
        LEFT JOIN s.interno i
        WHERE e.tipo = :tipo
          AND s.jornada.idJornada = :idJornada
          AND e.timestampAbandono >= :desde
          AND e.timestampAbandono <  :hasta
          AND (t.idTitular = :idTitular OR i.idInterno = :idInterno)
    """)
    long contarIncidenciasEnJornada(@Param("tipo") TipoIncidencia tipo,
                                    @Param("idJornada") Long idJornada,
                                    @Param("idTitular") Long idTitular,
                                    @Param("idInterno") Long idInterno,
                                    @Param("desde") LocalDateTime desde,
                                    @Param("hasta") LocalDateTime hasta);

    /** Todas las alertas activas (fuera de zona o abandono no resuelto). */
    @Query("""
        SELECT e FROM EventoAbandono e
        JOIN FETCH e.sesion s
        LEFT JOIN FETCH s.titular t
        LEFT JOIN FETCH t.persona
        LEFT JOIN FETCH s.interno i
        LEFT JOIN FETCH i.persona
        LEFT JOIN FETCH s.lugar
        WHERE e.resuelto = false
        ORDER BY e.timestampSalida ASC
        """)
    List<EventoAbandono> findAlertasActivas();

    /** Eventos no resueltos cuyo tiempo fuera superó el timeout (para detectar abandono). */
    @Query("""
        SELECT e FROM EventoAbandono e
        WHERE e.resuelto = false
          AND e.estado   = :estadoFuera
          AND e.timestampSalida <= :limite
          AND e.sesion.activa = true
        """)
    List<EventoAbandono> findParaConfirmarAbandono(
        @Param("estadoFuera") EstadoEvento estadoFuera,
        @Param("limite")      LocalDateTime limite);

    /** Eventos no notificados al admin. */
    List<EventoAbandono> findByNotificadoAdminFalseAndResueltoFalse();

    /** Historial de eventos de un titular (por persona.idPersona), más recientes primero. */
    @Query("""
        SELECT e FROM EventoAbandono e
        JOIN FETCH e.sesion s
        LEFT JOIN FETCH s.lugar
        WHERE s.titular.persona.idPersona = :idPersona
        ORDER BY e.timestampSalida DESC
        """)
    List<EventoAbandono> findByTitularPersona(@Param("idPersona") Long idPersona);

    /** Historial de eventos de un interno (por persona.idPersona), más recientes primero. */
    @Query("""
        SELECT e FROM EventoAbandono e
        JOIN FETCH e.sesion s
        LEFT JOIN FETCH s.lugar
        WHERE s.interno.persona.idPersona = :idPersona
        ORDER BY e.timestampSalida DESC
        """)
    List<EventoAbandono> findByInternoPersona(@Param("idPersona") Long idPersona);

    /** Historial de incidencias de un titular (por idTitular), más recientes primero. */
    @Query("""
        SELECT e FROM EventoAbandono e
        JOIN FETCH e.sesion s
        LEFT JOIN FETCH s.lugar
        WHERE s.titular.idTitular = :idTitular
        ORDER BY e.timestampSalida DESC
        """)
    List<EventoAbandono> findByTitularId(@Param("idTitular") Long idTitular);

    /** Historial de incidencias de un interno (por idInterno), más recientes primero. */
    @Query("""
        SELECT e FROM EventoAbandono e
        JOIN FETCH e.sesion s
        LEFT JOIN FETCH s.lugar
        WHERE s.interno.idInterno = :idInterno
        ORDER BY e.timestampSalida DESC
        """)
    List<EventoAbandono> findByInternoId(@Param("idInterno") Long idInterno);

    /** Incidencias GPS de una jornada (a través de la sesión), más recientes primero. */
    @Query("""
        SELECT e FROM EventoAbandono e
        JOIN FETCH e.sesion s
        LEFT JOIN FETCH s.titular t
        LEFT JOIN FETCH t.persona
        LEFT JOIN FETCH s.interno i
        LEFT JOIN FETCH i.persona
        LEFT JOIN FETCH s.lugar
        WHERE s.jornada.idJornada = :idJornada
        ORDER BY e.timestampSalida DESC
        """)
    List<EventoAbandono> findByJornadaId(@Param("idJornada") Long idJornada);

    /**
     * Incidencias en un estado de justificación dado (p.ej. EN_REVISION), con el
     * grafo necesario para mostrarlas al admin. Más antiguas primero (cola FIFO).
     */
    @Query("""
        SELECT e FROM EventoAbandono e
        JOIN FETCH e.sesion s
        LEFT JOIN FETCH s.titular t
        LEFT JOIN FETCH t.persona
        LEFT JOIN FETCH s.interno i
        LEFT JOIN FETCH i.persona
        LEFT JOIN FETCH s.lugar
        WHERE e.estadoJustificacion = :estado
        ORDER BY e.fechaSolicitudJustificacion ASC
        """)
    List<EventoAbandono> findByEstadoJustificacion(@Param("estado") EstadoJustificacion estado);
}
