package com.uap.controlMedico.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Pool chico y separado para envíos a Telegram: si la red hacia api.telegram.org
     * está bloqueada o lenta, estos threads se quedan esperando pero nunca son los
     * threads que atienden requests HTTP ni los de los @Scheduled de monitoreo/asistencia.
     */
    @Bean(name = "telegramExecutor")
    public Executor telegramExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("telegram-");
        executor.initialize();
        return executor;
    }
}
