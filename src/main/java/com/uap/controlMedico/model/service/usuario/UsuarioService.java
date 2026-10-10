package com.uap.controlMedico.model.service.usuario;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.uap.controlMedico.model.dto.usuarioDto.CambiarPasswordRequest;
import com.uap.controlMedico.model.dto.usuarioDto.LoginRequest;
import com.uap.controlMedico.model.dto.usuarioDto.LoginResponse;
import com.uap.controlMedico.model.dto.usuarioDto.ResetPasswordRequest;
import com.uap.controlMedico.model.dto.usuarioDto.TokenResetResponse;
import com.uap.controlMedico.model.dto.usuarioDto.UsuarioRequest;
import com.uap.controlMedico.model.dto.usuarioDto.UsuarioResponse;
import com.uap.controlMedico.model.dto.usuarioDto.UsuarioSesion;

public interface UsuarioService {
    UsuarioResponse crear(UsuarioRequest request);
    UsuarioResponse actualizar(Long id, UsuarioRequest request);
    UsuarioResponse buscarPorId(Long id);
    UsuarioResponse buscarPorUsername(String username);
    List<UsuarioResponse> listarActivos();
    List<UsuarioResponse> buscarPorTermino(String termino);
    void desactivar(Long id);
    void activar(Long id);
    LoginResponse login(LoginRequest request);
    UsuarioSesion registrarLoginExitoso(String username);

    /**
     * Abre una sesión única para el usuario: genera un UUID, lo persiste como
     * sesión vigente y lo devuelve para grabarlo en el JWT. Invalida de inmediato
     * el token del dispositivo anterior.
     */
    String abrirSesionUnica(Long idUsuario);

    /** Cierra la sesión vigente (logout): ningún token del usuario vuelve a valer. */
    void cerrarSesionUnica(String username);

    Page<UsuarioResponse> getAll(String termino, String rolNombre,
                              Boolean activo, Pageable pageable);

    TokenResetResponse generarTokenReset(Long id);

    void forzarCambioPassword(Long id);

    void resetPassword(ResetPasswordRequest request);

    void cambiarPassword(String username, CambiarPasswordRequest request);

}
