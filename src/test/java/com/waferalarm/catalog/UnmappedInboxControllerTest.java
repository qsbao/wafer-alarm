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
class UnmappedInboxControllerTest {

    @Autowired MockMvc mvc;
    @Autowired StagingUnmappedRepository unmappedRepo;
    @Autowired StagingDismissedRepository dismissedRepo;
    @Autowired SourceSystemRepository sourceSystemRepo;
    @Autowired SourceMappingRepository sourceMappingRepo;
    @Autowired ConnectorRunRepository connectorRunRepo;
    @Autowired MeasurementRepository measurementRepo;
    @Autowired CollectorWatermarkRepository watermarkRepo;
    @Autowired BackfillTaskRepository backfillTaskRepo;
    @Autowired ParameterRepository parameterRepo;
    @Autowired AlarmRepository alarmRepo;
    @Autowired RuleRepository ruleRepo;
    @Autowired RuleVersionRepository ruleVersionRepo;
    @Autowired ParameterLimitRepository parameterLimitRepo;
    @Autowired UnmappedDataService service;

    private SourceSystemEntity source;

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

        source = sourceSystemRepo.save(new SourceSystemEntity(
                "MES-1", "localhost", 3306, "mes", null, "zone-a", "UTC"));
    }

    @Test
    void listUnmapped_returnsGroupedBySource() throws Exception {
        service.recordUnmapped(source.getId(), "col_a", "10.5");
        service.recordUnmapped(source.getId(), "col_b", "hello");

        mvc.perform(get("/api/unmapped"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.['MES-1']", hasSize(2)))
                .andExpect(jsonPath("$.['MES-1'][0].columnKey", is("col_a")));
    }

    @Test
    void listUnmappedFlat_returnsAllEntries() throws Exception {
        service.recordUnmapped(source.getId(), "col_a", "10.5");

        mvc.perform(get("/api/unmapped/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].columnKey", is("col_a")))
                .andExpect(jsonPath("$[0].sampleValue", is("10.5")))
                .andExpect(jsonPath("$[0].occurrenceCount", is(1)));
    }

    @Test
    void dismiss_removesEntryAndReturns200() throws Exception {
        service.recordUnmapped(source.getId(), "noisy_col", "abc");

        mvc.perform(post("/api/unmapped/dismiss")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceSystemId\":" + source.getId() + ",\"columnKey\":\"noisy_col\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/unmapped/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
