package com.uap.controlMedico.model.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.uap.controlMedico.model.entity.dto.PersonaDTO;


import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

@Service
public class ConsultaLondraService {
    private final RestTemplate restTemplate;
    //private final String API_URL = "http://172.16.21.3:8003"; 
    private final String API_URL = "http://virtual.uap.edu.bo:8003"; 

    public ConsultaLondraService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<PersonaDTO> obtenerListaPersonas() {
        String metodoUrl = API_URL + "/persona/optenerLista";

        HttpHeaders headers = crearHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<PersonaDTO[]> response = restTemplate.exchange(
                metodoUrl,
                HttpMethod.POST,
                entity,
                PersonaDTO[].class);
        return Arrays.asList(response.getBody());
    }

    public PersonaDTO optenerPersona(int perId) {

        String metodoUrl = API_URL + "/persona/optenerPersona/" + perId;

        HttpHeaders headers = crearHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<PersonaDTO> response = restTemplate.exchange(
                metodoUrl,
                HttpMethod.POST,
                entity,
                PersonaDTO.class);

        return response.getBody();
    }    

    private HttpHeaders crearHeaders() {

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth("ControlSeguimiento", "ControlSeguimientoasdasdas1q23215ef%&d");

        return headers;
    }
}
