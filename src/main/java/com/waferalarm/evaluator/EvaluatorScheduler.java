package com.waferalarm.evaluator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;

@Component
public class EvaluatorScheduler {

    private static final Logger log = LoggerFactory.getLogger(EvaluatorScheduler.class);

    private final EvaluatorRunner runner;
    private final ExecutorService evaluatorExecutor;

    public EvaluatorScheduler(
            EvaluatorRunner runner,
            @Qualifier("evaluatorExecutor") ExecutorService evaluatorExecutor) {
        this.runner = runner;
        this.evaluatorExecutor = evaluatorExecutor;
    }

    @Scheduled(fixedDelayString = "${app.evaluator.tick-interval-seconds:60}000")
    public void evaluate() {
        evaluatorExecutor.submit(() -> {
            try {
                runner.tick();
            } catch (Exception e) {
                log.error("Evaluator tick failed", e);
            }
        });
    }
}
