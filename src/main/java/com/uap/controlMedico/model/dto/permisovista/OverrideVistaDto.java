package com.uap.controlMedico.model.dto.permisovista;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Ajuste por usuario sobre una vista: tipo = "GRANT" (agrega) | "DENY" (quita). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OverrideVistaDto {
    private String codigo;
    private String tipo;
}
