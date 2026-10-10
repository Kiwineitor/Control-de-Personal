package com.uap.controlMedico.model.dto.response;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    
    private boolean success;
    private String message;
    private T data;
    private LocalDateTime timestamp;

    private int code;
 
    // ── Constructor privado — usar factory methods ─────────────────────
 
    private ApiResponse(boolean success,  int code, String message, T data) {
        this.success   = success;
        this.code      = code;
        this.message   = message;
        this.data      = data;
        this.timestamp = LocalDateTime.now();
    }
 
    // ── Factory methods ────────────────────────────────────────────────
 
    /** Respuesta exitosa con datos y mensaje. */
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, 0, message, data);
    }
 
    /** Respuesta exitosa solo con datos (mensaje genérico). */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, 0, "Operación exitosa", data);
    }
 
    /** Respuesta exitosa sin datos (ej: DELETE). */
    public static <T> ApiResponse<T> ok(String message) {
        return new ApiResponse<>(true, 0, message, null);
    }
 
    /** Respuesta de error con mensaje. */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, 0, message, null);
    }
 
    /** Respuesta de error con datos adicionales (ej: errores de validación). */
    public static <T> ApiResponse<T> error(String message, T data) {
        return new ApiResponse<>(false, 0, message, data);
    }

}
