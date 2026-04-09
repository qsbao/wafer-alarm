package com.waferalarm.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "alarm")
public class AlarmEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Column(name = "parameter_id", nullable = false)
    private Long parameterId;

    @Column(name = "context_key")
    private String contextKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlarmState state;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(name = "occurrence_count", nullable = false)
    private int occurrenceCount;

    @Column(name = "first_violation_at", nullable = false)
    private Instant firstViolationAt;

    @Column(name = "last_violation_at", nullable = false)
    private Instant lastViolationAt;

    @Column(name = "last_value")
    private Double lastValue;

    @Column(name = "threshold_value")
    private Double thresholdValue;

    @Column(name = "consecutive_clean_count", nullable = false)
    private int consecutiveCleanCount;

    @Column(name = "suppressed_until")
    private Instant suppressedUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AlarmEntity() {}

    public static AlarmEntity fromSnapshot(AlarmSnapshot snapshot) {
        AlarmEntity e = new AlarmEntity();
        e.ruleId = snapshot.ruleId();
        e.parameterId = snapshot.parameterId();
        e.contextKey = snapshot.contextKey();
        e.state = snapshot.state();
        e.severity = snapshot.severity();
        e.occurrenceCount = snapshot.occurrenceCount();
        e.firstViolationAt = snapshot.firstViolationAt();
        e.lastViolationAt = snapshot.lastViolationAt();
        e.lastValue = snapshot.lastValue();
        e.thresholdValue = snapshot.thresholdValue();
        e.consecutiveCleanCount = snapshot.consecutiveCleanCount();
        e.suppressedUntil = snapshot.suppressedUntil();
        e.createdAt = Instant.now();
        e.updatedAt = Instant.now();
        return e;
    }

    public void updateFromSnapshot(AlarmSnapshot snapshot) {
        this.state = snapshot.state();
        this.occurrenceCount = snapshot.occurrenceCount();
        this.lastViolationAt = snapshot.lastViolationAt();
        this.lastValue = snapshot.lastValue();
        this.consecutiveCleanCount = snapshot.consecutiveCleanCount();
        this.suppressedUntil = snapshot.suppressedUntil();
        this.updatedAt = Instant.now();
    }

    public AlarmSnapshot toSnapshot() {
        return new AlarmSnapshot(id, ruleId, parameterId, contextKey,
                state, severity, occurrenceCount, firstViolationAt,
                lastViolationAt, lastValue != null ? lastValue : 0.0,
                thresholdValue != null ? thresholdValue : 0.0,
                consecutiveCleanCount, suppressedUntil);
    }

    public Long getId() { return id; }
    public Long getRuleId() { return ruleId; }
    public Long getParameterId() { return parameterId; }
    public String getContextKey() { return contextKey; }
    public AlarmState getState() { return state; }
    public Severity getSeverity() { return severity; }
    public int getOccurrenceCount() { return occurrenceCount; }
    public Instant getFirstViolationAt() { return firstViolationAt; }
    public Instant getLastViolationAt() { return lastViolationAt; }
    public Double getLastValue() { return lastValue; }
    public Double getThresholdValue() { return thresholdValue; }
    public int getConsecutiveCleanCount() { return consecutiveCleanCount; }
    public Instant getSuppressedUntil() { return suppressedUntil; }
    public Instant getCreatedAt() { return createdAt; }
}
