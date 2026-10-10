package com.uap.controlMedico.model.service.londra;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.uap.controlMedico.model.entity.Interno;
import com.uap.controlMedico.model.entity.Titular;

@Service
public class LondraServiceImpl implements LondraService {
    
    @Value("${sisu.londra.base-url:http://localhost:8080/londra/api}")
    private String londraBaseUrl;
 
    @Override
    public void sincronizarTitular(Titular titular) {
        // TODO: Implementar llamada HTTP real a Londra
    }
 
    @Override
    public void sincronizarInterno(Interno interno) {
        // TODO: Implementar llamada HTTP real a Londra
    }

}
