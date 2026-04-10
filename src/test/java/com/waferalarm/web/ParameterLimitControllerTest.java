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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ParameterLimitControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ParameterRepository parameterRepo;
    @Autowired ParameterLimitRepository limitRepo;

    private Long paramId;

    @BeforeEach
    void setUp() {
        limitRepo.deleteAll();
        parameterRepo.deleteAll();
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        paramId = param.getId();
    }

    @Test
    void lists_limits_with_ambiguity_warnings() throws Exception {
        // Two limits with same specificity (both have 1 key: "tool")
        limitRepo.save(new ParameterLimitEntity(paramId, "{\"tool\":\"TOOL-A\"}", 120.0, null));
        limitRepo.save(new ParameterLimitEntity(paramId, "{\"tool\":\"TOOL-B\"}", 130.0, null));

        // These are NOT ambiguous — they match different contexts.
        // Ambiguity = same parameter, same context_match_json keys AND values
        // Actually, per LimitResolver: ambiguity = two limits with same specificity
        // that BOTH match the same measurement context. But at listing time,
        // we flag rows that have identical context keys (same key set).

        // Two limits with identical context: both tool=TOOL-A → ambiguous
        limitRepo.save(new ParameterLimitEntity(paramId, "{\"tool\":\"TOOL-A\"}", 140.0, null));

        mvc.perform(get("/api/parameter-limits/by-parameter/" + paramId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                // The two tool=TOOL-A limits should be flagged as ambiguous
                .andExpect(jsonPath("$[?(@.upperLimit == 120.0)].ambiguous", contains(true)))
                .andExpect(jsonPath("$[?(@.upperLimit == 140.0)].ambiguous", contains(true)))
                // The tool=TOOL-B limit is not ambiguous
                .andExpect(jsonPath("$[?(@.upperLimit == 130.0)].ambiguous", contains(false)));
    }
}
