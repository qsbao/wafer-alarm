package com.waferalarm.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "rule_version")
public class RuleVersionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false)
    private RuleType ruleType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private String author;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "absolute_delta")
    private Double absoluteDelta;

    @Column(name = "percentage_delta")
    private Double percentageDelta;

    @Column(name = "minimum_baseline")
    private Double minimumBaseline;

    protected RuleVersionEntity() {}

    public RuleVersionEntity(Long ruleId, RuleType ruleType, Severity severity, boolean enabled, String author) {
        this.ruleId = ruleId;
        this.ruleType = ruleType;
        this.severity = severity;
        this.enabled = enabled;
        this.author = author;
        this.createdAt = Instant.now();
    }

    public RuleVersionEntity(Long ruleId, RuleType ruleType, Severity severity, boolean enabled, String author,
                             Double absoluteDelta, Double percentageDelta, Double minimumBaseline) {
        this(ruleId, ruleType, severity, enabled, author);
        this.absoluteDelta = absoluteDelta;
        this.percentageDelta = percentageDelta;
        this.minimumBaseline = minimumBaseline;
    }

    public Long getId() { return id; }
    public Long getRuleId() { return ruleId; }
    public RuleType getRuleType() { return ruleType; }
    public Severity getSeverity() { return severity; }
    public boolean isEnabled() { return enabled; }
    public String getAuthor() { return author; }
    public Instant getCreatedAt() { return createdAt; }
    public Double getAbsoluteDelta() { return absoluteDelta; }
    public Double getPercentageDelta() { return percentageDelta; }
    public Double getMinimumBaseline() { return minimumBaseline; }
}
