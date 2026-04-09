package com.waferalarm.collector;

import com.waferalarm.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SourceMappingControllerTest {

    @Autowired MockMvc mvc;
    @Autowired SourceSystemRepository sourceSystemRepo;
    @Autowired SourceMappingRepository sourceMappingRepo;
    @Autowired ConnectorRunRepository connectorRunRepo;
    @Autowired ParameterRepository parameterRepo;
    @Autowired AlarmRepository alarmRepo;
    @Autowired MeasurementRepository measurementRepo;
    @Autowired RuleRepository ruleRepo;
    @Autowired RuleVersionRepository ruleVersionRepo;
    @Autowired ParameterLimitRepository parameterLimitRepo;
    @Autowired CollectorWatermarkRepository watermarkRepo;

    Long sourceSystemId;
    Long parameterId;

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

        var ss = sourceSystemRepo.save(new SourceSystemEntity("MES", "host", 3306, "db", null, "zone-a", "UTC"));
        sourceSystemId = ss.getId();
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        parameterId = param.getId();
    }

    @Test
    void listMappings_returnsAll() throws Exception {
        sourceMappingRepo.save(new SourceMappingEntity(
                sourceSystemId, parameterId, "SELECT * FROM t WHERE ts > :watermark_low AND ts <= :watermark_high",
                "value", "ts", null, 300, 10000, 30));

        mvc.perform(get("/api/source-mappings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].queryTemplate", containsString("SELECT")));
    }

    @Test
    void createMapping_returnsCreated_withDefaults() throws Exception {
        mvc.perform(post("/api/source-mappings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceSystemId":%d,"parameterId":%d,
                                 "queryTemplate":"SELECT * FROM t WHERE ts > :watermark_low AND ts <= :watermark_high",
                                 "valueColumn":"measured_value","watermarkColumn":"ts"}
                                """.formatted(sourceSystemId, parameterId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceSystemId", is(sourceSystemId.intValue())))
                .andExpect(jsonPath("$.parameterId", is(parameterId.intValue())))
                .andExpect(jsonPath("$.watermarkColumn", is("ts")))
                .andExpect(jsonPath("$.pollIntervalSeconds", is(300)))
                .andExpect(jsonPath("$.rowCap", is(10000)))
                .andExpect(jsonPath("$.queryTimeoutSeconds", is(30)))
                .andExpect(jsonPath("$.enabled", is(true)));
    }

    @Test
    void createMapping_rejectsMissingWatermarkColumn() throws Exception {
        mvc.perform(post("/api/source-mappings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceSystemId":%d,"parameterId":%d,
                                 "queryTemplate":"SELECT * FROM t",
                                 "valueColumn":"measured_value"}
                                """.formatted(sourceSystemId, parameterId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createMapping_rejectsMissingQueryTemplate() throws Exception {
        mvc.perform(post("/api/source-mappings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceSystemId":%d,"parameterId":%d,
                                 "valueColumn":"measured_value","watermarkColumn":"ts"}
                                """.formatted(sourceSystemId, parameterId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createMapping_customRowCapAndTimeout() throws Exception {
        mvc.perform(post("/api/source-mappings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceSystemId":%d,"parameterId":%d,
                                 "queryTemplate":"SELECT * FROM t WHERE ts > :watermark_low AND ts <= :watermark_high",
                                 "valueColumn":"measured_value","watermarkColumn":"ts",
                                 "pollIntervalSeconds":60,"rowCap":500,"queryTimeoutSeconds":10}
                                """.formatted(sourceSystemId, parameterId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pollIntervalSeconds", is(60)))
                .andExpect(jsonPath("$.rowCap", is(500)))
                .andExpect(jsonPath("$.queryTimeoutSeconds", is(10)));
    }

    @Test
    void updateMapping_appliesChanges() throws Exception {
        var m = sourceMappingRepo.save(new SourceMappingEntity(
                sourceSystemId, parameterId, "SELECT old FROM t WHERE ts > :watermark_low AND ts <= :watermark_high",
                "value", "ts", null, 300, 10000, 30));

        mvc.perform(put("/api/source-mappings/" + m.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceSystemId":%d,"parameterId":%d,
                                 "queryTemplate":"SELECT new FROM t WHERE ts > :watermark_low AND ts <= :watermark_high",
                                 "valueColumn":"new_value","watermarkColumn":"updated_at",
                                 "pollIntervalSeconds":120,"rowCap":5000,"queryTimeoutSeconds":15}
                                """.formatted(sourceSystemId, parameterId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queryTemplate", containsString("new")))
                .andExpect(jsonPath("$.valueColumn", is("new_value")))
                .andExpect(jsonPath("$.watermarkColumn", is("updated_at")));
    }

    @Test
    void disableMapping_setsEnabledFalse() throws Exception {
        var m = sourceMappingRepo.save(new SourceMappingEntity(
                sourceSystemId, parameterId, "SELECT * FROM t WHERE ts > :watermark_low AND ts <= :watermark_high",
                "value", "ts", null, 300, 10000, 30));

        mvc.perform(post("/api/source-mappings/" + m.getId() + "/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled", is(false)));
    }

    @Test
    void enableMapping_setsEnabledTrue() throws Exception {
        var m = sourceMappingRepo.save(new SourceMappingEntity(
                sourceSystemId, parameterId, "SELECT * FROM t WHERE ts > :watermark_low AND ts <= :watermark_high",
                "value", "ts", null, 300, 10000, 30));
        m.setEnabled(false);
        sourceMappingRepo.save(m);

        mvc.perform(post("/api/source-mappings/" + m.getId() + "/enable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled", is(true)));
    }

    @Test
    void updateMapping_notFound_returns404() throws Exception {
        mvc.perform(put("/api/source-mappings/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceSystemId":1,"parameterId":1,
                                 "queryTemplate":"x","valueColumn":"v","watermarkColumn":"ts"}
                                """))
                .andExpect(status().isNotFound());
    }
}
