package com.waferalarm.collector;

import com.waferalarm.catalog.UnmappedDataService;
import com.waferalarm.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class UnmappedColumnDetectionTest {

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ParameterRepository parameterRepo;
    @Autowired SourceSystemRepository sourceSystemRepo;
    @Autowired SourceMappingRepository sourceMappingRepo;
    @Autowired MeasurementRepository measurementRepo;
    @Autowired ConnectorRunRepository connectorRunRepo;
    @Autowired CollectorWatermarkRepository watermarkRepo;
    @Autowired StagingUnmappedRepository unmappedRepo;
    @Autowired StagingDismissedRepository dismissedRepo;
    @Autowired AlarmRepository alarmRepo;
    @Autowired RuleRepository ruleRepo;
    @Autowired RuleVersionRepository ruleVersionRepo;
    @Autowired ParameterLimitRepository parameterLimitRepo;
    @Autowired BackfillTaskRepository backfillTaskRepo;
    @Autowired CollectorRunner collectorRunner;

    @BeforeEach
    void setUp() {
        unmappedRepo.deleteAll();
        dismissedRepo.deleteAll();
        connectorRunRepo.deleteAll();
        alarmRepo.deleteAll();
        measurementRepo.deleteAll();
        parameterLimitRepo.deleteAll();
        ruleVersionRepo.deleteAll();
        ruleRepo.deleteAll();
        watermarkRepo.deleteAll();
        backfillTaskRepo.deleteAll();
        sourceMappingRepo.deleteAll();
        sourceSystemRepo.deleteAll();
        parameterRepo.deleteAll();

        jdbcTemplate.execute("DROP TABLE IF EXISTS fake_source_extra");
        jdbcTemplate.execute("""
            CREATE TABLE fake_source_extra (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                wafer_id VARCHAR(255),
                measured_value DOUBLE,
                ts TIMESTAMP,
                tool VARCHAR(255),
                recipe VARCHAR(255),
                product VARCHAR(255),
                lot_id VARCHAR(255),
                extra_metric DOUBLE,
                new_sensor VARCHAR(255)
            )
        """);
    }

    @Test
    void collector_detects_unmapped_columns_in_result_set() {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        var source = sourceSystemRepo.save(new SourceSystemEntity(
                "test-source", "localhost", 3306, "testdb", null, "zone-a", "UTC"));

        // Mapping that selects ALL columns including unmapped ones
        var mapping = sourceMappingRepo.save(new SourceMappingEntity(
                source.getId(), param.getId(),
                "SELECT wafer_id, measured_value, ts, tool, recipe, product, lot_id, extra_metric, new_sensor FROM fake_source_extra WHERE ts > :watermark_low AND ts <= :watermark_high ORDER BY ts",
                "measured_value", "ts",
                "{\"tool\":\"tool\",\"recipe\":\"recipe\",\"product\":\"product\",\"lot_id\":\"lot_id\",\"wafer_id\":\"wafer_id\"}",
                300, 10000, 30));

        jdbcTemplate.update(
                "INSERT INTO fake_source_extra (wafer_id, measured_value, ts, tool, recipe, product, lot_id, extra_metric, new_sensor) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "W-001", 95.0, Instant.parse("2026-01-01T00:00:00Z"), "TOOL-A", "RCP-1", "PROD-X", "LOT-1", 42.5, "sensor-val");

        collectorRunner.collectAll();

        // Measurements should still be collected normally
        assertThat(measurementRepo.findAll()).hasSize(1);

        // Unmapped columns should be recorded
        var unmapped = unmappedRepo.findAll();
        assertThat(unmapped).hasSizeGreaterThanOrEqualTo(2);
        assertThat(unmapped.stream().map(StagingUnmappedEntity::getColumnKey))
                .contains("extra_metric", "new_sensor");
    }

    @Test
    void collector_does_not_record_mapped_columns_as_unmapped() {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        var source = sourceSystemRepo.save(new SourceSystemEntity(
                "test-source", "localhost", 3306, "testdb", null, "zone-a", "UTC"));

        // Mapping that only selects mapped columns (no extras)
        var mapping = sourceMappingRepo.save(new SourceMappingEntity(
                source.getId(), param.getId(),
                "SELECT wafer_id, measured_value, ts, tool, recipe, product, lot_id FROM fake_source_extra WHERE ts > :watermark_low AND ts <= :watermark_high ORDER BY ts",
                "measured_value", "ts",
                "{\"tool\":\"tool\",\"recipe\":\"recipe\",\"product\":\"product\",\"lot_id\":\"lot_id\",\"wafer_id\":\"wafer_id\"}",
                300, 10000, 30));

        jdbcTemplate.update(
                "INSERT INTO fake_source_extra (wafer_id, measured_value, ts, tool, recipe, product, lot_id, extra_metric, new_sensor) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "W-001", 95.0, Instant.parse("2026-01-01T00:00:00Z"), "TOOL-A", "RCP-1", "PROD-X", "LOT-1", 42.5, "sensor-val");

        collectorRunner.collectAll();

        // No unmapped columns when query only selects mapped columns
        assertThat(unmappedRepo.findAll()).isEmpty();
    }

    @Test
    void collector_increments_count_on_repeated_unmapped_columns() {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        var source = sourceSystemRepo.save(new SourceSystemEntity(
                "test-source", "localhost", 3306, "testdb", null, "zone-a", "UTC"));

        var mapping = sourceMappingRepo.save(new SourceMappingEntity(
                source.getId(), param.getId(),
                "SELECT wafer_id, measured_value, ts, tool, recipe, product, lot_id, extra_metric FROM fake_source_extra WHERE ts > :watermark_low AND ts <= :watermark_high ORDER BY ts",
                "measured_value", "ts",
                "{\"tool\":\"tool\",\"recipe\":\"recipe\",\"product\":\"product\",\"lot_id\":\"lot_id\",\"wafer_id\":\"wafer_id\"}",
                300, 10000, 30));

        // First tick
        jdbcTemplate.update(
                "INSERT INTO fake_source_extra (wafer_id, measured_value, ts, tool, recipe, product, lot_id, extra_metric) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "W-001", 95.0, Instant.parse("2026-01-01T00:00:00Z"), "TOOL-A", "RCP-1", "PROD-X", "LOT-1", 42.5);
        collectorRunner.collectAll();

        // Second tick with new data
        jdbcTemplate.update(
                "INSERT INTO fake_source_extra (wafer_id, measured_value, ts, tool, recipe, product, lot_id, extra_metric) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                "W-002", 96.0, Instant.parse("2026-01-01T01:00:00Z"), "TOOL-A", "RCP-1", "PROD-X", "LOT-1", 55.0);
        collectorRunner.collectAll();

        var unmapped = unmappedRepo.findAll();
        assertThat(unmapped).hasSize(1);
        assertThat(unmapped.getFirst().getColumnKey()).isEqualTo("extra_metric");
        assertThat(unmapped.getFirst().getOccurrenceCount()).isEqualTo(2);
    }
}
