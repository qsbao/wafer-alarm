package com.waferalarm.audit;

import com.waferalarm.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditLogIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired AuditLogRepository auditLogRepo;
    @Autowired ParameterRepository parameterRepo;
    @Autowired ParameterLimitRepository limitRepo;
    @Autowired LimitAuditLogRepository limitAuditRepo;
    @Autowired RuleRepository ruleRepo;
    @Autowired RuleVersionRepository ruleVersionRepo;
    @Autowired RuleStateRepository ruleStateRepo;
    @Autowired AlarmRepository alarmRepo;
    @Autowired MeasurementRepository measurementRepo;
    @Autowired SourceSystemRepository sourceSystemRepo;
    @Autowired SourceMappingRepository sourceMappingRepo;

    @BeforeEach
    void setUp() {
        alarmRepo.deleteAll();
        measurementRepo.deleteAll();
        auditLogRepo.deleteAll();
        limitAuditRepo.deleteAll();
        limitRepo.deleteAll();
        ruleStateRepo.deleteAll();
        ruleVersionRepo.deleteAll();
        ruleRepo.deleteAll();
        sourceMappingRepo.deleteAll();
        sourceSystemRepo.deleteAll();
        parameterRepo.deleteAll();
    }

    // --- Parameter mutations ---

    @Test
    void creating_parameter_writes_audit_row() throws Exception {
        mvc.perform(post("/api/parameters")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"CD","unit":"nm","defaultUpperLimit":100.0}
                    """))
                .andExpect(status().isCreated());

        var audits = auditLogRepo.findAll();
        assertThat(audits).hasSize(1);
        assertThat(audits.getFirst().getEntityType()).isEqualTo("PARAMETER");
        assertThat(audits.getFirst().getAction()).isEqualTo("CREATE");
        assertThat(audits.getFirst().getAfterJson()).contains("\"name\":\"CD\"");
        assertThat(audits.getFirst().getBeforeJson()).isNull();
    }

    @Test
    void updating_parameter_writes_audit_row_with_before_and_after() throws Exception {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));

        mvc.perform(put("/api/parameters/" + param.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"CD-v2","unit":"um","defaultUpperLimit":200.0}
                    """))
                .andExpect(status().isOk());

        var audits = auditLogRepo.findAll();
        assertThat(audits).hasSize(1);
        assertThat(audits.getFirst().getAction()).isEqualTo("UPDATE");
        assertThat(audits.getFirst().getBeforeJson()).contains("\"name\":\"CD\"");
        assertThat(audits.getFirst().getAfterJson()).contains("\"name\":\"CD-v2\"");
    }

    @Test
    void disabling_parameter_writes_audit_row() throws Exception {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));

        mvc.perform(post("/api/parameters/" + param.getId() + "/disable"))
                .andExpect(status().isOk());

        var audits = auditLogRepo.findAll();
        assertThat(audits).hasSize(1);
        assertThat(audits.getFirst().getAction()).isEqualTo("DISABLE");
        assertThat(audits.getFirst().getEntityType()).isEqualTo("PARAMETER");
    }

    // --- Rule mutations ---

    @Test
    void creating_rule_writes_audit_row() throws Exception {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));

        mvc.perform(post("/api/rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"parameterId":%d,"ruleType":"UPPER_THRESHOLD","severity":"CRITICAL","author":"eng1"}
                    """.formatted(param.getId())))
                .andExpect(status().isCreated());

        var audits = auditLogRepo.findFiltered("RULE", null, "CREATE", null, null);
        assertThat(audits).hasSize(1);
        assertThat(audits.getFirst().getAfterJson()).contains("UPPER_THRESHOLD");
    }

    @Test
    void updating_rule_writes_audit_row() throws Exception {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        var rule = ruleRepo.save(new RuleEntity(param.getId(), RuleType.UPPER_THRESHOLD, Severity.WARNING));
        var version = ruleVersionRepo.save(new RuleVersionEntity(
                rule.getId(), RuleType.UPPER_THRESHOLD, Severity.WARNING, true, "system"));
        rule.setCurrentVersionId(version.getId());
        ruleRepo.save(rule);

        mvc.perform(put("/api/rules/" + rule.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"parameterId":%d,"ruleType":"UPPER_THRESHOLD","severity":"CRITICAL","author":"eng1"}
                    """.formatted(param.getId())))
                .andExpect(status().isOk());

        var audits = auditLogRepo.findFiltered("RULE", rule.getId(), "UPDATE", null, null);
        assertThat(audits).hasSize(1);
        assertThat(audits.getFirst().getBeforeJson()).contains("WARNING");
        assertThat(audits.getFirst().getAfterJson()).contains("CRITICAL");
    }

    @Test
    void disabling_rule_writes_audit_row() throws Exception {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        var rule = ruleRepo.save(new RuleEntity(param.getId(), RuleType.UPPER_THRESHOLD, Severity.WARNING));
        var version = ruleVersionRepo.save(new RuleVersionEntity(
                rule.getId(), RuleType.UPPER_THRESHOLD, Severity.WARNING, true, "system"));
        rule.setCurrentVersionId(version.getId());
        ruleRepo.save(rule);

        mvc.perform(post("/api/rules/" + rule.getId() + "/disable"))
                .andExpect(status().isOk());

        var audits = auditLogRepo.findFiltered("RULE", rule.getId(), "DISABLE", null, null);
        assertThat(audits).hasSize(1);
    }

    @Test
    void enabling_rule_writes_audit_row() throws Exception {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        var rule = ruleRepo.save(new RuleEntity(param.getId(), RuleType.UPPER_THRESHOLD, Severity.WARNING));
        rule.setEnabled(false);
        rule = ruleRepo.save(rule);

        mvc.perform(post("/api/rules/" + rule.getId() + "/enable"))
                .andExpect(status().isOk());

        var audits = auditLogRepo.findFiltered("RULE", rule.getId(), "ENABLE", null, null);
        assertThat(audits).hasSize(1);
    }

    // --- Alarm state changes ---

    @Test
    void acknowledging_alarm_writes_audit_row() throws Exception {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        var rule = ruleRepo.save(new RuleEntity(param.getId(), RuleType.UPPER_THRESHOLD, Severity.CRITICAL));
        var alarm = alarmRepo.save(AlarmEntity.fromSnapshot(new AlarmSnapshot(
                null, rule.getId(), param.getId(), "tool=A",
                AlarmState.FIRING, Severity.CRITICAL, 1,
                Instant.now(), Instant.now(), 105.0, 100.0, 0, null, null)));

        mvc.perform(post("/api/alarms/" + alarm.getId() + "/acknowledge"))
                .andExpect(status().isOk());

        var audits = auditLogRepo.findFiltered("ALARM", alarm.getId(), "ACKNOWLEDGE", null, null);
        assertThat(audits).hasSize(1);
        assertThat(audits.getFirst().getBeforeJson()).contains("FIRING");
        assertThat(audits.getFirst().getAfterJson()).contains("ACKNOWLEDGED");
    }

    @Test
    void resolving_alarm_writes_audit_row() throws Exception {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        var rule = ruleRepo.save(new RuleEntity(param.getId(), RuleType.UPPER_THRESHOLD, Severity.CRITICAL));
        var alarm = alarmRepo.save(AlarmEntity.fromSnapshot(new AlarmSnapshot(
                null, rule.getId(), param.getId(), "tool=A",
                AlarmState.FIRING, Severity.CRITICAL, 1,
                Instant.now(), Instant.now(), 105.0, 100.0, 0, null, null)));

        mvc.perform(post("/api/alarms/" + alarm.getId() + "/resolve"))
                .andExpect(status().isOk());

        var audits = auditLogRepo.findFiltered("ALARM", alarm.getId(), "RESOLVE", null, null);
        assertThat(audits).hasSize(1);
    }

    @Test
    void suppressing_alarm_writes_audit_row() throws Exception {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        var rule = ruleRepo.save(new RuleEntity(param.getId(), RuleType.UPPER_THRESHOLD, Severity.CRITICAL));
        var alarm = alarmRepo.save(AlarmEntity.fromSnapshot(new AlarmSnapshot(
                null, rule.getId(), param.getId(), "tool=A",
                AlarmState.FIRING, Severity.CRITICAL, 1,
                Instant.now(), Instant.now(), 105.0, 100.0, 0, null, null)));

        mvc.perform(post("/api/alarms/" + alarm.getId() + "/suppress")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"until":"2099-01-01T00:00:00Z"}
                    """))
                .andExpect(status().isOk());

        var audits = auditLogRepo.findFiltered("ALARM", alarm.getId(), "SUPPRESS", null, null);
        assertThat(audits).hasSize(1);
    }

    // --- Parameter limit mutations ---

    @Test
    void creating_limit_writes_to_unified_audit_log() throws Exception {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));

        mvc.perform(post("/api/parameter-limits")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"parameterId":%d,"contextMatchJson":"{}","upperLimit":120.0}
                    """.formatted(param.getId())))
                .andExpect(status().isCreated());

        var audits = auditLogRepo.findFiltered("PARAMETER_LIMIT", null, "CREATE", null, null);
        assertThat(audits).hasSize(1);
    }

    @Test
    void updating_limit_writes_to_unified_audit_log() throws Exception {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        var limit = limitRepo.save(new ParameterLimitEntity(param.getId(), "{}", 100.0, null));

        mvc.perform(put("/api/parameter-limits/" + limit.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"parameterId":%d,"upperLimit":150.0}
                    """.formatted(param.getId())))
                .andExpect(status().isOk());

        var audits = auditLogRepo.findFiltered("PARAMETER_LIMIT", limit.getId(), "UPDATE", null, null);
        assertThat(audits).hasSize(1);
    }

    @Test
    void deleting_limit_writes_to_unified_audit_log() throws Exception {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        var limit = limitRepo.save(new ParameterLimitEntity(param.getId(), "{}", 100.0, null));

        mvc.perform(delete("/api/parameter-limits/" + limit.getId()))
                .andExpect(status().isNoContent());

        var audits = auditLogRepo.findFiltered("PARAMETER_LIMIT", limit.getId(), "DELETE", null, null);
        assertThat(audits).hasSize(1);
    }

    // --- Source system mutations ---

    @Test
    void creating_source_system_writes_audit_row() throws Exception {
        mvc.perform(post("/api/source-systems")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"MES","host":"db1.local","port":3306,"dbName":"mes","credentialsRef":"ref","networkZone":"zone1"}
                    """))
                .andExpect(status().isCreated());

        var audits = auditLogRepo.findFiltered("SOURCE_SYSTEM", null, "CREATE", null, null);
        assertThat(audits).hasSize(1);
        assertThat(audits.getFirst().getAfterJson()).contains("MES");
    }

    @Test
    void updating_source_system_writes_audit_row() throws Exception {
        var ss = sourceSystemRepo.save(new SourceSystemEntity("MES", "db1.local", 3306, "mes", "ref", "zone1", "UTC"));

        mvc.perform(put("/api/source-systems/" + ss.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"MES-v2","host":"db2.local","port":3307,"dbName":"mes","credentialsRef":"ref","networkZone":"zone1"}
                    """))
                .andExpect(status().isOk());

        var audits = auditLogRepo.findFiltered("SOURCE_SYSTEM", ss.getId(), "UPDATE", null, null);
        assertThat(audits).hasSize(1);
    }

    @Test
    void disabling_source_system_writes_audit_row() throws Exception {
        var ss = sourceSystemRepo.save(new SourceSystemEntity("MES", "db1.local", 3306, "mes", "ref", "zone1", "UTC"));

        mvc.perform(post("/api/source-systems/" + ss.getId() + "/disable"))
                .andExpect(status().isOk());

        var audits = auditLogRepo.findFiltered("SOURCE_SYSTEM", ss.getId(), "DISABLE", null, null);
        assertThat(audits).hasSize(1);
    }

    // --- Source mapping mutations ---

    @Test
    void creating_source_mapping_writes_audit_row() throws Exception {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        var ss = sourceSystemRepo.save(new SourceSystemEntity("MES", "db1.local", 3306, "mes", "ref", "zone1", "UTC"));

        mvc.perform(post("/api/source-mappings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sourceSystemId":%d,"parameterId":%d,"queryTemplate":"SELECT *","valueColumn":"val",
                     "watermarkColumn":"ts","contextColumnMapping":"{}"}
                    """.formatted(ss.getId(), param.getId())))
                .andExpect(status().isCreated());

        var audits = auditLogRepo.findFiltered("SOURCE_MAPPING", null, "CREATE", null, null);
        assertThat(audits).hasSize(1);
    }

    @Test
    void updating_source_mapping_writes_audit_row() throws Exception {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        var ss = sourceSystemRepo.save(new SourceSystemEntity("MES", "db1.local", 3306, "mes", "ref", "zone1", "UTC"));
        var mapping = sourceMappingRepo.save(new SourceMappingEntity(
                ss.getId(), param.getId(), "SELECT *", "val", "ts", "{}", 300, 10000, 30));

        mvc.perform(put("/api/source-mappings/" + mapping.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sourceSystemId":%d,"parameterId":%d,"queryTemplate":"SELECT id, val","valueColumn":"val",
                     "watermarkColumn":"ts","contextColumnMapping":"{}"}
                    """.formatted(ss.getId(), param.getId())))
                .andExpect(status().isOk());

        var audits = auditLogRepo.findFiltered("SOURCE_MAPPING", mapping.getId(), "UPDATE", null, null);
        assertThat(audits).hasSize(1);
    }

    @Test
    void disabling_source_mapping_writes_audit_row() throws Exception {
        var param = parameterRepo.save(new ParameterEntity("CD", "nm", 100.0, null));
        var ss = sourceSystemRepo.save(new SourceSystemEntity("MES", "db1.local", 3306, "mes", "ref", "zone1", "UTC"));
        var mapping = sourceMappingRepo.save(new SourceMappingEntity(
                ss.getId(), param.getId(), "SELECT *", "val", "ts", "{}", 300, 10000, 30));

        mvc.perform(post("/api/source-mappings/" + mapping.getId() + "/disable"))
                .andExpect(status().isOk());

        var audits = auditLogRepo.findFiltered("SOURCE_MAPPING", mapping.getId(), "DISABLE", null, null);
        assertThat(audits).hasSize(1);
    }
}
