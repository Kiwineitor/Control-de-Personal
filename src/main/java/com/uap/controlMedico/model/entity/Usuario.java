package com.uap.controlMedico.model.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.uap.controlMedico.config.AuditoriaConfig;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
    name = "usuario",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_usuario_username", columnNames = "username"),
        @UniqueConstraint(name = "uk_usuario_email",    columnNames = "email")
    }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class Usuario extends AuditoriaConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idUsuario;
 
    /**
     * Nombre de usuario único para login.
     * Para personal de Londra se recomienda usar su CI o código institucional.
     */
    @NotBlank(message = "El username es obligatorio")
    @Size(min = 4, max = 50, message = "El username debe tener entre 4 y 50 caracteres")
    @Column(nullable = false, length = 50)
    private String username;
 
    /**
     * Contraseña almacenada con BCrypt (strength 12).
     * NUNCA se expone en DTOs de respuesta.
     */
    @NotBlank(message = "La contraseña es obligatoria")
    @Column(nullable = false)
    private String password;
 
    @Email(message = "El email no tiene formato válido")
    @Column(length = 150)
    private String email;
 
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String nombre;
 
    @NotBlank(message = "El apellido es obligatorio")
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String apellido;
 
    /**
     * Controla si el usuario puede autenticarse.
     * Usar activo=false en lugar de eliminar registros (soft delete).
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
 
    /** Timestamp del último login exitoso. Útil para auditoría y detección de cuentas abandonadas. */
    @Column(name = "ultimo_acceso")
    private LocalDateTime ultimoAcceso;
 
    /**
     * Persona del sistema vinculada a este usuario (nullable).
     * Si es null → usuario administrativo puro sin registro de persona.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_persona", foreignKey = @ForeignKey(name = "fk_usuario_persona"))
    private Persona persona;

    /**
     * ID de la persona en el sistema Londra (nullable).
     * Si tiene valor → está vinculado a un titular o interno del hospital.
     * Si es null → es un usuario administrativo puro (ej: IT, RRHH).
     */
    @Column(name = "per_id")
    private Long perId;
 
    /**
     * Roles asignados a este usuario.
     * CascadeType.ALL: al eliminar Usuario se eliminan sus UsuarioRol (no los Roles).
     * orphanRemoval: si se quita un UsuarioRol de la lista, se borra de la BD.
     */
    @OneToMany(
        fetch = FetchType.LAZY,
        mappedBy = "usuario",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    @Builder.Default
    private List<UsuarioRol> usuariosRol = new ArrayList<>();

    /**
     * Token UUID para restablecer contraseña.
     * Admin genera → usuario usa → se invalida tras uso o expiración.
     * Se almacena como hash SHA-256 en BD por seguridad.
     */
    @Column(name = "token_reset", length = 255)
    private String tokenReset;

    /** Expiración del token de reset (default: 24h desde generación). */
    @Column(name = "token_reset_expiry")
    private LocalDateTime tokenResetExpiry;

    /**
     * Forzar cambio de contraseña en próximo login.
     * Admin activa este flag → usuario no puede operar hasta cambiarla.
     */
    @Column(name = "requiere_reset", nullable = false)
    @Builder.Default
    private boolean requiereReset = false;

    /**
     * Identificador de la ÚNICA sesión activa del usuario (sesión única).
     *
     * En cada login se genera un UUID nuevo, se guarda aquí y se emite dentro del
     * JWT como claim `sid`. `JwtAuthFilter` (REST) y `JwtChannelInterceptor`
     * (WebSocket) exigen que el `sid` del token coincida con este valor, así que
     * al iniciar sesión en otro dispositivo el token anterior deja de servir.
     *
     * Nullable a propósito: los usuarios que aún no han iniciado sesión desde que
     * existe esta función lo tienen en null, y en ese caso sus tokens previos
     * (sin `sid`) se siguen aceptando hasta que caduquen o vuelvan a loguearse.
     * Evita expulsar a todo el personal de golpe al desplegar.
     */
    @Column(name = "sesion_actual", length = 36)
    private String sesionActual;

}
