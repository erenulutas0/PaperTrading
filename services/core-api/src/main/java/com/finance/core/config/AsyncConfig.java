package com.finance.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Core pool limits minimum simultaneous threads
        executor.setCorePoolSize(10);
        // Max pool size indicates maximum concurrent tasks execution
        executor.setMaxPoolSize(50);
        // Queue capacity before new threads are created or executions rejected
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("CoreApi-Async-");
        executor.initialize();
        return executor;
    }
}
