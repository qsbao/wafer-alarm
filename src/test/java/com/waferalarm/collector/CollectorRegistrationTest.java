package com.waferalarm.collector;

import com.waferalarm.domain.CollectorRegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class CollectorRegistrationTest {

    @Autowired CollectorRegistrationRepository registrationRepo;
    @Autowired CollectorRegistrationService registrationService;
    @Autowired MockMvc mockMvc;

    @Test
    void register_persists_collector_with_owned_ids() {
        registrationRepo.deleteAll();
        // Re-register (simulates startup)
        registrationService.register();

        var reg = registrationRepo.findByCollectorId(registrationService.getCollectorId());
        assertThat(reg).isPresent();
        // Test profile has no owned-source-system-ids configured, so it registers as "*" (owns all)
        assertThat(reg.get().getOwnedSourceSystemIds()).isEqualTo("*");
    }

    @Test
    void detects_overlapping_owned_sets() throws Exception {
        registrationRepo.deleteAll();
        var reg1 = new com.waferalarm.domain.CollectorRegistrationEntity("collector-1", "1,2,3");
        var reg2 = new com.waferalarm.domain.CollectorRegistrationEntity("collector-2", "3,4,5");
        registrationRepo.save(reg1);
        registrationRepo.save(reg2);

        mockMvc.perform(get("/api/health/collectors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overlaps").isNotEmpty())
                .andExpect(jsonPath("$.overlaps[0].sourceSystemId").value(3));
    }

    @Test
    void no_overlap_with_disjoint_sets() throws Exception {
        registrationRepo.deleteAll();
        var reg1 = new com.waferalarm.domain.CollectorRegistrationEntity("collector-1", "1,2");
        var reg2 = new com.waferalarm.domain.CollectorRegistrationEntity("collector-2", "3,4");
        registrationRepo.save(reg1);
        registrationRepo.save(reg2);

        mockMvc.perform(get("/api/health/collectors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overlaps").isEmpty());
    }

    @Test
    void health_endpoint_lists_all_registrations() throws Exception {
        registrationRepo.deleteAll();
        var reg1 = new com.waferalarm.domain.CollectorRegistrationEntity("collector-1", "1,2");
        registrationRepo.save(reg1);

        mockMvc.perform(get("/api/health/collectors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrations").isNotEmpty())
                .andExpect(jsonPath("$.registrations[0].collectorId").value("collector-1"));
    }
}
