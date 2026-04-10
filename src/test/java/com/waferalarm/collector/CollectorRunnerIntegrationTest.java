package com.waferalarm.collector;

import com.waferalarm.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CollectorRunnerIntegrationTest {

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ParameterRepository parameterRepo;
    @Autowired SourceSystemRepository sourceSystemRepo;
    @Autowired SourceMappingRepository sourceMappingRepo;
    @Autowired MeasurementRepository measurementRepo;
    @Autowired ConnectorRunRepository connectorRunRepo;
    @Autowired CollectorWatermarkRepository watermarkRepo;
    @Autowired AlarmRepository alarmRepo;
    @Autowired RuleRepository ruleRepo;
    @Autowired RuleVersionRepository ruleVersionRepo;
    @Autowired ParameterLimitRepository parameterLimitRepo;
    @Autowired CollectorRunner collectorRunner;

    @BeforeEach
    void setUp() {
        connectorRunRepo.deleteAll();
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
        return sourceMappingRepo.save(new SourceMappingEntity(
                sourceSystemId, parameterId,
                "SELECT wafer_id, measured_value, ts, tool, recipe, product, lot_id FROM fake_source WHERE ts > :watermark_low AND ts <= :watermark_high ORDER BY ts",
                "measured_value", "ts",
                "{\"tool\":\"tool\",\"recipe\":\"recipe\",\"product\":\"product\",\"lot_id\":\"lot_id\",\"wafer_id\":\"wafer_id\"}",
                300, 10000, 30));
    }

    @Test
    void collectAll_pulls_measurements_and_writes_connector_run() {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        var source = createSourceSystem();
        var mapping = createMapping(source.getId(), param.getId());

        jdbcTemplate.update(
                "INSERT INTO fake_source (wafer_id, measured_value, ts, tool, recipe, product, lot_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "W-001", 95.0, Instant.parse("2026-01-01T00:00:00Z"), "TOOL-A", "RCP-1", "PROD-X", "LOT-1");

        collectorRunner.collectAll();

        assertThat(measurementRepo.findAll()).hasSize(1);
        assertThat(measurementRepo.findAll().getFirst().getWaferId()).isEqualTo("W-001");

        List<ConnectorRunEntity> runs = connectorRunRepo.findAll();
        assertThat(runs).hasSize(1);
        assertThat(runs.getFirst().getRowsPulled()).isEqualTo(1);
        assertThat(runs.getFirst().getError()).isNull();
    }

    @Test
    void collectAll_advances_watermark_between_ticks() {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        var source = createSourceSystem();
        var mapping = createMapping(source.getId(), param.getId());

        // First tick
        jdbcTemplate.update(
                "INSERT INTO fake_source (wafer_id, measured_value, ts, tool, recipe, product, lot_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "W-001", 95.0, Instant.parse("2026-01-01T00:00:00Z"), "TOOL-A", "RCP-1", "PROD-X", "LOT-1");
        collectorRunner.collectAll();
        assertThat(measurementRepo.findAll()).hasSize(1);

        // Second tick with new data
        jdbcTemplate.update(
                "INSERT INTO fake_source (wafer_id, measured_value, ts, tool, recipe, product, lot_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "W-002", 105.0, Instant.parse("2026-01-01T01:00:00Z"), "TOOL-B", "RCP-2", "PROD-Y", "LOT-2");
        collectorRunner.collectAll();

        // Should have both measurements (W-001 from tick 1, W-002 from tick 2)
        assertThat(measurementRepo.findAll()).hasSize(2);
        assertThat(connectorRunRepo.findAll()).hasSize(2);
    }

    @Test
    void collectAll_skips_disabled_mappings() {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        var source = createSourceSystem();
        var mapping = createMapping(source.getId(), param.getId());
        mapping.setEnabled(false);
        sourceMappingRepo.save(mapping);

        jdbcTemplate.update(
                "INSERT INTO fake_source (wafer_id, measured_value, ts, tool, recipe, product, lot_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "W-001", 95.0, Instant.parse("2026-01-01T00:00:00Z"), "TOOL-A", "RCP-1", "PROD-X", "LOT-1");

        collectorRunner.collectAll();

        assertThat(measurementRepo.findAll()).isEmpty();
        assertThat(connectorRunRepo.findAll()).isEmpty();
    }

    @Test
    void collectAll_resumes_from_persisted_watermark_no_duplicates() {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        var source = createSourceSystem();
        var mapping = createMapping(source.getId(), param.getId());

        // Insert two rows
        jdbcTemplate.update(
                "INSERT INTO fake_source (wafer_id, measured_value, ts, tool, recipe, product, lot_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "W-001", 95.0, Instant.parse("2026-01-01T00:00:00Z"), "TOOL-A", "RCP-1", "PROD-X", "LOT-1");
        jdbcTemplate.update(
                "INSERT INTO fake_source (wafer_id, measured_value, ts, tool, recipe, product, lot_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "W-002", 105.0, Instant.parse("2026-01-01T01:00:00Z"), "TOOL-B", "RCP-2", "PROD-Y", "LOT-2");

        // First collect gets both
        collectorRunner.collectAll();
        assertThat(measurementRepo.findAll()).hasSize(2);

        // Second collect should get zero new rows (watermark advanced past both)
        collectorRunner.collectAll();
        assertThat(measurementRepo.findAll()).hasSize(2); // no duplicates
        assertThat(connectorRunRepo.findAll()).hasSize(2);
        assertThat(connectorRunRepo.findAll().get(1).getRowsPulled()).isEqualTo(0);
    }

    @Test
    void collectAll_records_error_in_connector_run_on_failure() {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        var source = createSourceSystem();
        // Bad query template that will fail
        var mapping = sourceMappingRepo.save(new SourceMappingEntity(
                source.getId(), param.getId(),
                "SELECT * FROM nonexistent_table WHERE ts > :watermark_low AND ts <= :watermark_high",
                "measured_value", "ts", null, 300, 10000, 30));

        collectorRunner.collectAll();

        List<ConnectorRunEntity> runs = connectorRunRepo.findAll();
        assertThat(runs).hasSize(1);
        assertThat(runs.getFirst().getError()).isNotNull();
        assertThat(runs.getFirst().getRowsPulled()).isEqualTo(0);
    }
}
