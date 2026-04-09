package com.waferalarm.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "rule")
public class RuleEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parameter_id", nullable = false)
    private Long parameterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false)
    private RuleType ruleType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "current_version_id")
    private Long currentVersionId;

    protected RuleEntity() {}

    public RuleEntity(Long parameterId, RuleType ruleType, Severity severity) {
        this.parameterId = parameterId;
        this.ruleType = ruleType;
        this.severity = severity;
        this.enabled = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getParameterId() { return parameterId; }
    public RuleType getRuleType() { return ruleType; }
    public Severity getSeverity() { return severity; }
    public boolean isEnabled() { return enabled; }

    public RuleData toRuleData() {
        return new RuleData(id, parameterId, ruleType, severity, enabled, currentVersionId);
    }

    public Long getCurrentVersionId() { return currentVersionId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setCurrentVersionId(Long currentVersionId) { this.currentVersionId = currentVersionId; }
    public void setSeverity(Severity severity) { this.severity = severity; this.updatedAt = Instant.now(); }
    public void setEnabled(boolean enabled) { this.enabled = enabled; this.updatedAt = Instant.now(); }
    public void setRuleType(RuleType ruleType) { this.ruleType = ruleType; this.updatedAt = Instant.now(); }
}
