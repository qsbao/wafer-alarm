package com.waferalarm.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "limit_audit_log")
public class LimitAuditLogEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "limit_id", nullable = false)
    private Long limitId;

    @Column(name = "parameter_id", nullable = false)
    private Long parameterId;

    @Column(nullable = false)
    private String action;

    @Column(name = "context_match_json")
    private String contextMatchJson;

    @Column(name = "upper_limit")
    private Double upperLimit;

    @Column(name = "lower_limit")
    private Double lowerLimit;

    @Column(nullable = false)
    private String actor;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LimitAuditLogEntity() {}

    public LimitAuditLogEntity(Long limitId, Long parameterId, String action,
                               String contextMatchJson, Double upperLimit, Double lowerLimit) {
        this.limitId = limitId;
        this.parameterId = parameterId;
        this.action = action;
        this.contextMatchJson = contextMatchJson;
        this.upperLimit = upperLimit;
        this.lowerLimit = lowerLimit;
        this.actor = "system";
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getLimitId() { return limitId; }
    public Long getParameterId() { return parameterId; }
    public String getAction() { return action; }
    public String getContextMatchJson() { return contextMatchJson; }
    public Double getUpperLimit() { return upperLimit; }
    public Double getLowerLimit() { return lowerLimit; }
    public String getActor() { return actor; }
    public Instant getCreatedAt() { return createdAt; }
}
