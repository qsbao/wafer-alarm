package com.waferalarm.web;

import com.waferalarm.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BacktestControllerTest {

    @Autowired private MockMvc mockMvc;
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
    void backtest_endpoint_returns_violations() throws Exception {
        limitRepo.save(new ParameterLimitEntity(parameter.getId(), "{}", 100.0, null));

        Instant now = Instant.now();
        measurementRepo.save(new MeasurementEntity(parameter.getId(), "W001", 101.0,
                now.minus(3, ChronoUnit.DAYS), "TOOL-A", "RCP-1", "PROD-X", "LOT-1"));
        measurementRepo.save(new MeasurementEntity(parameter.getId(), "W002", 99.0,
                now.minus(2, ChronoUnit.DAYS), "TOOL-A", "RCP-1", "PROD-X", "LOT-1"));

        String body = """
                {
                  "parameterId": %d,
                  "ruleType": "UPPER_THRESHOLD",
                  "severity": "WARNING",
                  "from": "%s",
                  "to": "%s"
                }
                """.formatted(parameter.getId(),
                now.minus(7, ChronoUnit.DAYS).toString(),
                now.toString());

        mockMvc.perform(post("/api/rules/backtest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalViolations", is(1)))
                .andExpect(jsonPath("$.severityBreakdown.WARNING", is(1)))
                .andExpect(jsonPath("$.violations", hasSize(1)))
                .andExpect(jsonPath("$.violations[0].waferId", is("W001")));
    }
}
