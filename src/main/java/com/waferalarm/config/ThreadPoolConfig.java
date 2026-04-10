package com.waferalarm.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableScheduling
public class ThreadPoolConfig {

    @Bean(name = "collectorExecutor")
    public ExecutorService collectorExecutor(
            @Value("${app.collector.pool-size:2}") int poolSize) {
        return Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "collector-worker");
            t.setDaemon(true);
            return t;
        });
    }

    @Bean(name = "evaluatorExecutor")
    public ExecutorService evaluatorExecutor(
            @Value("${app.evaluator.pool-size:2}") int poolSize) {
        return Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "evaluator-worker");
            t.setDaemon(true);
            return t;
        });
    }

    @Bean(name = "backfillExecutor")
    public ExecutorService backfillExecutor(
            @Value("${app.backfill.pool-size:1}") int poolSize) {
        return Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "backfill-worker");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
    }
}
