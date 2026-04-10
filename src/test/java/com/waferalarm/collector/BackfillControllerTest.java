package com.waferalarm.collector;

import com.waferalarm.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class BackfillControllerTest {

    @Autowired MockMvc mockMvc;
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
    @Autowired RuleStateRepository ruleStateRepo;

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
    }

    private SourceMappingEntity createMapping() {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        var source = sourceSystemRepo.save(new SourceSystemEntity(
                "test-source", "localhost", 3306, "testdb", null, "zone-a", "UTC"));
        var mapping = new SourceMappingEntity(
                source.getId(), param.getId(),
                "SELECT wafer_id, measured_value, ts FROM fake_source WHERE ts > :watermark_low AND ts <= :watermark_high ORDER BY ts",
                "measured_value", "ts", null, 300, 10000, 30);
        mapping.setBackfillEnabled(true);
        return sourceMappingRepo.save(mapping);
    }

    @Test
    void post_backfill_triggers_and_returns_task() throws Exception {
        var mapping = createMapping();

        mockMvc.perform(post("/api/source-mappings/" + mapping.getId() + "/backfill"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.sourceMappingId").value(mapping.getId()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void get_backfill_tasks_returns_all() throws Exception {
        var mapping = createMapping();
        backfillTaskRepo.save(new BackfillTaskEntity(
                mapping.getId(), Instant.now().minus(30, ChronoUnit.DAYS), Instant.now()));

        mockMvc.perform(get("/api/backfill-tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sourceMappingId").value(mapping.getId()));
    }

    @Test
    void get_backfill_task_by_mapping_id() throws Exception {
        var mapping = createMapping();
        backfillTaskRepo.save(new BackfillTaskEntity(
                mapping.getId(), Instant.now().minus(30, ChronoUnit.DAYS), Instant.now()));

        mockMvc.perform(get("/api/backfill-tasks/mapping/" + mapping.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceMappingId").value(mapping.getId()));
    }

    @Test
    void get_backfill_task_by_mapping_id_returns_404_when_not_found() throws Exception {
        mockMvc.perform(get("/api/backfill-tasks/mapping/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_backfill_returns_404_for_nonexistent_mapping() throws Exception {
        mockMvc.perform(post("/api/source-mappings/999/backfill"))
                .andExpect(status().isNotFound());
    }
}
