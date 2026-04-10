package com.waferalarm.collector;

import com.waferalarm.domain.*;
import com.waferalarm.evaluator.EvaluatorRunner;
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
    @Autowired EvaluatorRunner evaluatorRunner;
    @Autowired RuleStateRepository ruleStateRepo;
    @Autowired SourceMappingService sourceMappingService;

    @BeforeEach
    void setUp() {
        connectorRunRepo.deleteAll();
        backfillTaskRepo.deleteAll();
        alarmRepo.deleteAll();
        measurementRepo.deleteAll();
        parameterLimitRepo.deleteAll();
        ruleStateRepo.deleteAll();
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

    @Test
    void triggerBackfill_uses_custom_window_days() throws Exception {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        var source = createSourceSystem();
        var mapping = createMapping(source.getId(), param.getId());
        // Override to 7 days
        mapping.setBackfillWindowDays(7);
        sourceMappingRepo.save(mapping);

        // Insert data from 10 days ago (outside 7-day window)
        Instant tenDaysAgo = Instant.now().minus(10, ChronoUnit.DAYS);
        jdbcTemplate.update(
                "INSERT INTO fake_source (wafer_id, measured_value, ts, tool, recipe, product, lot_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "W-OLD", 90.0, java.sql.Timestamp.from(tenDaysAgo), "TOOL-A", "RCP-1", "PROD-X", "LOT-1");

        // Insert data from 3 days ago (inside 7-day window)
        Instant threeDaysAgo = Instant.now().minus(3, ChronoUnit.DAYS);
        jdbcTemplate.update(
                "INSERT INTO fake_source (wafer_id, measured_value, ts, tool, recipe, product, lot_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "W-RECENT", 95.0, java.sql.Timestamp.from(threeDaysAgo), "TOOL-A", "RCP-1", "PROD-X", "LOT-1");

        var task = backfillRunner.triggerBackfill(mapping.getId());
        awaitTaskCompletion(task.getId(), 10);

        var completedTask = backfillTaskRepo.findById(task.getId()).orElseThrow();
        assertThat(completedTask.getStatus()).isEqualTo(BackfillTaskEntity.Status.COMPLETED);
        // Only the 3-day-old row should be pulled (10-day-old is outside window)
        assertThat(completedTask.getRowsProcessed()).isEqualTo(1);
        assertThat(measurementRepo.findAll()).hasSize(1);
        assertThat(measurementRepo.findAll().getFirst().getWaferId()).isEqualTo("W-RECENT");
    }

    @Test
    void triggerBackfill_default_window_is_30_days() throws Exception {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        var source = createSourceSystem();
        // Create mapping without overriding backfillWindowDays
        var mapping = new SourceMappingEntity(
                source.getId(), param.getId(),
                "SELECT wafer_id, measured_value, ts, tool, recipe, product, lot_id FROM fake_source WHERE ts > :watermark_low AND ts <= :watermark_high ORDER BY ts",
                "measured_value", "ts",
                "{\"tool\":\"tool\",\"recipe\":\"recipe\",\"product\":\"product\",\"lot_id\":\"lot_id\",\"wafer_id\":\"wafer_id\"}",
                300, 10000, 30);
        mapping.setBackfillEnabled(true);
        mapping = sourceMappingRepo.save(mapping);

        assertThat(mapping.getBackfillWindowDays()).isEqualTo(30);

        // Insert data from 20 days ago (inside default 30-day window)
        Instant twentyDaysAgo = Instant.now().minus(20, ChronoUnit.DAYS);
        jdbcTemplate.update(
                "INSERT INTO fake_source (wafer_id, measured_value, ts, tool, recipe, product, lot_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "W-20D", 95.0, java.sql.Timestamp.from(twentyDaysAgo), "TOOL-A", "RCP-1", "PROD-X", "LOT-1");

        var task = backfillRunner.triggerBackfill(mapping.getId());
        awaitTaskCompletion(task.getId(), 10);

        assertThat(backfillTaskRepo.findById(task.getId()).orElseThrow().getRowsProcessed()).isEqualTo(1);
    }

    @Test
    void backfilled_measurements_do_not_generate_alarms() throws Exception {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        var source = createSourceSystem();
        var mapping = createMapping(source.getId(), param.getId());

        // Create a threshold rule that would fire for value > 100
        var rule = ruleRepo.save(new RuleEntity(param.getId(), RuleType.UPPER_THRESHOLD, Severity.WARNING));

        // Insert violating data from 5 days ago (value 150 exceeds limit 100)
        Instant fiveDaysAgo = Instant.now().minus(5, ChronoUnit.DAYS);
        jdbcTemplate.update(
                "INSERT INTO fake_source (wafer_id, measured_value, ts, tool, recipe, product, lot_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "W-VIOLATING", 150.0, java.sql.Timestamp.from(fiveDaysAgo), "TOOL-A", "RCP-1", "PROD-X", "LOT-1");

        // Run backfill
        var task = backfillRunner.triggerBackfill(mapping.getId());
        awaitTaskCompletion(task.getId(), 10);

        // Verify measurement was backfilled
        assertThat(measurementRepo.findAll()).hasSize(1);
        assertThat(measurementRepo.findAll().getFirst().isBackfilled()).isTrue();

        // Run evaluator - should NOT create alarm for backfilled data
        evaluatorRunner.tick();
        assertThat(alarmRepo.findAll()).isEmpty();
    }

    @Test
    void backfilled_measurements_appear_in_trend_chart_query() throws Exception {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        var source = createSourceSystem();
        var mapping = createMapping(source.getId(), param.getId());

        Instant fiveDaysAgo = Instant.now().minus(5, ChronoUnit.DAYS);
        jdbcTemplate.update(
                "INSERT INTO fake_source (wafer_id, measured_value, ts, tool, recipe, product, lot_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "W-BACKFILL", 95.0, java.sql.Timestamp.from(fiveDaysAgo), "TOOL-A", "RCP-1", "PROD-X", "LOT-1");

        var task = backfillRunner.triggerBackfill(mapping.getId());
        awaitTaskCompletion(task.getId(), 10);

        // The trend chart uses findByParameterIdAndTsBetween which does NOT filter backfilled
        var trendData = measurementRepo.findByParameterIdAndTsBetween(
                param.getId(),
                Instant.now().minus(30, ChronoUnit.DAYS),
                Instant.now());
        assertThat(trendData).hasSize(1);
        assertThat(trendData.getFirst().getWaferId()).isEqualTo("W-BACKFILL");
    }

    @Test
    void backfill_resumes_from_last_processed_ts_after_restart() throws Exception {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        var source = createSourceSystem();
        var mapping = createMapping(source.getId(), param.getId());

        // Simulate a partially completed backfill task (as if the process crashed)
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant fifteenDaysAgo = Instant.now().minus(15, ChronoUnit.DAYS);
        var task = new BackfillTaskEntity(mapping.getId(), thirtyDaysAgo, Instant.now());
        task.markRunning();
        task.recordProgress(5, fifteenDaysAgo); // Already processed up to 15 days ago
        task = backfillTaskRepo.save(task);

        // Insert data: one before the last_processed_ts (should be skipped), one after
        jdbcTemplate.update(
                "INSERT INTO fake_source (wafer_id, measured_value, ts, tool, recipe, product, lot_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "W-ALREADY-DONE", 90.0, java.sql.Timestamp.from(thirtyDaysAgo.plus(1, ChronoUnit.DAYS)),
                "TOOL-A", "RCP-1", "PROD-X", "LOT-1");
        jdbcTemplate.update(
                "INSERT INTO fake_source (wafer_id, measured_value, ts, tool, recipe, product, lot_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "W-REMAINING", 95.0, java.sql.Timestamp.from(fifteenDaysAgo.plus(1, ChronoUnit.DAYS)),
                "TOOL-A", "RCP-1", "PROD-X", "LOT-1");

        // Resume pending/running tasks (simulates app restart)
        backfillRunner.resumeIncomplete();
        awaitTaskCompletion(task.getId(), 10);

        var completedTask = backfillTaskRepo.findById(task.getId()).orElseThrow();
        assertThat(completedTask.getStatus()).isEqualTo(BackfillTaskEntity.Status.COMPLETED);
        // Should only pull the row AFTER lastProcessedTs
        assertThat(completedTask.getRowsProcessed()).isEqualTo(5 + 1); // previous 5 + 1 new
        assertThat(measurementRepo.findAll()).hasSize(1);
        assertThat(measurementRepo.findAll().getFirst().getWaferId()).isEqualTo("W-REMAINING");
    }

    @Test
    void creating_mapping_with_backfill_enabled_triggers_backfill() throws Exception {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        var source = createSourceSystem();

        // Insert historical data
        Instant tenDaysAgo = Instant.now().minus(10, ChronoUnit.DAYS);
        jdbcTemplate.update(
                "INSERT INTO fake_source (wafer_id, measured_value, ts, tool, recipe, product, lot_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "W-AUTO", 95.0, java.sql.Timestamp.from(tenDaysAgo), "TOOL-A", "RCP-1", "PROD-X", "LOT-1");

        // Create mapping via the service (simulating the API call)
        var req = new SourceMappingRequest(
                source.getId(), param.getId(),
                "SELECT wafer_id, measured_value, ts, tool, recipe, product, lot_id FROM fake_source WHERE ts > :watermark_low AND ts <= :watermark_high ORDER BY ts",
                "measured_value", "ts",
                "{\"tool\":\"tool\",\"recipe\":\"recipe\",\"product\":\"product\",\"lot_id\":\"lot_id\",\"wafer_id\":\"wafer_id\"}",
                300, 10000, 30,
                true, 30);  // backfillEnabled=true

        var mapping = sourceMappingService.create(req);

        // Wait for auto-triggered backfill to complete
        var taskOpt = backfillTaskRepo.findBySourceMappingId(mapping.getId());
        assertThat(taskOpt).isPresent();
        awaitTaskCompletion(taskOpt.get().getId(), 10);

        assertThat(measurementRepo.findAll()).hasSize(1);
        assertThat(measurementRepo.findAll().getFirst().getWaferId()).isEqualTo("W-AUTO");
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
