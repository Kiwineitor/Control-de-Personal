package com.uap.controlMedico.model.dto.actividadDto;

import java.time.LocalDateTime;
import java.time.LocalTime;

import com.uap.controlMedico.model.enums.EstadoActividad;
import com.uap.controlMedico.model.enums.TipoActividad;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ActividadJornadaDto {
    
    private Long             idActividadJornada;

    // ── Actividad del catálogo ─────────────────────────────────────────────
    private Long             idActividad;
    private String           nombreActividad;
    private TipoActividad    tipoActividad;
    private String           descripcionActividad;

    // ── A quién pertenece ─────────────────────────────────────────────────
    /** "INTERNO" | "TITULAR" */
    private String           asignadoA;
    private Long             idInternoJornada;
    private Long             idTitularJornada;
    private Long             idPersona;        // idInterno o idTitular
    private String           nombrePersona;

    // ── Jornada de contexto ───────────────────────────────────────────────
    private Long             idJornada;
    private String           nombreJornada;
    private String           fechaJornada;     // yyyy-MM-dd

    // ── Estado y tiempos ──────────────────────────────────────────────────
    private EstadoActividad  estadoActividad;
    private LocalTime        horaInicio;
    private LocalTime        horaFin;
    private String           evidenciaUrl;
    private String           observaciones;

    // ── Validación ────────────────────────────────────────────────────────
    private boolean          validada;
    private Long             idValidadoPor;
    private String           nombreValidador;
    private LocalDateTime    fechaValidacion;

    private LocalDateTime    fechaCreacion;
}
