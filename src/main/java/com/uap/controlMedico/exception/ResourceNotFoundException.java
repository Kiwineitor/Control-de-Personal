package com.uap.controlMedico.exception;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {
    
    public ResourceNotFoundException(String mensaje) {
        super(mensaje);
    }
 
    public ResourceNotFoundException(String entidad, Long id) {
        super(entidad + " con id " + id + " no encontrado");
    }
 
    public ResourceNotFoundException(String entidad, String campo, Object valor) {
        super(entidad + " con " + campo + " '" + valor + "' no encontrado");
    }

}
