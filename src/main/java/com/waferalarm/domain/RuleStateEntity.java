package com.waferalarm.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "rule_state")
public class RuleStateEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Column(name = "context_key_hash", nullable = false)
    private String contextKeyHash;

    @Column(name = "last_value", nullable = false)
    private double lastValue;

    @Column(name = "last_ts", nullable = false)
    private Instant lastTs;

    @Column(name = "last_wafer_id", nullable = false)
    private String lastWaferId;

    protected RuleStateEntity() {}

    public RuleStateEntity(Long ruleId, String contextKeyHash, double lastValue, Instant lastTs, String lastWaferId) {
        this.ruleId = ruleId;
        this.contextKeyHash = contextKeyHash;
        this.lastValue = lastValue;
        this.lastTs = lastTs;
        this.lastWaferId = lastWaferId;
    }

    public Long getId() { return id; }
    public Long getRuleId() { return ruleId; }
    public String getContextKeyHash() { return contextKeyHash; }
    public double getLastValue() { return lastValue; }
    public Instant getLastTs() { return lastTs; }
    public String getLastWaferId() { return lastWaferId; }

    public void setLastValue(double lastValue) { this.lastValue = lastValue; }
    public void setLastTs(Instant lastTs) { this.lastTs = lastTs; }
    public void setLastWaferId(String lastWaferId) { this.lastWaferId = lastWaferId; }

    public RuleStateData toData() {
        return new RuleStateData(ruleId, contextKeyHash, lastValue, lastTs, lastWaferId);
    }

    public void updateFromData(RuleStateData data) {
        this.lastValue = data.lastValue();
        this.lastTs = data.lastTs();
        this.lastWaferId = data.lastWaferId();
    }
}
