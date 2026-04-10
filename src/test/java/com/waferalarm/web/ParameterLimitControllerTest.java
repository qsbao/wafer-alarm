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

import static org.assertj.core.api.Assertions.assertThat;
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
    @Autowired LimitAuditLogRepository auditRepo;

    private Long paramId;

    @BeforeEach
    void setUp() {
        auditRepo.deleteAll();
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

    @Test
    void creating_limit_writes_audit_record() throws Exception {
        mvc.perform(post("/api/parameter-limits")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"parameterId":%d,"contextMatchJson":"{\\"tool\\":\\"TOOL-A\\"}","upperLimit":120.0}
                    """.formatted(paramId)))
                .andExpect(status().isCreated());

        var audits = auditRepo.findAll();
        assertThat(audits).hasSize(1);
        assertThat(audits.getFirst().getAction()).isEqualTo("CREATE");
        assertThat(audits.getFirst().getLimitId()).isNotNull();
    }

    @Test
    void updating_limit_writes_audit_record() throws Exception {
        var limit = limitRepo.save(new ParameterLimitEntity(paramId, "{}", 100.0, null));

        mvc.perform(put("/api/parameter-limits/" + limit.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"parameterId":%d,"upperLimit":150.0}
                    """.formatted(paramId)))
                .andExpect(status().isOk());

        var audits = auditRepo.findAll();
        assertThat(audits).hasSize(1);
        assertThat(audits.getFirst().getAction()).isEqualTo("UPDATE");
    }

    @Test
    void create_limit_for_parameter_tool_recipe_tuple() throws Exception {
        mvc.perform(post("/api/parameter-limits")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"parameterId":%d,"contextMatchJson":"{\\"tool\\":\\"TOOL-A\\",\\"recipe\\":\\"RCP-1\\"}","upperLimit":120.0,"lowerLimit":10.0}
                    """.formatted(paramId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parameterId", is(paramId.intValue())))
                .andExpect(jsonPath("$.contextMatchJson", is("{\"tool\":\"TOOL-A\",\"recipe\":\"RCP-1\"}")))
                .andExpect(jsonPath("$.upperLimit", is(120.0)))
                .andExpect(jsonPath("$.lowerLimit", is(10.0)));
    }

    @Test
    void edit_limit_updates_values() throws Exception {
        var limit = limitRepo.save(new ParameterLimitEntity(paramId, "{\"tool\":\"TOOL-A\"}", 100.0, 5.0));

        mvc.perform(put("/api/parameter-limits/" + limit.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"parameterId":%d,"contextMatchJson":"{\\"tool\\":\\"TOOL-A\\",\\"recipe\\":\\"RCP-1\\"}","upperLimit":150.0,"lowerLimit":15.0}
                    """.formatted(paramId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upperLimit", is(150.0)))
                .andExpect(jsonPath("$.lowerLimit", is(15.0)))
                .andExpect(jsonPath("$.contextMatchJson", is("{\"tool\":\"TOOL-A\",\"recipe\":\"RCP-1\"}")));
    }

    @Test
    void deleting_limit_writes_audit_record() throws Exception {
        var limit = limitRepo.save(new ParameterLimitEntity(paramId, "{}", 100.0, null));

        mvc.perform(delete("/api/parameter-limits/" + limit.getId()))
                .andExpect(status().isNoContent());

        var audits = auditRepo.findAll();
        assertThat(audits).hasSize(1);
        assertThat(audits.getFirst().getAction()).isEqualTo("DELETE");
        assertThat(audits.getFirst().getLimitId()).isEqualTo(limit.getId());
    }
}
