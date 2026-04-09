package com.waferalarm.evaluator;

import com.waferalarm.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class EvaluatorRunnerIntegrationTest {

    @Autowired ParameterRepository parameterRepo;
    @Autowired MeasurementRepository measurementRepo;
    @Autowired RuleRepository ruleRepo;
    @Autowired AlarmRepository alarmRepo;
    @Autowired EvalWatermarkRepository watermarkRepo;
    @Autowired EvaluatorRunner runner;

    @BeforeEach
    void cleanDb() {
        alarmRepo.deleteAll();
        measurementRepo.deleteAll();
        ruleRepo.deleteAll();
        watermarkRepo.deleteAll();
        parameterRepo.deleteAll();
    }

    @Test
    void tick_creates_alarm_for_measurement_exceeding_upper_threshold() {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        ruleRepo.save(new RuleEntity(param.getId(), RuleType.UPPER_THRESHOLD, Severity.WARNING));

        var m = new MeasurementEntity(param.getId(), "W001", 105.0,
                Instant.parse("2026-01-01T00:00:00Z"), "TOOL-A", "RCP-1", "PROD-X", "LOT-1");
        measurementRepo.save(m);

        runner.tick();

        List<AlarmEntity> alarms = alarmRepo.findAll();
        assertThat(alarms).hasSize(1);
        AlarmEntity alarm = alarms.getFirst();
        assertThat(alarm.getState()).isEqualTo(AlarmState.FIRING);
        assertThat(alarm.getLastValue()).isEqualTo(105.0);
        assertThat(alarm.getThresholdValue()).isEqualTo(100.0);
        assertThat(alarm.getOccurrenceCount()).isEqualTo(1);
    }

    @Test
    void tick_does_not_create_alarm_for_measurement_within_limit() {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        ruleRepo.save(new RuleEntity(param.getId(), RuleType.UPPER_THRESHOLD, Severity.WARNING));

        var m = new MeasurementEntity(param.getId(), "W002", 95.0,
                Instant.parse("2026-01-01T00:00:00Z"), "TOOL-A", "RCP-1", "PROD-X", "LOT-1");
        measurementRepo.save(m);

        runner.tick();

        assertThat(alarmRepo.findAll()).isEmpty();
    }

    @Test
    void watermark_prevents_re_evaluation_of_same_measurements() {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        ruleRepo.save(new RuleEntity(param.getId(), RuleType.UPPER_THRESHOLD, Severity.WARNING));

        var m = new MeasurementEntity(param.getId(), "W003", 105.0,
                Instant.parse("2026-01-01T00:00:00Z"), "TOOL-A", "RCP-1", "PROD-X", "LOT-1");
        measurementRepo.save(m);

        runner.tick();
        assertThat(alarmRepo.findAll()).hasSize(1);

        // Second tick: same measurement should not create a duplicate alarm
        runner.tick();
        assertThat(alarmRepo.findAll()).hasSize(1);
        assertThat(alarmRepo.findAll().getFirst().getOccurrenceCount()).isEqualTo(1);
    }

    @Test
    void second_violation_accumulates_on_existing_alarm() {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        ruleRepo.save(new RuleEntity(param.getId(), RuleType.UPPER_THRESHOLD, Severity.WARNING));

        measurementRepo.save(new MeasurementEntity(param.getId(), "W004", 105.0,
                Instant.parse("2026-01-01T00:00:00Z"), "TOOL-A", "RCP-1", "PROD-X", "LOT-1"));
        runner.tick();

        // New measurement arrives after the watermark
        measurementRepo.save(new MeasurementEntity(param.getId(), "W005", 110.0,
                Instant.parse("2026-01-01T01:00:00Z"), "TOOL-A", "RCP-1", "PROD-X", "LOT-1"));
        runner.tick();

        List<AlarmEntity> alarms = alarmRepo.findAll();
        assertThat(alarms).hasSize(1);
        assertThat(alarms.getFirst().getOccurrenceCount()).isEqualTo(2);
    }
}
