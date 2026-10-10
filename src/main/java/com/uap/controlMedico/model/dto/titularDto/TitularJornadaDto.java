package com.uap.controlMedico.model.dto.titularDto;

import java.time.LocalTime;

import com.uap.controlMedico.model.enums.EstadoAsignacion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TitularJornadaDto {
    private Long   idTitularJornada;
    private Long   idTitular;
    private String nombreCompleto;
    private String cargo;
    private String especialidad;
    private Long   idLugar;
    private String nombreLugar;
    private EstadoAsignacion estado;
    private String observaciones;
    private LocalTime horaInicioOverride;
    private LocalTime horaFinOverride;
    private LocalTime horaInicioEfectiva;
    private LocalTime horaFinEfectiva;
    private String diasTrabajoJson;

    // ── Franja de tarde (jornadas PARTIDAS / administrativas) ──────────────
    private LocalTime horaInicioTardeOverride;
    private LocalTime horaFinTardeOverride;
    /** Ya resueltas: override del titular si existe, si no las de la jornada. */
    private LocalTime horaInicioTardeEfectiva;
    private LocalTime horaFinTardeEfectiva;

    /** Qué franjas cubre este titular. Solo significativas si la jornada es PARTIDA. */
    private boolean trabajaManana;
    private boolean trabajaTarde;
    /** true si la jornada de esta asignación es PARTIDA (evita que el front lo deduzca). */
    private boolean jornadaPartida;
}
