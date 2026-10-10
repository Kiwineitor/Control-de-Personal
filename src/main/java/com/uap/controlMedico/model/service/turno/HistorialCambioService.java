package com.uap.controlMedico.model.service.turno;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.uap.controlMedico.model.dao.HistorialCambioDao;
import com.uap.controlMedico.model.entity.HistorialCambio;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class HistorialCambioService {

    private final HistorialCambioDao historialDao;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @Transactional
    public void registrarAccion(String entidad, Long idEntidad, String accion,
                                String campo, String valorAnterior, String valorNuevo) {
        historialDao.save(HistorialCambio.builder()
            .entidad(entidad)
            .idEntidad(idEntidad)
            .campo(campo)
            .valorAnterior(valorAnterior)
            .valorNuevo(valorNuevo)
            .accion(accion)
            .usuario(getUsuarioActual())
            .fechaCambio(LocalDateTime.now())
            .build());
    }

    @Transactional
    public void compararYGuardar(String entidad, Long idEntidad,
                                  Object antes, Object despues) {
        if (antes == null || despues == null) return;
        if (!(antes instanceof java.util.Map) || !(despues instanceof java.util.Map)) return;

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> a = (java.util.Map<String, Object>) antes;
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> d = (java.util.Map<String, Object>) despues;

        String usuario = getUsuarioActual();
        LocalDateTime ahora = LocalDateTime.now();
        java.util.List<HistorialCambio> cambios = new java.util.ArrayList<>();

        for (String campo : d.keySet()) {
            if (campo.startsWith("__")) continue;
            Object valA = a.get(campo);
            Object valD = d.get(campo);
            if (valA == null && valD == null) continue;
            if (valA != null && valA.equals(valD)) continue;

            cambios.add(HistorialCambio.builder()
                .entidad(entidad)
                .idEntidad(idEntidad)
                .campo(campo)
                .valorAnterior(formatear(valA))
                .valorNuevo(formatear(valD))
                .accion("MODIFICADO")
                .usuario(usuario)
                .fechaCambio(ahora)
                .build());
        }

        if (!cambios.isEmpty()) {
            historialDao.saveAll(cambios);
            log.info("Audit: {} #{} — {} cambios registrados por {}", entidad, idEntidad, cambios.size(), usuario);
        }
    }

    @Transactional(readOnly = true)
    public List<HistorialCambio> obtenerHistorial(String entidad, Long idEntidad) {
        return historialDao.findByEntidadAndIdEntidadOrderByFechaCambioDesc(entidad, idEntidad);
    }

    /** Captura el estado de un request DTO como Map para comparación posterior. */
    public java.util.Map<String, Object> capturarSnapshot(Object dto) {
        if (dto == null) return null;
        try {
            var snapshot = new java.util.LinkedHashMap<String, Object>();
            for (java.lang.reflect.Field f : dto.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                snapshot.put(f.getName(), f.get(dto));
            }
            return snapshot;
        } catch (Exception e) {
            return null;
        }
    }

    /** Extrae un Map del DTO contenido en un ApiResponse. */
    public java.util.Map<String, Object> extraerReturn(Object resultado) {
        if (resultado == null) return null;
        try {
            java.lang.reflect.Method getData = resultado.getClass().getMethod("getData");
            Object data = getData.invoke(resultado);
            if (data == null) return null;
            var snapshot = new java.util.LinkedHashMap<String, Object>();
            for (java.lang.reflect.Field f : data.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                snapshot.put(f.getName(), f.get(data));
            }
            return snapshot;
        } catch (Exception e) {
            return null;
        }
    }

    private String formatear(Object valor) {
        if (valor == null) return null;
        if (valor instanceof LocalTime lt) return lt.format(TIME_FMT);
        return valor.toString();
    }

    private String getUsuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "SISTEMA";
    }
}
