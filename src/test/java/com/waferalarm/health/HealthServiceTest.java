package com.waferalarm.health;

import com.waferalarm.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class HealthServiceTest {

    @Autowired HealthService healthService;
    @Autowired SourceMappingRepository mappingRepo;
    @Autowired SourceSystemRepository sourceSystemRepo;
    @Autowired ParameterRepository parameterRepo;
    @Autowired ConnectorRunRepository connectorRunRepo;
    @Autowired EvalRunRepository evalRunRepo;
    @Autowired EvalWatermarkRepository evalWatermarkRepo;

    @Autowired AlarmRepository alarmRepo;
    @Autowired RuleStateRepository ruleStateRepo;
    @Autowired RuleVersionRepository ruleVersionRepo;
    @Autowired ParameterLimitRepository parameterLimitRepo;
    @Autowired RuleRepository ruleRepo;
    @Autowired MeasurementRepository measurementRepo;
    @Autowired StagingUnmappedRepository unmappedRepo;
    @Autowired StagingDismissedRepository dismissedRepo;

    @BeforeEach
    void clean() {
        connectorRunRepo.deleteAll();
        evalRunRepo.deleteAll();
        evalWatermarkRepo.deleteAll();
        alarmRepo.deleteAll();
        ruleStateRepo.deleteAll();
        measurementRepo.deleteAll();
        parameterLimitRepo.deleteAll();
        ruleVersionRepo.deleteAll();
        ruleRepo.deleteAll();
        mappingRepo.deleteAll();
        unmappedRepo.deleteAll();
        dismissedRepo.deleteAll();
        sourceSystemRepo.deleteAll();
        parameterRepo.deleteAll();
    }

    private SourceMappingEntity createMapping() {
        SourceSystemEntity ss = new SourceSystemEntity(
                "test-src-" + System.nanoTime(), "localhost", 3306,
                "testdb", "none", "zone-a", "UTC");
        ss = sourceSystemRepo.save(ss);

        ParameterEntity p = new ParameterEntity("param-" + System.nanoTime(), "nm", null, null);
        p = parameterRepo.save(p);

        SourceMappingEntity m = new SourceMappingEntity(
                ss.getId(), p.getId(), "SELECT 1", "val", "ts", null, 60, 1000, 30);
        return mappingRepo.save(m);
    }

    @Test
    void connectorHealthShowsLastSuccessfulTickAndRowsPulled() {
        SourceMappingEntity mapping = createMapping();
        Instant now = Instant.now();

        // Two successful runs
        connectorRunRepo.save(new ConnectorRunEntity(
                mapping.getId(), now.minus(Duration.ofMinutes(5)), now.minus(Duration.ofMinutes(4)),
                100, 60000, null));
        connectorRunRepo.save(new ConnectorRunEntity(
                mapping.getId(), now.minus(Duration.ofMinutes(1)), now,
                50, 30000, null));

        HealthReport report = healthService.buildReport();

        assertThat(report.connectors()).hasSize(1);
        ConnectorHealth ch = report.connectors().get(0);
        assertThat(ch.sourceMappingId()).isEqualTo(mapping.getId());
        assertThat(ch.lastSuccessfulTick()).isNotNull();
        assertThat(ch.totalRowsPulled()).isEqualTo(150);
        assertThat(ch.errorCount()).isZero();
    }

    @Test
    void connectorHealthShowsErrorCount() {
        SourceMappingEntity mapping = createMapping();
        Instant now = Instant.now();

        connectorRunRepo.save(new ConnectorRunEntity(
                mapping.getId(), now.minus(Duration.ofMinutes(3)), now.minus(Duration.ofMinutes(2)),
                0, 1000, "connection refused"));
        connectorRunRepo.save(new ConnectorRunEntity(
                mapping.getId(), now.minus(Duration.ofMinutes(1)), now,
                10, 500, null));

        HealthReport report = healthService.buildReport();

        ConnectorHealth ch = report.connectors().get(0);
        assertThat(ch.errorCount()).isEqualTo(1);
        assertThat(ch.totalRowsPulled()).isEqualTo(10);
    }

    @Test
    void stalledConnectorDetectedWhenZeroRowsForNTicks() {
        SourceMappingEntity mapping = createMapping();
        Instant now = Instant.now();

        // 3 consecutive runs with zero rows
        for (int i = 3; i >= 1; i--) {
            connectorRunRepo.save(new ConnectorRunEntity(
                    mapping.getId(),
                    now.minus(Duration.ofMinutes(i)),
                    now.minus(Duration.ofMinutes(i)).plus(Duration.ofSeconds(30)),
                    0, 30000, null));
        }

        HealthReport report = healthService.buildReport();

        ConnectorHealth ch = report.connectors().get(0);
        assertThat(ch.stalled()).isTrue();
        assertThat(ch.consecutiveZeroRowTicks()).isEqualTo(3);
    }

    @Test
    void connectorNotStalledWhenRecentTickHasRows() {
        SourceMappingEntity mapping = createMapping();
        Instant now = Instant.now();

        connectorRunRepo.save(new ConnectorRunEntity(
                mapping.getId(), now.minus(Duration.ofMinutes(2)), now.minus(Duration.ofMinutes(1)),
                0, 30000, null));
        connectorRunRepo.save(new ConnectorRunEntity(
                mapping.getId(), now.minus(Duration.ofMinutes(1)), now,
                5, 30000, null));

        HealthReport report = healthService.buildReport();
        assertThat(report.connectors().get(0).stalled()).isFalse();
    }

    @Test
    void evaluatorHealthShowsLastTickAndWatermarkLag() {
        Instant now = Instant.now();

        evalRunRepo.save(new EvalRunEntity(
                now.minus(Duration.ofMinutes(2)), now.minus(Duration.ofMinutes(1)),
                100, 5, 60000, null));

        EvalWatermark wm = new EvalWatermark("evaluator-main", now.minus(Duration.ofMinutes(3)));
        evalWatermarkRepo.save(wm);

        HealthReport report = healthService.buildReport();

        assertThat(report.evaluator()).isNotNull();
        assertThat(report.evaluator().lastTick()).isNotNull();
        assertThat(report.evaluator().watermarkLagSeconds()).isGreaterThan(0);
    }

    @Test
    void evaluatorHealthHandlesNoRuns() {
        HealthReport report = healthService.buildReport();

        assertThat(report.evaluator()).isNotNull();
        assertThat(report.evaluator().lastTick()).isNull();
    }

    @Test
    void reportIncludesMultipleConnectors() {
        SourceMappingEntity m1 = createMapping();
        SourceMappingEntity m2 = createMapping();
        Instant now = Instant.now();

        connectorRunRepo.save(new ConnectorRunEntity(
                m1.getId(), now.minus(Duration.ofMinutes(1)), now, 10, 500, null));
        connectorRunRepo.save(new ConnectorRunEntity(
                m2.getId(), now.minus(Duration.ofMinutes(1)), now, 20, 500, null));

        HealthReport report = healthService.buildReport();
        assertThat(report.connectors()).hasSize(2);
    }
}
