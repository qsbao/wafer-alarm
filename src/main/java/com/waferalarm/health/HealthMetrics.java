package com.waferalarm.health;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

@Component
public class HealthMetrics implements MeterBinder {

    private final HealthService healthService;

    public HealthMetrics(HealthService healthService) {
        this.healthService = healthService;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("wafer.health.stalled_connectors", healthService,
                        hs -> hs.buildReport().connectors().stream().filter(ConnectorHealth::stalled).count())
                .description("Number of stalled connectors")
                .register(registry);

        Gauge.builder("wafer.health.connector_errors", healthService,
                        hs -> hs.buildReport().connectors().stream().mapToLong(ConnectorHealth::errorCount).sum())
                .description("Total connector error count (24h)")
                .register(registry);

        Gauge.builder("wafer.health.eval_lag_seconds", healthService,
                        hs -> hs.buildReport().evaluator().watermarkLagSeconds())
                .description("Evaluator watermark lag in seconds")
                .register(registry);

        Gauge.builder("wafer.health.eval_errors", healthService,
                        hs -> hs.buildReport().evaluator().errorCount())
                .description("Evaluator error count (24h)")
                .register(registry);
    }
}
