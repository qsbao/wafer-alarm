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
class SourceSystemControllerTest {

    @Autowired MockMvc mvc;
    @Autowired SourceSystemRepository sourceSystemRepo;
    @Autowired SourceMappingRepository sourceMappingRepo;
    @Autowired ConnectorRunRepository connectorRunRepo;
    @Autowired AlarmRepository alarmRepo;
    @Autowired MeasurementRepository measurementRepo;
    @Autowired RuleRepository ruleRepo;
    @Autowired RuleVersionRepository ruleVersionRepo;
    @Autowired ParameterLimitRepository parameterLimitRepo;
    @Autowired CollectorWatermarkRepository watermarkRepo;
    @Autowired ParameterRepository parameterRepo;
    @Autowired BackfillTaskRepository backfillTaskRepo;

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
    }

    @Test
    void listSourceSystems_returnsAll() throws Exception {
        sourceSystemRepo.save(new SourceSystemEntity("MES", "mes-host", 3306, "mes_db", "creds-ref", "zone-a", "UTC"));

        mvc.perform(get("/api/source-systems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("MES")))
                .andExpect(jsonPath("$[0].host", is("mes-host")))
                .andExpect(jsonPath("$[0].port", is(3306)));
    }

    @Test
    void createSourceSystem_returnsCreated() throws Exception {
        mvc.perform(post("/api/source-systems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"MES","host":"mes-host","port":3306,"dbName":"mes_db",
                                 "credentialsRef":"vault/mes","networkZone":"zone-a","timezone":"Asia/Taipei"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("MES")))
                .andExpect(jsonPath("$.host", is("mes-host")))
                .andExpect(jsonPath("$.port", is(3306)))
                .andExpect(jsonPath("$.networkZone", is("zone-a")))
                .andExpect(jsonPath("$.timezone", is("Asia/Taipei")))
                .andExpect(jsonPath("$.enabled", is(true)));
    }

    @Test
    void createSourceSystem_rejectsMissingHost() throws Exception {
        mvc.perform(post("/api/source-systems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"MES","port":3306}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateSourceSystem_appliesChanges() throws Exception {
        var ss = sourceSystemRepo.save(new SourceSystemEntity("MES", "old-host", 3306, "db", null, "zone-a", "UTC"));

        mvc.perform(put("/api/source-systems/" + ss.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"MES-v2","host":"new-host","port":3307,"dbName":"new_db",
                                 "networkZone":"zone-b","timezone":"US/Eastern"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("MES-v2")))
                .andExpect(jsonPath("$.host", is("new-host")))
                .andExpect(jsonPath("$.port", is(3307)));
    }

    @Test
    void disableSourceSystem() throws Exception {
        var ss = sourceSystemRepo.save(new SourceSystemEntity("MES", "host", 3306, "db", null, "zone-a", "UTC"));

        mvc.perform(post("/api/source-systems/" + ss.getId() + "/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled", is(false)));
    }

    @Test
    void enableSourceSystem() throws Exception {
        var ss = sourceSystemRepo.save(new SourceSystemEntity("MES", "host", 3306, "db", null, "zone-a", "UTC"));
        ss.setEnabled(false);
        sourceSystemRepo.save(ss);

        mvc.perform(post("/api/source-systems/" + ss.getId() + "/enable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled", is(true)));
    }

    @Test
    void updateSourceSystem_notFound_returns404() throws Exception {
        mvc.perform(put("/api/source-systems/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"x","host":"h","port":1}
                                """))
                .andExpect(status().isNotFound());
    }
}
