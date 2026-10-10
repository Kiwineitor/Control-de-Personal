package com.uap.controlMedico.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.uap.controlMedico.model.dao.RolDao;
import com.uap.controlMedico.model.dao.RolVistaDao;
import com.uap.controlMedico.model.dao.VistaDao;
import com.uap.controlMedico.model.entity.Rol;
import com.uap.controlMedico.model.entity.RolVista;
import com.uap.controlMedico.model.entity.Vista;

import lombok.RequiredArgsConstructor;

/**
 * Siembra el catálogo de vistas y los conjuntos base por rol. Idempotente y seguro:
 *  - Sólo agrega vistas/relaciones que faltan (no pisa ajustes hechos por el admin).
 *  - Los defaults por rol replican el acceso ACTUAL, de modo que al activar el sistema
 *    de vistas nadie pierde acceso.
 * Corre en cada arranque (cubre BDs de producción ya existentes). @Order posterior a
 * DataInitializer para que los roles ya existan en el primer arranque.
 */
@Component
@Order(20)
@RequiredArgsConstructor
public class VistaSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(VistaSeeder.class);

    private final VistaDao    vistaDao;
    private final RolDao      rolDao;
    private final RolVistaDao rolVistaDao;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // 1) Catálogo de vistas (idempotente por código).
        seedVista("INICIO",        "Inicio",                     "General",     0);
        seedVista("JORNADAS",      "Jornadas",                   "Operaciones", 10);
        seedVista("ACTIVIDADES",   "Actividades",                "Operaciones", 20);
        seedVista("PERMISOS",      "Permisos",                   "Operaciones", 30);
        seedVista("MONITOREO",     "Monitoreo y Seguridad",      "Monitoreo",   40);
        seedVista("PERSONAL",      "Personal del Hospital",      "Personal",    50);
        seedVista("CONFIGURACION", "Configuración del Sistema",  "Sistema",     60);
        seedVista("CONSOLA",       "Consola del Sistema",        "Sistema",     70);

        // 2) Conjuntos base por rol (sólo si el rol aún no tiene vistas asignadas).
        //    Replican el acceso actual (ver sisu-roles.ts) para no cambiar nada al activar.
        seedRolDefaults("ADMIN",      List.of("INICIO", "JORNADAS", "ACTIVIDADES", "PERMISOS",
                                               "MONITOREO", "PERSONAL", "CONFIGURACION", "CONSOLA"));
        seedRolDefaults("SUPERVISOR", List.of("INICIO", "JORNADAS", "ACTIVIDADES", "PERMISOS",
                                               "MONITOREO", "PERSONAL"));
        // Funcionarios: su acceso WEB (Mis Turnos/Actividades/Permisos/Faltas) y el
        // portal /app son por ROL, no por vista. En el menú admin solo ven INICIO.
        seedRolDefaults("MEDICO",     List.of("INICIO"));
        seedRolDefaults("ENFERMERO",  List.of("INICIO"));
        seedRolDefaults("INTERNO",    List.of("INICIO"));
        seedRolDefaults("CAJERO",     List.of("INICIO"));
        seedRolDefaults("FARMACIA",   List.of("INICIO"));
        seedRolDefaults("LIMPIEZA",   List.of("INICIO"));
    }

    private void seedVista(String codigo, String nombre, String grupo, int orden) {
        if (!vistaDao.existsByCodigo(codigo)) {
            vistaDao.save(Vista.builder()
                .codigo(codigo).nombre(nombre).grupo(grupo).orden(orden).activo(true).build());
            log.info("[VistaSeeder] Vista creada: {}", codigo);
        }
    }

    private void seedRolDefaults(String rolNombre, List<String> codigos) {
        Rol rol = rolDao.findByNombreIgnoreCase(rolNombre).orElse(null);
        if (rol == null) return;                                   // aún no existe el rol
        if (!rolVistaDao.findByRol_IdRol(rol.getIdRol()).isEmpty()) return; // ya tiene vistas

        for (String codigo : codigos) {
            vistaDao.findByCodigo(codigo).ifPresent(v ->
                rolVistaDao.save(RolVista.builder().rol(rol).vista(v).build()));
        }
        log.info("[VistaSeeder] Vistas base del rol {} sembradas: {}", rolNombre, codigos);
    }
}
