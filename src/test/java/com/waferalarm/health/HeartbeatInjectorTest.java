package com.waferalarm.health;

import com.waferalarm.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class HeartbeatInjectorTest {

    @Autowired HeartbeatInjector heartbeatInjector;
    @Autowired ParameterRepository parameterRepo;
    @Autowired MeasurementRepository measurementRepo;
    @Autowired RuleRepository ruleRepo;

    @Autowired AlarmRepository alarmRepo;
    @Autowired RuleStateRepository ruleStateRepo;
    @Autowired RuleVersionRepository ruleVersionRepo;
    @Autowired ParameterLimitRepository parameterLimitRepo;
    @Autowired ConnectorRunRepository connectorRunRepo;
    @Autowired SourceMappingRepository sourceMappingRepo;

    @BeforeEach
    void clean() {
        alarmRepo.deleteAll();
        ruleStateRepo.deleteAll();
        connectorRunRepo.deleteAll();
        measurementRepo.deleteAll();
        parameterLimitRepo.deleteAll();
        ruleVersionRepo.deleteAll();
        ruleRepo.deleteAll();
        sourceMappingRepo.deleteAll();
        parameterRepo.deleteAll();
    }

    @Test
    void ensureSyntheticParameterCreatesParameterIfMissing() {
        heartbeatInjector.ensureSyntheticSetup();

        ParameterEntity param = parameterRepo.findAll().stream()
                .filter(p -> p.getName().equals("__heartbeat__"))
                .findFirst()
                .orElse(null);
        assertThat(param).isNotNull();
        assertThat(param.getUnit()).isEqualTo("heartbeat");
    }

    @Test
    void ensureSyntheticParameterIsIdempotent() {
        heartbeatInjector.ensureSyntheticSetup();
        heartbeatInjector.ensureSyntheticSetup();

        long count = parameterRepo.findAll().stream()
                .filter(p -> p.getName().equals("__heartbeat__"))
                .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void ensureSyntheticRuleCreated() {
        heartbeatInjector.ensureSyntheticSetup();

        List<RuleEntity> rules = ruleRepo.findAll();
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).getRuleType()).isEqualTo(RuleType.UPPER_THRESHOLD);
        assertThat(rules.get(0).getSeverity()).isEqualTo(Severity.CRITICAL);
    }

    @Test
    void injectHeartbeatInsertsMeasurement() {
        heartbeatInjector.ensureSyntheticSetup();
        heartbeatInjector.injectHeartbeat();

        List<MeasurementEntity> measurements = measurementRepo.findAll();
        assertThat(measurements).hasSize(1);
        assertThat(measurements.get(0).getValue()).isEqualTo(1.0);
        assertThat(measurements.get(0).getWaferId()).startsWith("heartbeat-");
    }

    @Test
    void multipleHeartbeatsProduceMultipleMeasurements() {
        heartbeatInjector.ensureSyntheticSetup();
        heartbeatInjector.injectHeartbeat();
        heartbeatInjector.injectHeartbeat();

        assertThat(measurementRepo.findAll()).hasSize(2);
    }
}
