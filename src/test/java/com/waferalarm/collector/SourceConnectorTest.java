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
class SourceConnectorTest {

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ParameterRepository parameterRepo;
    @Autowired SourceSystemRepository sourceSystemRepo;
    @Autowired SourceMappingRepository sourceMappingRepo;
    @Autowired MeasurementRepository measurementRepo;
    @Autowired AlarmRepository alarmRepo;
    @Autowired RuleRepository ruleRepo;
    @Autowired RuleVersionRepository ruleVersionRepo;
    @Autowired ParameterLimitRepository parameterLimitRepo;
    @Autowired ConnectorRunRepository connectorRunRepo;
    @Autowired CollectorWatermarkRepository watermarkRepo;
    @Autowired BackfillTaskRepository backfillTaskRepo;
    @Autowired StagingUnmappedRepository unmappedRepo;
    @Autowired StagingDismissedRepository dismissedRepo;

    SourceConnector connector;

    @BeforeEach
    void setUp() {
        connectorRunRepo.deleteAll();
        alarmRepo.deleteAll();
        measurementRepo.deleteAll();
        parameterLimitRepo.deleteAll();
        ruleVersionRepo.deleteAll();
        ruleRepo.deleteAll();
        watermarkRepo.deleteAll();
        backfillTaskRepo.deleteAll();
        sourceMappingRepo.deleteAll();
        unmappedRepo.deleteAll();
        dismissedRepo.deleteAll();
        sourceSystemRepo.deleteAll();
        parameterRepo.deleteAll();

        connector = new SourceConnector(jdbcTemplate);

        // Create a fake source table to query against (using the test H2 DB itself)
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

    @Test
    void pull_returns_measurements_from_source_query_with_watermark_range() {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));

        // Insert rows into fake source
        jdbcTemplate.update(
            "INSERT INTO fake_source (wafer_id, measured_value, ts, tool, recipe, product, lot_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
            "W-001", 95.0, Instant.parse("2026-01-01T00:00:00Z"), "TOOL-A", "RCP-1", "PROD-X", "LOT-1");
        jdbcTemplate.update(
            "INSERT INTO fake_source (wafer_id, measured_value, ts, tool, recipe, product, lot_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
            "W-002", 105.0, Instant.parse("2026-01-01T01:00:00Z"), "TOOL-B", "RCP-2", "PROD-Y", "LOT-2");

        var mapping = new SourceMappingEntity(
            1L, param.getId(),
            "SELECT wafer_id, measured_value, ts, tool, recipe, product, lot_id FROM fake_source WHERE ts > :watermark_low AND ts <= :watermark_high ORDER BY ts",
            "measured_value", "ts",
            "{\"tool\":\"tool\",\"recipe\":\"recipe\",\"product\":\"product\",\"lot_id\":\"lot_id\",\"wafer_id\":\"wafer_id\"}",
            300, 10000, 30);

        Instant watermarkLow = Instant.parse("2025-12-31T00:00:00Z");
        Instant watermarkHigh = Instant.parse("2026-01-02T00:00:00Z");

        List<MeasurementEntity> results = connector.pull(mapping, param.getId(), watermarkLow, watermarkHigh);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getWaferId()).isEqualTo("W-001");
        assertThat(results.get(0).getValue()).isEqualTo(95.0);
        assertThat(results.get(0).getParameterId()).isEqualTo(param.getId());
        assertThat(results.get(0).getTool()).isEqualTo("TOOL-A");
        assertThat(results.get(1).getWaferId()).isEqualTo("W-002");
        assertThat(results.get(1).getValue()).isEqualTo(105.0);
    }

    @Test
    void pull_respects_watermark_range_filters_old_data() {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));

        jdbcTemplate.update(
            "INSERT INTO fake_source (wafer_id, measured_value, ts, tool, recipe, product, lot_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
            "W-001", 95.0, Instant.parse("2026-01-01T00:00:00Z"), "TOOL-A", "RCP-1", "PROD-X", "LOT-1");
        jdbcTemplate.update(
            "INSERT INTO fake_source (wafer_id, measured_value, ts, tool, recipe, product, lot_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
            "W-002", 105.0, Instant.parse("2026-01-01T02:00:00Z"), "TOOL-B", "RCP-2", "PROD-Y", "LOT-2");

        var mapping = new SourceMappingEntity(
            1L, param.getId(),
            "SELECT wafer_id, measured_value, ts, tool, recipe, product, lot_id FROM fake_source WHERE ts > :watermark_low AND ts <= :watermark_high ORDER BY ts",
            "measured_value", "ts",
            "{\"tool\":\"tool\",\"recipe\":\"recipe\",\"product\":\"product\",\"lot_id\":\"lot_id\",\"wafer_id\":\"wafer_id\"}",
            300, 10000, 30);

        // Watermark range only includes the first row
        Instant watermarkLow = Instant.parse("2025-12-31T00:00:00Z");
        Instant watermarkHigh = Instant.parse("2026-01-01T01:00:00Z");

        List<MeasurementEntity> results = connector.pull(mapping, param.getId(), watermarkLow, watermarkHigh);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getWaferId()).isEqualTo("W-001");
    }

    @Test
    void pull_enforces_row_cap() {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));

        for (int i = 1; i <= 5; i++) {
            jdbcTemplate.update(
                "INSERT INTO fake_source (wafer_id, measured_value, ts, tool, recipe, product, lot_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "W-" + String.format("%03d", i), 95.0 + i,
                Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i * 60),
                "TOOL-A", "RCP-1", "PROD-X", "LOT-1");
        }

        var mapping = new SourceMappingEntity(
            1L, param.getId(),
            "SELECT wafer_id, measured_value, ts, tool, recipe, product, lot_id FROM fake_source WHERE ts > :watermark_low AND ts <= :watermark_high ORDER BY ts",
            "measured_value", "ts",
            "{\"tool\":\"tool\",\"recipe\":\"recipe\",\"product\":\"product\",\"lot_id\":\"lot_id\",\"wafer_id\":\"wafer_id\"}",
            300, 3, 30);  // row_cap = 3

        Instant watermarkLow = Instant.parse("2025-12-31T00:00:00Z");
        Instant watermarkHigh = Instant.parse("2026-01-02T00:00:00Z");

        List<MeasurementEntity> results = connector.pull(mapping, param.getId(), watermarkLow, watermarkHigh);

        assertThat(results).hasSize(3);
    }

    @Test
    void pull_returns_empty_when_no_data_in_range() {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));

        var mapping = new SourceMappingEntity(
            1L, param.getId(),
            "SELECT wafer_id, measured_value, ts, tool, recipe, product, lot_id FROM fake_source WHERE ts > :watermark_low AND ts <= :watermark_high ORDER BY ts",
            "measured_value", "ts",
            "{\"tool\":\"tool\",\"recipe\":\"recipe\",\"product\":\"product\",\"lot_id\":\"lot_id\",\"wafer_id\":\"wafer_id\"}",
            300, 10000, 30);

        Instant watermarkLow = Instant.parse("2025-12-31T00:00:00Z");
        Instant watermarkHigh = Instant.parse("2026-01-02T00:00:00Z");

        List<MeasurementEntity> results = connector.pull(mapping, param.getId(), watermarkLow, watermarkHigh);

        assertThat(results).isEmpty();
    }
}
