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
    @Autowired RuleVersionRepository ruleVersionRepo;
    @Autowired ParameterLimitRepository parameterLimitRepo;
    @Autowired AlarmRepository alarmRepo;
    @Autowired EvalWatermarkRepository watermarkRepo;
    @Autowired RuleStateRepository ruleStateRepo;
    @Autowired EvaluatorRunner runner;

    @BeforeEach
    void cleanDb() {
        alarmRepo.deleteAll();
        ruleStateRepo.deleteAll();
        measurementRepo.deleteAll();
        parameterLimitRepo.deleteAll();
        ruleVersionRepo.deleteAll();
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

    @Test
    void alarm_auto_closes_after_consecutive_clean_wafers() {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        ruleRepo.save(new RuleEntity(param.getId(), RuleType.UPPER_THRESHOLD, Severity.WARNING));

        // First: create an alarm with a violating measurement
        measurementRepo.save(new MeasurementEntity(param.getId(), "W010", 105.0,
                Instant.parse("2026-01-01T00:00:00Z"), "TOOL-A", "RCP-1", "PROD-X", "LOT-1"));
        runner.tick();
        assertThat(alarmRepo.findAll()).hasSize(1);
        assertThat(alarmRepo.findAll().getFirst().getState()).isEqualTo(AlarmState.FIRING);

        // 3 consecutive clean wafers (default threshold = 3)
        measurementRepo.save(new MeasurementEntity(param.getId(), "W011", 95.0,
                Instant.parse("2026-01-01T01:00:00Z"), "TOOL-A", "RCP-1", "PROD-X", "LOT-1"));
        runner.tick();
        assertThat(alarmRepo.findAll().getFirst().getConsecutiveCleanCount()).isEqualTo(1);

        measurementRepo.save(new MeasurementEntity(param.getId(), "W012", 90.0,
                Instant.parse("2026-01-01T02:00:00Z"), "TOOL-A", "RCP-1", "PROD-X", "LOT-1"));
        runner.tick();
        assertThat(alarmRepo.findAll().getFirst().getConsecutiveCleanCount()).isEqualTo(2);

        measurementRepo.save(new MeasurementEntity(param.getId(), "W013", 88.0,
                Instant.parse("2026-01-01T03:00:00Z"), "TOOL-A", "RCP-1", "PROD-X", "LOT-1"));
        runner.tick();

        AlarmEntity alarm = alarmRepo.findAll().getFirst();
        assertThat(alarm.getState()).isEqualTo(AlarmState.RESOLVED);
        assertThat(alarm.getConsecutiveCleanCount()).isEqualTo(3);
    }

    @Test
    void suppressed_alarm_does_not_refire_within_window() {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        ruleRepo.save(new RuleEntity(param.getId(), RuleType.UPPER_THRESHOLD, Severity.WARNING));

        // Create alarm
        measurementRepo.save(new MeasurementEntity(param.getId(), "W020", 105.0,
                Instant.parse("2026-01-01T00:00:00Z"), "TOOL-A", "RCP-1", "PROD-X", "LOT-1"));
        runner.tick();

        // Suppress until far future
        AlarmEntity alarm = alarmRepo.findAll().getFirst();
        alarm.updateFromSnapshot(alarm.toSnapshot()
                .withState(AlarmState.SUPPRESSED));
        // Set suppressed_until manually on the entity
        AlarmSnapshot suppressed = new AlarmSnapshot(
                alarm.getId(), alarm.getRuleId(), alarm.getParameterId(),
                alarm.getContextKey(), AlarmState.SUPPRESSED, alarm.getSeverity(),
                alarm.getOccurrenceCount(), alarm.getFirstViolationAt(),
                alarm.getLastViolationAt(), alarm.getLastValue(),
                alarm.getThresholdValue(), 0,
                Instant.parse("2099-01-01T00:00:00Z"), null);
        alarm.updateFromSnapshot(suppressed);
        alarmRepo.save(alarm);

        // Another violation arrives — should not create new alarm
        measurementRepo.save(new MeasurementEntity(param.getId(), "W021", 110.0,
                Instant.parse("2026-01-01T01:00:00Z"), "TOOL-A", "RCP-1", "PROD-X", "LOT-1"));
        runner.tick();

        List<AlarmEntity> alarms = alarmRepo.findAll();
        assertThat(alarms).hasSize(1);
        assertThat(alarms.getFirst().getState()).isEqualTo(AlarmState.SUPPRESSED);
    }

    // --- Per-context limit overrides global (AC2) ---

    @Test
    void context_specific_limit_overrides_global_default_at_evaluation_time() {
        // Global default: upper=100 (from parameter catalog)
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        ruleRepo.save(new RuleEntity(param.getId(), RuleType.UPPER_THRESHOLD, Severity.WARNING));

        // Context-specific limit: tool=TOOL-A, recipe=RCP-1 → upper=120
        parameterLimitRepo.save(new ParameterLimitEntity(
                param.getId(), "{\"tool\":\"TOOL-A\",\"recipe\":\"RCP-1\"}", 120.0, null));

        // Measurement: value=110, matches the specific context
        // Should NOT fire because 110 < 120 (specific limit), even though 110 > 100 (global)
        measurementRepo.save(new MeasurementEntity(param.getId(), "W100", 110.0,
                Instant.parse("2026-01-01T00:00:00Z"), "TOOL-A", "RCP-1", "PROD-X", "LOT-1"));
        runner.tick();

        assertThat(alarmRepo.findAll()).isEmpty();
    }

    @Test
    void global_limit_still_fires_when_no_specific_limit_matches() {
        // Global default: upper=100
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        ruleRepo.save(new RuleEntity(param.getId(), RuleType.UPPER_THRESHOLD, Severity.WARNING));

        // Context-specific limit for TOOL-A/RCP-1 only
        parameterLimitRepo.save(new ParameterLimitEntity(
                param.getId(), "{\"tool\":\"TOOL-A\",\"recipe\":\"RCP-1\"}", 120.0, null));

        // Measurement from TOOL-B: no specific limit → uses global 100 → fires
        measurementRepo.save(new MeasurementEntity(param.getId(), "W101", 110.0,
                Instant.parse("2026-01-01T00:00:00Z"), "TOOL-B", "RCP-2", "PROD-X", "LOT-1"));
        runner.tick();

        List<AlarmEntity> alarms = alarmRepo.findAll();
        assertThat(alarms).hasSize(1);
        assertThat(alarms.getFirst().getThresholdValue()).isEqualTo(100.0);
    }

    // --- Limit deletion fallback (AC5) ---

    @Test
    void deleting_specific_limit_causes_fallback_to_global() {
        // Global default: upper=100
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        ruleRepo.save(new RuleEntity(param.getId(), RuleType.UPPER_THRESHOLD, Severity.WARNING));

        // Context-specific limit: upper=120 for TOOL-A/RCP-1
        var specificLimit = parameterLimitRepo.save(new ParameterLimitEntity(
                param.getId(), "{\"tool\":\"TOOL-A\",\"recipe\":\"RCP-1\"}", 120.0, null));

        // value=110 with specific limit (120) → no alarm
        measurementRepo.save(new MeasurementEntity(param.getId(), "W200", 110.0,
                Instant.parse("2026-01-01T00:00:00Z"), "TOOL-A", "RCP-1", "PROD-X", "LOT-1"));
        runner.tick();
        assertThat(alarmRepo.findAll()).isEmpty();

        // Delete the specific limit → falls back to global (100)
        parameterLimitRepo.deleteById(specificLimit.getId());

        // Same value=110 now exceeds global limit 100 → fires
        measurementRepo.save(new MeasurementEntity(param.getId(), "W201", 110.0,
                Instant.parse("2026-01-01T01:00:00Z"), "TOOL-A", "RCP-1", "PROD-X", "LOT-1"));
        runner.tick();

        List<AlarmEntity> alarms = alarmRepo.findAll();
        assertThat(alarms).hasSize(1);
        assertThat(alarms.getFirst().getThresholdValue()).isEqualTo(100.0);
    }

    // --- Rate-of-change integration tests ---

    @Test
    void roc_first_measurement_does_not_fire() {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", null, null));
        var rule = new RuleEntity(param.getId(), RuleType.RATE_OF_CHANGE, Severity.WARNING);
        rule.setAbsoluteDelta(5.0);
        ruleRepo.save(rule);

        measurementRepo.save(new MeasurementEntity(param.getId(), "W001", 50.0,
                Instant.parse("2026-01-01T00:00:00Z"), "TOOL-A", "RCP-1", "PROD-X", "LOT-1"));
        runner.tick();

        assertThat(alarmRepo.findAll()).isEmpty();
        // But state should be persisted
        assertThat(ruleStateRepo.findAll()).hasSize(1);
        assertThat(ruleStateRepo.findAll().getFirst().getLastValue()).isEqualTo(50.0);
    }

    @Test
    void roc_fires_when_delta_exceeds_threshold_across_ticks() {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", null, null));
        var rule = new RuleEntity(param.getId(), RuleType.RATE_OF_CHANGE, Severity.WARNING);
        rule.setAbsoluteDelta(5.0);
        ruleRepo.save(rule);

        // First tick: baseline
        measurementRepo.save(new MeasurementEntity(param.getId(), "W001", 50.0,
                Instant.parse("2026-01-01T00:00:00Z"), "TOOL-A", "RCP-1", "PROD-X", "LOT-1"));
        runner.tick();
        assertThat(alarmRepo.findAll()).isEmpty();

        // Second tick: delta = 10 > 5 threshold
        measurementRepo.save(new MeasurementEntity(param.getId(), "W002", 60.0,
                Instant.parse("2026-01-01T01:00:00Z"), "TOOL-A", "RCP-1", "PROD-X", "LOT-1"));
        runner.tick();

        List<AlarmEntity> alarms = alarmRepo.findAll();
        assertThat(alarms).hasSize(1);
        assertThat(alarms.getFirst().getState()).isEqualTo(AlarmState.FIRING);
        assertThat(alarms.getFirst().getLastValue()).isEqualTo(60.0);
    }

    @Test
    void roc_state_persists_across_ticks_no_false_fire_after_restart() {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", null, null));
        var rule = new RuleEntity(param.getId(), RuleType.RATE_OF_CHANGE, Severity.WARNING);
        rule.setAbsoluteDelta(5.0);
        ruleRepo.save(rule);

        // First tick: baseline
        measurementRepo.save(new MeasurementEntity(param.getId(), "W001", 50.0,
                Instant.parse("2026-01-01T00:00:00Z"), "TOOL-A", "RCP-1", "PROD-X", "LOT-1"));
        runner.tick();

        // Second tick: within threshold (delta=3)
        measurementRepo.save(new MeasurementEntity(param.getId(), "W002", 53.0,
                Instant.parse("2026-01-01T01:00:00Z"), "TOOL-A", "RCP-1", "PROD-X", "LOT-1"));
        runner.tick();

        assertThat(alarmRepo.findAll()).isEmpty();
        // State should show the latest value
        RuleStateEntity state = ruleStateRepo.findAll().getFirst();
        assertThat(state.getLastValue()).isEqualTo(53.0);
        assertThat(state.getLastWaferId()).isEqualTo("W002");
    }
}
