package com.waferalarm.evaluator;

import com.waferalarm.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RuleBacktesterTest {

    @Autowired private RuleBacktester backtester;
    @Autowired private MeasurementRepository measurementRepo;
    @Autowired private ParameterLimitRepository limitRepo;
    @Autowired private ParameterRepository parameterRepo;
    @Autowired private RuleRepository ruleRepo;
    @Autowired private AlarmRepository alarmRepo;
    @Autowired private RuleStateRepository ruleStateRepo;

    private ParameterEntity parameter;

    @BeforeEach
    void setUp() {
        alarmRepo.deleteAll();
        ruleStateRepo.deleteAll();
        ruleRepo.deleteAll();
        measurementRepo.deleteAll();
        limitRepo.deleteAll();
        parameterRepo.deleteAll();

        parameter = parameterRepo.save(new ParameterEntity("Thickness", "nm", null, null));
    }

    @Test
    void threshold_backtest_returns_violations() {
        // Set up limits
        limitRepo.save(new ParameterLimitEntity(parameter.getId(), "{}", 100.0, null));

        // Insert measurements: two above limit, one below
        Instant now = Instant.now();
        measurementRepo.save(new MeasurementEntity(parameter.getId(), "W001", 101.0,
                now.minus(3, ChronoUnit.DAYS), "TOOL-A", "RCP-1", "PROD-X", "LOT-1"));
        measurementRepo.save(new MeasurementEntity(parameter.getId(), "W002", 99.0,
                now.minus(2, ChronoUnit.DAYS), "TOOL-A", "RCP-1", "PROD-X", "LOT-1"));
        measurementRepo.save(new MeasurementEntity(parameter.getId(), "W003", 105.0,
                now.minus(1, ChronoUnit.DAYS), "TOOL-A", "RCP-1", "PROD-X", "LOT-1"));

        // Build a candidate rule (not persisted — simulating form state)
        var request = new BacktestRequest(
                parameter.getId(),
                RuleType.UPPER_THRESHOLD,
                Severity.WARNING,
                null, null, null,
                now.minus(7, ChronoUnit.DAYS),
                now
        );

        BacktestResult result = backtester.run(request);

        assertThat(result.totalViolations()).isEqualTo(2);
        assertThat(result.severityBreakdown()).containsEntry("WARNING", 2L);
        assertThat(result.violations()).hasSize(2);
        assertThat(result.violations()).extracting(BacktestViolation::waferId)
                .containsExactly("W001", "W003");
    }

    @Test
    void roc_backtest_returns_violations() {
        Instant now = Instant.now();
        // Two measurements with large delta
        measurementRepo.save(new MeasurementEntity(parameter.getId(), "W001", 50.0,
                now.minus(3, ChronoUnit.DAYS), "TOOL-A", "RCP-1", "PROD-X", "LOT-1"));
        measurementRepo.save(new MeasurementEntity(parameter.getId(), "W002", 70.0,
                now.minus(2, ChronoUnit.DAYS), "TOOL-A", "RCP-1", "PROD-X", "LOT-1"));
        measurementRepo.save(new MeasurementEntity(parameter.getId(), "W003", 72.0,
                now.minus(1, ChronoUnit.DAYS), "TOOL-A", "RCP-1", "PROD-X", "LOT-1"));

        var request = new BacktestRequest(
                parameter.getId(),
                RuleType.RATE_OF_CHANGE,
                Severity.CRITICAL,
                10.0, null, null, // absolute delta > 10
                now.minus(7, ChronoUnit.DAYS),
                now
        );

        BacktestResult result = backtester.run(request);

        // W002 has delta 20 from W001 (>10), W003 has delta 2 from W002 (<=10)
        assertThat(result.totalViolations()).isEqualTo(1);
        assertThat(result.severityBreakdown()).containsEntry("CRITICAL", 1L);
        assertThat(result.violations().getFirst().waferId()).isEqualTo("W002");
    }

    @Test
    void backtest_does_not_persist_alarms_or_rule_state() {
        limitRepo.save(new ParameterLimitEntity(parameter.getId(), "{}", 100.0, null));

        Instant now = Instant.now();
        measurementRepo.save(new MeasurementEntity(parameter.getId(), "W001", 150.0,
                now.minus(1, ChronoUnit.DAYS), "TOOL-A", "RCP-1", "PROD-X", "LOT-1"));

        var request = new BacktestRequest(
                parameter.getId(),
                RuleType.UPPER_THRESHOLD,
                Severity.WARNING,
                null, null, null,
                now.minus(7, ChronoUnit.DAYS),
                now
        );

        BacktestResult result = backtester.run(request);

        assertThat(result.totalViolations()).isEqualTo(1);
        // Verify no alarms or rule_state rows were created
        assertThat(alarmRepo.findAll()).isEmpty();
        assertThat(ruleStateRepo.findAll()).isEmpty();
    }

    @Test
    void backtest_with_no_matching_measurements_returns_empty() {
        limitRepo.save(new ParameterLimitEntity(parameter.getId(), "{}", 100.0, null));

        Instant now = Instant.now();
        // No measurements in the window

        var request = new BacktestRequest(
                parameter.getId(),
                RuleType.UPPER_THRESHOLD,
                Severity.WARNING,
                null, null, null,
                now.minus(7, ChronoUnit.DAYS),
                now
        );

        BacktestResult result = backtester.run(request);

        assertThat(result.totalViolations()).isEqualTo(0);
        assertThat(result.severityBreakdown()).isEmpty();
        assertThat(result.violations()).isEmpty();
    }
}
