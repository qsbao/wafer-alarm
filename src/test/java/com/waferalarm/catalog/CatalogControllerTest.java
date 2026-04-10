package com.waferalarm.catalog;

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
class CatalogControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ParameterRepository parameterRepo;
    @Autowired AlarmRepository alarmRepo;
    @Autowired MeasurementRepository measurementRepo;
    @Autowired RuleRepository ruleRepo;
    @Autowired RuleVersionRepository ruleVersionRepo;
    @Autowired ParameterLimitRepository parameterLimitRepo;
    @Autowired ConnectorRunRepository connectorRunRepo;
    @Autowired SourceMappingRepository sourceMappingRepo;
    @Autowired RuleStateRepository ruleStateRepo;

    @BeforeEach
    void setUp() {
        alarmRepo.deleteAll();
        ruleStateRepo.deleteAll();
        connectorRunRepo.deleteAll();
        measurementRepo.deleteAll();
        parameterLimitRepo.deleteAll();
        ruleVersionRepo.deleteAll();
        ruleRepo.deleteAll();
        sourceMappingRepo.deleteAll();
        parameterRepo.deleteAll();
    }

    @Test
    void listParameters_returnsAll() throws Exception {
        parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        parameterRepo.save(new ParameterEntity("Overlay", "nm", null, null));

        mvc.perform(get("/api/parameters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", is("CD")));
    }

    @Test
    void createParameter_returnsCreated() throws Exception {
        mvc.perform(post("/api/parameters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"CD","unit":"nm","description":"Critical Dimension",
                                 "area":"Litho","defaultLowerLimit":10.0,"defaultUpperLimit":100.0}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("CD")))
                .andExpect(jsonPath("$.area", is("Litho")))
                .andExpect(jsonPath("$.defaultLowerLimit", is(10.0)))
                .andExpect(jsonPath("$.defaultUpperLimit", is(100.0)))
                .andExpect(jsonPath("$.enabled", is(true)));
    }

    @Test
    void createParameter_rejectsInvalidLimits() throws Exception {
        mvc.perform(post("/api/parameters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"CD","unit":"nm","defaultLowerLimit":200.0,"defaultUpperLimit":100.0}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateParameter_appliesChanges() throws Exception {
        var p = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));

        mvc.perform(put("/api/parameters/" + p.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"CD-v2","unit":"um","description":"updated","area":"Etch",
                                 "defaultLowerLimit":5.0,"defaultUpperLimit":200.0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("CD-v2")))
                .andExpect(jsonPath("$.unit", is("um")))
                .andExpect(jsonPath("$.area", is("Etch")));
    }

    @Test
    void disableParameter_setsEnabledFalse() throws Exception {
        var p = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));

        mvc.perform(post("/api/parameters/" + p.getId() + "/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled", is(false)));
    }

    @Test
    void updateParameter_notFound_returns404() throws Exception {
        mvc.perform(put("/api/parameters/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"x","unit":"u"}
                                """))
                .andExpect(status().isNotFound());
    }
}
