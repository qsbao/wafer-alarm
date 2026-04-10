package com.waferalarm.web;

import com.waferalarm.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AlarmControllerTest {

    @Autowired TestRestTemplate rest;
    @Autowired AlarmRepository alarmRepo;
    @Autowired ParameterRepository parameterRepo;
    @Autowired RuleRepository ruleRepo;
    @Autowired RuleVersionRepository versionRepo;
    @Autowired ParameterLimitRepository limitRepo;
    @Autowired MeasurementRepository measurementRepo;
    @Autowired RuleStateRepository ruleStateRepo;

    private static final ParameterizedTypeReference<List<AlarmDto>> ALARM_LIST =
            new ParameterizedTypeReference<>() {};

    private ParameterEntity paramA;
    private ParameterEntity paramB;
    private RuleEntity ruleA;
    private RuleEntity ruleB;

    @BeforeEach
    void clean() {
        alarmRepo.deleteAll();
        ruleStateRepo.deleteAll();
        measurementRepo.deleteAll();
        limitRepo.deleteAll();
        versionRepo.deleteAll();
        ruleRepo.deleteAll();
        parameterRepo.deleteAll();

        paramA = parameterRepo.save(new ParameterEntity("CD", "nm", null, null));
        paramB = parameterRepo.save(new ParameterEntity("Thickness", "um", null, null));
        ruleA = ruleRepo.save(new RuleEntity(paramA.getId(), RuleType.UPPER_THRESHOLD, Severity.WARNING));
        ruleB = ruleRepo.save(new RuleEntity(paramB.getId(), RuleType.UPPER_THRESHOLD, Severity.CRITICAL));
    }

    private AlarmEntity createAlarm(Long ruleId, Long parameterId, String contextKey,
                                     Severity severity, AlarmState state, Instant lastViolation) {
        AlarmSnapshot snap = new AlarmSnapshot(
                null, ruleId, parameterId, contextKey,
                state, severity, 1,
                lastViolation.minusSeconds(60), lastViolation,
                10.0, 5.0, 0, null, null);
        return alarmRepo.save(AlarmEntity.fromSnapshot(snap));
    }

    @Test
    void filter_by_parameterId() {
        createAlarm(ruleA.getId(), paramA.getId(), "tool=T1", Severity.WARNING, AlarmState.FIRING, Instant.now());
        createAlarm(ruleB.getId(), paramB.getId(), "tool=T2", Severity.CRITICAL, AlarmState.FIRING, Instant.now());

        var response = rest.exchange(
                "/api/alarms?parameterId=" + paramA.getId(),
                HttpMethod.GET, null, ALARM_LIST);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        List<AlarmDto> alarms = response.getBody();
        assertThat(alarms).hasSize(1);
        assertThat(alarms.get(0).parameterId()).isEqualTo(paramA.getId());
    }

    @Test
    void filter_by_tool() {
        createAlarm(ruleA.getId(), paramA.getId(), "tool=T1", Severity.WARNING, AlarmState.FIRING, Instant.now());
        createAlarm(ruleB.getId(), paramB.getId(), "tool=T2", Severity.CRITICAL, AlarmState.FIRING, Instant.now());

        var response = rest.exchange("/api/alarms?tool=T1", HttpMethod.GET, null, ALARM_LIST);

        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).contextKey()).contains("tool=T1");
    }

    @Test
    void filter_by_severity() {
        createAlarm(ruleA.getId(), paramA.getId(), "tool=T1", Severity.WARNING, AlarmState.FIRING, Instant.now());
        createAlarm(ruleB.getId(), paramB.getId(), "tool=T2", Severity.CRITICAL, AlarmState.FIRING, Instant.now());

        var response = rest.exchange("/api/alarms?severity=CRITICAL", HttpMethod.GET, null, ALARM_LIST);

        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).severity()).isEqualTo("CRITICAL");
    }

    @Test
    void filter_by_state() {
        createAlarm(ruleA.getId(), paramA.getId(), "tool=T1", Severity.WARNING, AlarmState.FIRING, Instant.now());
        createAlarm(ruleA.getId(), paramA.getId(), "tool=T3", Severity.WARNING, AlarmState.ACKNOWLEDGED, Instant.now());

        var response = rest.exchange("/api/alarms?state=FIRING", HttpMethod.GET, null, ALARM_LIST);

        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).state()).isEqualTo("FIRING");
    }

    @Test
    void combined_filters() {
        createAlarm(ruleA.getId(), paramA.getId(), "tool=T1", Severity.WARNING, AlarmState.FIRING, Instant.now());
        createAlarm(ruleA.getId(), paramA.getId(), "tool=T2", Severity.WARNING, AlarmState.FIRING, Instant.now());
        createAlarm(ruleB.getId(), paramB.getId(), "tool=T1", Severity.CRITICAL, AlarmState.FIRING, Instant.now());

        var response = rest.exchange(
                "/api/alarms?parameterId=" + paramA.getId() + "&tool=T1",
                HttpMethod.GET, null, ALARM_LIST);

        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).parameterId()).isEqualTo(paramA.getId());
        assertThat(response.getBody().get(0).contextKey()).contains("tool=T1");
    }

    @Test
    void default_sort_severity_desc_then_time_desc() {
        Instant earlier = Instant.now().minusSeconds(120);
        Instant later = Instant.now().minusSeconds(10);

        createAlarm(ruleA.getId(), paramA.getId(), "tool=T1", Severity.WARNING, AlarmState.FIRING, earlier);
        createAlarm(ruleB.getId(), paramB.getId(), "tool=T2", Severity.CRITICAL, AlarmState.FIRING, later);
        createAlarm(ruleA.getId(), paramA.getId(), "tool=T3", Severity.INFO, AlarmState.FIRING, later);
        createAlarm(ruleB.getId(), paramB.getId(), "tool=T4", Severity.CRITICAL, AlarmState.FIRING, earlier);

        var response = rest.exchange("/api/alarms", HttpMethod.GET, null, ALARM_LIST);

        List<AlarmDto> alarms = response.getBody();
        assertThat(alarms).hasSize(4);
        // CRITICAL first (sorted by time desc within severity)
        assertThat(alarms.get(0).severity()).isEqualTo("CRITICAL");
        assertThat(alarms.get(1).severity()).isEqualTo("CRITICAL");
        // Then WARNING
        assertThat(alarms.get(2).severity()).isEqualTo("WARNING");
        // Then INFO
        assertThat(alarms.get(3).severity()).isEqualTo("INFO");
    }

    @Test
    void resolved_endpoint_returns_recently_resolved() {
        // Resolved 1 hour ago - should appear
        var recent = createAlarm(ruleA.getId(), paramA.getId(), "tool=T1", Severity.WARNING, AlarmState.RESOLVED, Instant.now().minusSeconds(3600));
        // Active alarm - should NOT appear
        createAlarm(ruleB.getId(), paramB.getId(), "tool=T2", Severity.CRITICAL, AlarmState.FIRING, Instant.now());

        var response = rest.exchange("/api/alarms/resolved", HttpMethod.GET, null, ALARM_LIST);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        List<AlarmDto> alarms = response.getBody();
        assertThat(alarms).hasSize(1);
        assertThat(alarms.get(0).state()).isEqualTo("RESOLVED");
    }

    @Test
    void no_filters_returns_all_active() {
        createAlarm(ruleA.getId(), paramA.getId(), "tool=T1", Severity.WARNING, AlarmState.FIRING, Instant.now());
        createAlarm(ruleB.getId(), paramB.getId(), "tool=T2", Severity.CRITICAL, AlarmState.ACKNOWLEDGED, Instant.now());
        // Resolved should not appear
        createAlarm(ruleA.getId(), paramA.getId(), "tool=T3", Severity.INFO, AlarmState.RESOLVED, Instant.now());

        var response = rest.exchange("/api/alarms", HttpMethod.GET, null, ALARM_LIST);

        assertThat(response.getBody()).hasSize(2);
    }

    @Test
    void dto_includes_parameter_name() {
        createAlarm(ruleA.getId(), paramA.getId(), "tool=T1", Severity.WARNING, AlarmState.FIRING, Instant.now());

        var response = rest.exchange("/api/alarms", HttpMethod.GET, null, ALARM_LIST);

        assertThat(response.getBody().get(0).parameterName()).isEqualTo("CD");
    }
}
