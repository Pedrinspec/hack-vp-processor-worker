package com.fiap.vp_processor_worker.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AsyncConfig {

    @Bean
    public ExecutorService videoExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
