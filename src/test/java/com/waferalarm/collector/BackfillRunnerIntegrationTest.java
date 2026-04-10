package com.waferalarm.collector;

import com.waferalarm.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class BackfillRunnerIntegrationTest {

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ParameterRepository parameterRepo;
    @Autowired SourceSystemRepository sourceSystemRepo;
    @Autowired SourceMappingRepository sourceMappingRepo;
    @Autowired MeasurementRepository measurementRepo;
    @Autowired BackfillTaskRepository backfillTaskRepo;
    @Autowired ConnectorRunRepository connectorRunRepo;
    @Autowired CollectorWatermarkRepository watermarkRepo;
    @Autowired AlarmRepository alarmRepo;
    @Autowired RuleRepository ruleRepo;
    @Autowired RuleVersionRepository ruleVersionRepo;
    @Autowired ParameterLimitRepository parameterLimitRepo;
    @Autowired BackfillRunner backfillRunner;

    @BeforeEach
    void setUp() {
        connectorRunRepo.deleteAll();
        backfillTaskRepo.deleteAll();
        alarmRepo.deleteAll();
        measurementRepo.deleteAll();
        parameterLimitRepo.deleteAll();
        ruleVersionRepo.deleteAll();
        ruleRepo.deleteAll();
        watermarkRepo.deleteAll();
        sourceMappingRepo.deleteAll();
        sourceSystemRepo.deleteAll();
        parameterRepo.deleteAll();

        jdbcTemplate.execute("DROP TABLE IF EXISTS fake_source");
        jdbcTemplate.execute("""
            CREATE TABLE fake_source (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                wafer_id VARCHAR(255),
                measured_value DOUBLE,
                ts TIMESTAMP,
                tool VARCHAR(255),
                recipe VARCHAR(255),
                product VARCHAR(255),
                lot_id VARCHAR(255)
            )
        """);
    }

    private SourceSystemEntity createSourceSystem() {
        return sourceSystemRepo.save(new SourceSystemEntity(
                "test-source", "localhost", 3306, "testdb", null, "zone-a", "UTC"));
    }

    private SourceMappingEntity createMapping(Long sourceSystemId, Long parameterId) {
        var mapping = new SourceMappingEntity(
                sourceSystemId, parameterId,
                "SELECT wafer_id, measured_value, ts, tool, recipe, product, lot_id FROM fake_source WHERE ts > :watermark_low AND ts <= :watermark_high ORDER BY ts",
                "measured_value", "ts",
                "{\"tool\":\"tool\",\"recipe\":\"recipe\",\"product\":\"product\",\"lot_id\":\"lot_id\",\"wafer_id\":\"wafer_id\"}",
                300, 10000, 30);
        mapping.setBackfillEnabled(true);
        mapping.setBackfillWindowDays(30);
        return sourceMappingRepo.save(mapping);
    }

    @Test
    void triggerBackfill_creates_task_and_pulls_historical_data() throws Exception {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        var source = createSourceSystem();
        var mapping = createMapping(source.getId(), param.getId());

        // Insert data from 10 days ago
        Instant tenDaysAgo = Instant.now().minus(10, ChronoUnit.DAYS);
        jdbcTemplate.update(
                "INSERT INTO fake_source (wafer_id, measured_value, ts, tool, recipe, product, lot_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "W-HIST-001", 95.0, java.sql.Timestamp.from(tenDaysAgo), "TOOL-A", "RCP-1", "PROD-X", "LOT-1");
        jdbcTemplate.update(
                "INSERT INTO fake_source (wafer_id, measured_value, ts, tool, recipe, product, lot_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "W-HIST-002", 97.0, java.sql.Timestamp.from(tenDaysAgo.plus(1, ChronoUnit.HOURS)), "TOOL-A", "RCP-1", "PROD-X", "LOT-1");

        var task = backfillRunner.triggerBackfill(mapping.getId());

        // Wait for async backfill to complete
        awaitTaskCompletion(task.getId(), 10);

        // Verify task completed
        var completedTask = backfillTaskRepo.findById(task.getId()).orElseThrow();
        assertThat(completedTask.getStatus()).isEqualTo(BackfillTaskEntity.Status.COMPLETED);
        assertThat(completedTask.getRowsProcessed()).isEqualTo(2);

        // Verify measurements were inserted
        var measurements = measurementRepo.findAll();
        assertThat(measurements).hasSize(2);
        assertThat(measurements.stream().map(MeasurementEntity::getWaferId).toList())
                .containsExactlyInAnyOrder("W-HIST-001", "W-HIST-002");
    }

    private void awaitTaskCompletion(Long taskId, int timeoutSeconds) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            var task = backfillTaskRepo.findById(taskId).orElseThrow();
            if (task.getStatus() == BackfillTaskEntity.Status.COMPLETED ||
                task.getStatus() == BackfillTaskEntity.Status.FAILED) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        throw new AssertionError("Backfill task did not complete within " + timeoutSeconds + " seconds");
    }
}
