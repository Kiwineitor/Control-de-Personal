package com.uap.controlMedico.model.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.uap.controlMedico.model.service.ausencia.AusenciaService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class  AusenciaSchedulerService {
    
    private final AusenciaService ausenciaService;

    /** Verifica ausencias cada 5 minutos */
    @Scheduled(fixedDelay = 300_000)
    public void verificar() {
        log.debug("Scheduler: verificando ausencias...");
        ausenciaService.verificarAusencias();
    }
}
