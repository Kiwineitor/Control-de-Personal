package com.uap.controlMedico.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.uap.controlMedico.model.dao.RolDao;
import com.uap.controlMedico.model.dao.UsuarioDao;
import com.uap.controlMedico.model.entity.Rol;
import com.uap.controlMedico.model.entity.Usuario;
import com.uap.controlMedico.model.entity.UsuarioRol;
import com.uap.controlMedico.model.enums.RolNombre;

import lombok.RequiredArgsConstructor;

/**
 * Crea roles y usuarios fundadores al primer arranque del sistema.
 * Idempotente: si ya existen usuarios, no hace nada.
 */
@Component
@Order(10)
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final RolDao          rolDao;
    private final UsuarioDao      usuarioDao;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (usuarioDao.count() > 0) {
            log.info("[DataInitializer] Usuarios ya existentes — omitiendo inicialización.");
            return;
        }

        log.info("[DataInitializer] Primer arranque: creando roles y usuarios fundadores.");

        inicializarRoles();

        // Administrador: acceso total al sistema
        crearUsuario(
            "Administrador", "Admin2026$",
            "admin@sistema.local", "Administrador", "Sistema",
            RolNombre.ADMIN
        );

        // Super usuario: acceso completo excepto gestión de usuarios y roles
        crearUsuario(
            "Usuario26", "usuario$26",
            "supervisor@sistema.local", "Super", "Usuario",
            RolNombre.SUPERVISOR
        );

        log.info("[DataInitializer] Inicialización completada exitosamente.");
    }

    private void inicializarRoles() {
        for (RolNombre nombre : RolNombre.values()) {
            if (!rolDao.existsByNombreIgnoreCase(nombre.name())) {
                rolDao.save(Rol.builder()
                        .nombre(nombre.name())
                        .estado("ACTIVO")
                        .build());
                log.info("  Rol creado: {}", nombre.name());
            }
        }
    }

    private void crearUsuario(String username, String rawPassword,
                               String email, String nombre, String apellido,
                               RolNombre rolNombre) {
        if (usuarioDao.existsByUsername(username)) {
            log.info("  Usuario '{}' ya existe — omitiendo.", username);
            return;
        }

        Rol rol = rolDao.findByNombreIgnoreCase(rolNombre.name())
                .orElseThrow(() -> new IllegalStateException(
                        "[DataInitializer] Rol no encontrado: " + rolNombre));

        Usuario usuario = Usuario.builder()
                .username(username)
                .password(passwordEncoder.encode(rawPassword))
                .email(email)
                .nombre(nombre)
                .apellido(apellido)
                .activo(true)
                .requiereReset(false)
                .build();

        usuarioDao.save(usuario);

        // CascadeType.ALL en usuariosRol propaga el UsuarioRol sin repo separado
        usuario.getUsuariosRol().add(
                UsuarioRol.builder()
                        .usuario(usuario)
                        .rol(rol)
                        .build()
        );
        usuarioDao.save(usuario);

        log.info("  Usuario fundador creado: '{}' con rol {}.", username, rolNombre.name());
    }
}
