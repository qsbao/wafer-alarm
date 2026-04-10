package com.waferalarm.collector;

import com.waferalarm.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class MultiZoneCollectorTest {

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
    @Autowired BackfillTaskRepository backfillTaskRepo;
    @Autowired SourceConnector connector;

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
        sourceSystemRepo.deleteAll();
        parameterRepo.deleteAll();

        jdbcTemplate.execute("DROP TABLE IF EXISTS fake_source_a");
        jdbcTemplate.execute("DROP TABLE IF EXISTS fake_source_b");
        jdbcTemplate.execute("""
            CREATE TABLE fake_source_a (
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
        jdbcTemplate.execute("""
            CREATE TABLE fake_source_b (
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

    private SourceSystemEntity createSourceSystem(String name, String zone) {
        return sourceSystemRepo.save(new SourceSystemEntity(
                name, "localhost", 3306, "testdb", null, zone, "UTC"));
    }

    private SourceMappingEntity createMapping(Long sourceSystemId, Long parameterId, String table) {
        return sourceMappingRepo.save(new SourceMappingEntity(
                sourceSystemId, parameterId,
                "SELECT wafer_id, measured_value, ts, tool, recipe, product, lot_id FROM " + table
                        + " WHERE ts > :watermark_low AND ts <= :watermark_high ORDER BY ts",
                "measured_value", "ts",
                "{\"tool\":\"tool\",\"recipe\":\"recipe\",\"product\":\"product\",\"lot_id\":\"lot_id\",\"wafer_id\":\"wafer_id\"}",
                300, 10000, 30));
    }

    private CollectorRunner buildRunner(Set<Long> ownedIds) {
        var config = new CollectorConfig(ownedIds);
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        return new CollectorRunner(connector, sourceMappingRepo, measurementRepo,
                watermarkRepo, connectorRunRepo, executor, config);
    }

    @Test
    void collector_only_pulls_from_owned_source_systems() {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        var sourceA = createSourceSystem("source-a", "zone-a");
        var sourceB = createSourceSystem("source-b", "zone-b");
        var mappingA = createMapping(sourceA.getId(), param.getId(), "fake_source_a");
        var mappingB = createMapping(sourceB.getId(), param.getId(), "fake_source_b");

        jdbcTemplate.update(
                "INSERT INTO fake_source_a (wafer_id, measured_value, ts, tool, recipe, product, lot_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "W-A1", 95.0, Instant.parse("2026-01-01T00:00:00Z"), "TOOL-A", "RCP-1", "PROD-X", "LOT-1");
        jdbcTemplate.update(
                "INSERT INTO fake_source_b (wafer_id, measured_value, ts, tool, recipe, product, lot_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "W-B1", 105.0, Instant.parse("2026-01-01T00:00:00Z"), "TOOL-B", "RCP-2", "PROD-Y", "LOT-2");

        // Collector A only owns source A
        var runnerA = buildRunner(Set.of(sourceA.getId()));
        runnerA.collectAll();

        // Should only have wafer from source A
        var measurements = measurementRepo.findAll();
        assertThat(measurements).hasSize(1);
        assertThat(measurements.getFirst().getWaferId()).isEqualTo("W-A1");

        // Connector runs should only be for mapping A
        var runs = connectorRunRepo.findAll();
        assertThat(runs).hasSize(1);
        assertThat(runs.getFirst().getSourceMappingId()).isEqualTo(mappingA.getId());
    }

    @Test
    void empty_owned_set_collects_from_all_sources() {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        var sourceA = createSourceSystem("source-a", "zone-a");
        var sourceB = createSourceSystem("source-b", "zone-b");
        createMapping(sourceA.getId(), param.getId(), "fake_source_a");
        createMapping(sourceB.getId(), param.getId(), "fake_source_b");

        jdbcTemplate.update(
                "INSERT INTO fake_source_a (wafer_id, measured_value, ts, tool, recipe, product, lot_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "W-A1", 95.0, Instant.parse("2026-01-01T00:00:00Z"), "TOOL-A", "RCP-1", "PROD-X", "LOT-1");
        jdbcTemplate.update(
                "INSERT INTO fake_source_b (wafer_id, measured_value, ts, tool, recipe, product, lot_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "W-B1", 105.0, Instant.parse("2026-01-01T00:00:00Z"), "TOOL-B", "RCP-2", "PROD-Y", "LOT-2");

        // Empty owned set = collect all
        var runner = buildRunner(Set.of());
        runner.collectAll();

        assertThat(measurementRepo.findAll()).hasSize(2);
        assertThat(connectorRunRepo.findAll()).hasSize(2);
    }

    @Test
    void two_collectors_with_disjoint_sets_produce_no_duplicates() {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        var sourceA = createSourceSystem("source-a", "zone-a");
        var sourceB = createSourceSystem("source-b", "zone-b");
        createMapping(sourceA.getId(), param.getId(), "fake_source_a");
        createMapping(sourceB.getId(), param.getId(), "fake_source_b");

        jdbcTemplate.update(
                "INSERT INTO fake_source_a (wafer_id, measured_value, ts, tool, recipe, product, lot_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "W-A1", 95.0, Instant.parse("2026-01-01T00:00:00Z"), "TOOL-A", "RCP-1", "PROD-X", "LOT-1");
        jdbcTemplate.update(
                "INSERT INTO fake_source_b (wafer_id, measured_value, ts, tool, recipe, product, lot_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "W-B1", 105.0, Instant.parse("2026-01-01T00:00:00Z"), "TOOL-B", "RCP-2", "PROD-Y", "LOT-2");

        // Collector A owns source A, Collector B owns source B
        var runnerA = buildRunner(Set.of(sourceA.getId()));
        var runnerB = buildRunner(Set.of(sourceB.getId()));

        runnerA.collectAll();
        runnerB.collectAll();

        // Both wafers present, no duplicates
        var measurements = measurementRepo.findAll();
        assertThat(measurements).hasSize(2);
        assertThat(measurements).extracting("waferId").containsExactlyInAnyOrder("W-A1", "W-B1");

        // Running again produces no additional rows
        runnerA.collectAll();
        runnerB.collectAll();
        assertThat(measurementRepo.findAll()).hasSize(2);
    }
}
