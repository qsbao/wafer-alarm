package com.waferalarm.health;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthEndpointTest {

    @Autowired MockMvc mockMvc;

    @Test
    void healthReportEndpointReturnsConnectorsAndEvaluator() throws Exception {
        mockMvc.perform(get("/api/health/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectors").isArray())
                .andExpect(jsonPath("$.evaluator").exists())
                .andExpect(jsonPath("$.evaluator.watermarkLagSeconds").isNumber());
    }

    @Test
    void prometheusMetricsEndpointExposesCustomGauges() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("wafer_health_stalled_connectors")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("wafer_health_eval_lag_seconds")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("wafer_health_connector_errors")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("wafer_health_eval_errors")));
    }

    @Test
    void metricsEndpointIsAccessible() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk());
    }
}
