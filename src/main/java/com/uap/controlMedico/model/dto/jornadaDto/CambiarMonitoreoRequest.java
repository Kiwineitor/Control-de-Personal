package com.uap.controlMedico.model.dto.jornadaDto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Detener o reanudar el control GPS de un turno.
 *
 * Se manda el estado deseado y no un "toggle" para que la operación sea
 * idempotente: dos clics seguidos desde la vista de vigilancia dejan el turno
 * donde el operador quiso, no oscilando.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CambiarMonitoreoRequest {

    /** true = detener el control; false = reanudarlo. */
    private boolean pausado;
}
