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
}
