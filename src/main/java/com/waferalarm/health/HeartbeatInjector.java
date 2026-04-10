package com.waferalarm.health;

import com.waferalarm.domain.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class HeartbeatInjector {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatInjector.class);
    static final String HEARTBEAT_PARAMETER_NAME = "__heartbeat__";

    private final ParameterRepository parameterRepo;
    private final MeasurementRepository measurementRepo;
    private final RuleRepository ruleRepo;

    public HeartbeatInjector(ParameterRepository parameterRepo,
                             MeasurementRepository measurementRepo,
                             RuleRepository ruleRepo) {
        this.parameterRepo = parameterRepo;
        this.measurementRepo = measurementRepo;
        this.ruleRepo = ruleRepo;
    }

    @PostConstruct
    public void ensureSyntheticSetup() {
        ParameterEntity param = parameterRepo.findByName(HEARTBEAT_PARAMETER_NAME)
                .orElseGet(() -> {
                    ParameterEntity p = new ParameterEntity(HEARTBEAT_PARAMETER_NAME, "heartbeat", 0.5, null);
                    return parameterRepo.save(p);
                });

        boolean hasRule = ruleRepo.findByEnabledTrue().stream()
                .anyMatch(r -> r.getParameterId().equals(param.getId()));

        if (!hasRule) {
            RuleEntity rule = new RuleEntity(param.getId(), RuleType.UPPER_THRESHOLD, Severity.CRITICAL);
            ruleRepo.save(rule);
            log.info("Created synthetic heartbeat rule for parameter {}", param.getId());
        }
    }

    @Scheduled(fixedDelayString = "${app.heartbeat.interval-seconds:60}000")
    public void injectHeartbeat() {
        ParameterEntity param = parameterRepo.findByName(HEARTBEAT_PARAMETER_NAME).orElse(null);
        if (param == null) {
            log.warn("Heartbeat parameter not found; skipping injection");
            return;
        }

        String waferId = "heartbeat-" + UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now();
        MeasurementEntity m = new MeasurementEntity(
                param.getId(), waferId, 1.0, now, null, null, null, null);
        measurementRepo.save(m);
        log.debug("Injected heartbeat measurement: {}", waferId);
    }
}
