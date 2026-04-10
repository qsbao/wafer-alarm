package com.waferalarm.web;

import com.waferalarm.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RuleControllerTest {

    @Autowired TestRestTemplate rest;
    @Autowired RuleRepository ruleRepo;
    @Autowired RuleVersionRepository versionRepo;
    @Autowired ParameterRepository parameterRepo;
    @Autowired AlarmRepository alarmRepo;
    @Autowired ParameterLimitRepository limitRepo;
    @Autowired MeasurementRepository measurementRepo;

    @BeforeEach
    void clean() {
        alarmRepo.deleteAll();
        measurementRepo.deleteAll();
        limitRepo.deleteAll();
        versionRepo.deleteAll();
        ruleRepo.deleteAll();
        parameterRepo.deleteAll();
    }

    @Test
    void create_upper_threshold_rule() {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", null, null));
        var body = new RuleController.RuleRequest(param.getId(), RuleType.UPPER_THRESHOLD, Severity.WARNING, "engineer1");

        var response = rest.postForEntity("/api/rules", body, RuleController.RuleDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var dto = response.getBody();
        assertThat(dto).isNotNull();
        assertThat(dto.ruleType()).isEqualTo("UPPER_THRESHOLD");
        assertThat(dto.severity()).isEqualTo("WARNING");
        assertThat(dto.enabled()).isTrue();
        assertThat(dto.currentVersionId()).isNotNull();

        // Version was created
        var versions = versionRepo.findByRuleIdOrderByCreatedAtDesc(dto.id());
        assertThat(versions).hasSize(1);
        assertThat(versions.getFirst().getAuthor()).isEqualTo("engineer1");
    }

    @Test
    void create_lower_threshold_rule_with_independent_severity() {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", null, null));
        var body = new RuleController.RuleRequest(param.getId(), RuleType.LOWER_THRESHOLD, Severity.CRITICAL, "engineer2");

        var response = rest.postForEntity("/api/rules", body, RuleController.RuleDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().ruleType()).isEqualTo("LOWER_THRESHOLD");
        assertThat(response.getBody().severity()).isEqualTo("CRITICAL");
    }

    @Test
    void edit_rule_creates_new_version() {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", null, null));
        var createBody = new RuleController.RuleRequest(param.getId(), RuleType.UPPER_THRESHOLD, Severity.WARNING, "eng");
        var created = rest.postForEntity("/api/rules", createBody, RuleController.RuleDto.class).getBody();

        // Update severity
        var updateBody = new RuleController.RuleRequest(param.getId(), RuleType.UPPER_THRESHOLD, Severity.CRITICAL, "eng2");
        var response = rest.exchange("/api/rules/" + created.id(), HttpMethod.PUT,
                new HttpEntity<>(updateBody), RuleController.RuleDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().severity()).isEqualTo("CRITICAL");
        assertThat(response.getBody().currentVersionId()).isNotEqualTo(created.currentVersionId());

        var versions = versionRepo.findByRuleIdOrderByCreatedAtDesc(created.id());
        assertThat(versions).hasSize(2);
        assertThat(versions.getFirst().getAuthor()).isEqualTo("eng2");
    }

    @Test
    void disable_rule_stops_it_from_firing() {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", null, null));
        var createBody = new RuleController.RuleRequest(param.getId(), RuleType.UPPER_THRESHOLD, Severity.WARNING, "eng");
        var created = rest.postForEntity("/api/rules", createBody, RuleController.RuleDto.class).getBody();

        var response = rest.postForEntity("/api/rules/" + created.id() + "/disable", null, RuleController.RuleDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().enabled()).isFalse();

        // Check in DB
        var rule = ruleRepo.findById(created.id()).get();
        assertThat(rule.isEnabled()).isFalse();
    }

    @Test
    void enable_disabled_rule() {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", null, null));
        var createBody = new RuleController.RuleRequest(param.getId(), RuleType.UPPER_THRESHOLD, Severity.WARNING, "eng");
        var created = rest.postForEntity("/api/rules", createBody, RuleController.RuleDto.class).getBody();

        rest.postForEntity("/api/rules/" + created.id() + "/disable", null, RuleController.RuleDto.class);
        var response = rest.postForEntity("/api/rules/" + created.id() + "/enable", null, RuleController.RuleDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().enabled()).isTrue();
    }

    @Test
    void version_history() {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", null, null));
        var createBody = new RuleController.RuleRequest(param.getId(), RuleType.UPPER_THRESHOLD, Severity.WARNING, "eng");
        var created = rest.postForEntity("/api/rules", createBody, RuleController.RuleDto.class).getBody();

        // Edit to create second version
        var update = new RuleController.RuleRequest(param.getId(), RuleType.UPPER_THRESHOLD, Severity.CRITICAL, "eng2");
        rest.exchange("/api/rules/" + created.id(), HttpMethod.PUT, new HttpEntity<>(update), RuleController.RuleDto.class);

        var response = rest.getForEntity("/api/rules/" + created.id() + "/versions", RuleController.RuleVersionDto[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
    }
}
