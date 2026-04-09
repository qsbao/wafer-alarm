package com.waferalarm.trend;

import com.waferalarm.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class TrendChartServiceIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired MeasurementRepository measurementRepo;
    @Autowired ParameterRepository parameterRepo;
    @Autowired AlarmRepository alarmRepo;
    @Autowired RuleRepository ruleRepo;

    @BeforeEach
    void clean() {
        alarmRepo.deleteAll();
        measurementRepo.deleteAll();
        ruleRepo.deleteAll();
        parameterRepo.deleteAll();
    }

    @Test
    void returns_raw_points_below_threshold() throws Exception {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, 0.0));

        var t = Instant.parse("2024-01-01T00:00:00Z");
        measurementRepo.save(new MeasurementEntity(param.getId(), "W1", 10.0, t, "T1", "R1", "P1", "L1"));
        measurementRepo.save(new MeasurementEntity(param.getId(), "W2", 20.0, t.plusSeconds(60), "T1", "R1", "P1", "L1"));

        mvc.perform(get("/api/trend-chart")
                        .param("parameterId", param.getId().toString())
                        .param("from", "2024-01-01T00:00:00Z")
                        .param("to", "2024-01-02T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points", hasSize(2)))
                .andExpect(jsonPath("$.points[0].value", is(10.0)))
                .andExpect(jsonPath("$.points[1].value", is(20.0)))
                .andExpect(jsonPath("$.downsampled", is(false)));
    }

    @Test
    void downsamples_when_above_threshold() throws Exception {
        // Test config sets threshold to 5, so 8 points should trigger downsampling
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, 0.0));
        var t = Instant.parse("2024-01-01T00:00:00Z");

        for (int i = 0; i < 8; i++) {
            measurementRepo.save(new MeasurementEntity(
                    param.getId(), "W" + i, (double) i * 10,
                    t.plusSeconds(i * 60L), "T1", "R1", "P1", "L1"));
        }

        mvc.perform(get("/api/trend-chart")
                        .param("parameterId", param.getId().toString())
                        .param("from", "2024-01-01T00:00:00Z")
                        .param("to", "2024-01-02T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.downsampled", is(true)))
                .andExpect(jsonPath("$.points.length()", lessThan(8)));
    }

    @Test
    void empty_measurements_returns_empty_list() throws Exception {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, 0.0));

        mvc.perform(get("/api/trend-chart")
                        .param("parameterId", param.getId().toString())
                        .param("from", "2024-01-01T00:00:00Z")
                        .param("to", "2024-01-02T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points", hasSize(0)))
                .andExpect(jsonPath("$.downsampled", is(false)));
    }
}
