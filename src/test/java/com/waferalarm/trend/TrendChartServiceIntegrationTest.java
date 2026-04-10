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
import java.util.List;

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
        if (limitRepo != null) limitRepo.deleteAll();
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
    void points_include_context_fields() throws Exception {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, 0.0));
        var t = Instant.parse("2024-01-01T00:00:00Z");
        measurementRepo.save(new MeasurementEntity(param.getId(), "W1", 10.0, t, "TOOL_A", "RCP1", "PROD1", "LOT1"));

        mvc.perform(get("/api/trend-chart")
                        .param("parameterId", param.getId().toString())
                        .param("from", "2024-01-01T00:00:00Z")
                        .param("to", "2024-01-02T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points[0].tool", is("TOOL_A")))
                .andExpect(jsonPath("$.points[0].recipe", is("RCP1")))
                .andExpect(jsonPath("$.points[0].product", is("PROD1")))
                .andExpect(jsonPath("$.points[0].lotId", is("LOT1")))
                .andExpect(jsonPath("$.points[0].waferId", is("W1")));
    }

    @Test
    void filter_by_tool() throws Exception {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, 0.0));
        var t = Instant.parse("2024-01-01T00:00:00Z");
        measurementRepo.save(new MeasurementEntity(param.getId(), "W1", 10.0, t, "TOOL_A", "R1", "P1", "L1"));
        measurementRepo.save(new MeasurementEntity(param.getId(), "W2", 20.0, t.plusSeconds(60), "TOOL_B", "R1", "P1", "L1"));

        mvc.perform(get("/api/trend-chart")
                        .param("parameterId", param.getId().toString())
                        .param("from", "2024-01-01T00:00:00Z")
                        .param("to", "2024-01-02T00:00:00Z")
                        .param("tool", "TOOL_A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points", hasSize(1)))
                .andExpect(jsonPath("$.points[0].value", is(10.0)));
    }

    @Test
    void filter_by_recipe_product_lot() throws Exception {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, 0.0));
        var t = Instant.parse("2024-01-01T00:00:00Z");
        measurementRepo.save(new MeasurementEntity(param.getId(), "W1", 10.0, t, "T1", "RCP_A", "PROD_A", "LOT_A"));
        measurementRepo.save(new MeasurementEntity(param.getId(), "W2", 20.0, t.plusSeconds(60), "T1", "RCP_B", "PROD_B", "LOT_B"));

        mvc.perform(get("/api/trend-chart")
                        .param("parameterId", param.getId().toString())
                        .param("from", "2024-01-01T00:00:00Z")
                        .param("to", "2024-01-02T00:00:00Z")
                        .param("recipe", "RCP_A")
                        .param("product", "PROD_A")
                        .param("lot", "LOT_A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points", hasSize(1)))
                .andExpect(jsonPath("$.points[0].value", is(10.0)));
    }

    @Autowired ParameterLimitRepository limitRepo;

    @Test
    void returns_control_limits_from_limit_resolver() throws Exception {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, 0.0));
        var t = Instant.parse("2024-01-01T00:00:00Z");
        measurementRepo.save(new MeasurementEntity(param.getId(), "W1", 50.0, t, "T1", "R1", "P1", "L1"));

        // Global fallback limit
        limitRepo.save(new ParameterLimitEntity(param.getId(), "{}", 95.0, 5.0));
        // Context-specific limit for tool=T1
        limitRepo.save(new ParameterLimitEntity(param.getId(), "{\"tool\":\"T1\"}", 90.0, 10.0));

        // Without filter → global fallback (empty context match)
        mvc.perform(get("/api/trend-chart")
                        .param("parameterId", param.getId().toString())
                        .param("from", "2024-01-01T00:00:00Z")
                        .param("to", "2024-01-02T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upperLimit", is(95.0)))
                .andExpect(jsonPath("$.lowerLimit", is(5.0)));

        // With tool filter → specific limit resolved
        mvc.perform(get("/api/trend-chart")
                        .param("parameterId", param.getId().toString())
                        .param("from", "2024-01-01T00:00:00Z")
                        .param("to", "2024-01-02T00:00:00Z")
                        .param("tool", "T1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upperLimit", is(90.0)))
                .andExpect(jsonPath("$.lowerLimit", is(10.0)));
    }

    @Test
    void no_limits_returns_nulls() throws Exception {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", null, null));
        var t = Instant.parse("2024-01-01T00:00:00Z");
        measurementRepo.save(new MeasurementEntity(param.getId(), "W1", 50.0, t, "T1", "R1", "P1", "L1"));

        mvc.perform(get("/api/trend-chart")
                        .param("parameterId", param.getId().toString())
                        .param("from", "2024-01-01T00:00:00Z")
                        .param("to", "2024-01-02T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upperLimit").doesNotExist())
                .andExpect(jsonPath("$.lowerLimit").doesNotExist());
    }

    @Test
    void returns_alarm_bands_intersecting_view() throws Exception {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, 0.0));
        var rule = ruleRepo.save(new RuleEntity(param.getId(), RuleType.UPPER_THRESHOLD, Severity.WARNING));
        var t = Instant.parse("2024-01-01T12:00:00Z");
        measurementRepo.save(new MeasurementEntity(param.getId(), "W1", 50.0, t, "T1", "R1", "P1", "L1"));

        // Alarm fully inside view window
        var alarm1 = AlarmEntity.fromSnapshot(new AlarmSnapshot(
                null, rule.getId(), param.getId(), "tool=T1", AlarmState.FIRING, Severity.WARNING,
                3, Instant.parse("2024-01-01T10:00:00Z"), Instant.parse("2024-01-01T14:00:00Z"),
                105.0, 100.0, 0, null, null));
        alarmRepo.save(alarm1);

        // Alarm outside view window (should NOT appear)
        var alarm2 = AlarmEntity.fromSnapshot(new AlarmSnapshot(
                null, rule.getId(), param.getId(), "tool=T2", AlarmState.RESOLVED, Severity.CRITICAL,
                1, Instant.parse("2023-12-01T00:00:00Z"), Instant.parse("2023-12-01T01:00:00Z"),
                105.0, 100.0, 0, null, null));
        alarmRepo.save(alarm2);

        mvc.perform(get("/api/trend-chart")
                        .param("parameterId", param.getId().toString())
                        .param("from", "2024-01-01T00:00:00Z")
                        .param("to", "2024-01-02T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alarmBands", hasSize(1)))
                .andExpect(jsonPath("$.alarmBands[0].severity", is("WARNING")));
    }

    @Test
    void multi_parameter_overlay() throws Exception {
        var param1 = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, 0.0));
        var param2 = parameterRepo.save(new ParameterEntity("Resistance", "ohm", 200.0, 50.0));
        var t = Instant.parse("2024-01-01T00:00:00Z");
        measurementRepo.save(new MeasurementEntity(param1.getId(), "W1", 10.0, t, "T1", "R1", "P1", "L1"));
        measurementRepo.save(new MeasurementEntity(param2.getId(), "W1", 100.0, t, "T1", "R1", "P1", "L1"));

        mvc.perform(get("/api/trend-chart/multi")
                        .param("parameterIds", param1.getId().toString(), param2.getId().toString())
                        .param("from", "2024-01-01T00:00:00Z")
                        .param("to", "2024-01-02T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(2)))
                .andExpect(jsonPath("$['" + param1.getId() + "'].points[0].value", is(10.0)))
                .andExpect(jsonPath("$['" + param2.getId() + "'].points[0].value", is(100.0)));
    }

    @Test
    void multi_parameter_rejects_more_than_5() throws Exception {
        mvc.perform(get("/api/trend-chart/multi")
                        .param("parameterIds", "1", "2", "3", "4", "5", "6")
                        .param("from", "2024-01-01T00:00:00Z")
                        .param("to", "2024-01-02T00:00:00Z"))
                .andExpect(status().isBadRequest());
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
