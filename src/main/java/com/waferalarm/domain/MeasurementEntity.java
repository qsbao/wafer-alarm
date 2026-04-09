package com.waferalarm.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "measurement")
public class MeasurementEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parameter_id", nullable = false)
    private Long parameterId;

    @Column(name = "wafer_id", nullable = false)
    private String waferId;

    @Column(nullable = false)
    private double value;

    @Column(nullable = false)
    private Instant ts;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    private String tool;
    private String recipe;
    private String product;

    @Column(name = "lot_id")
    private String lotId;

    @Column(name = "context_json")
    private String contextJson;

    protected MeasurementEntity() {}

    public MeasurementEntity(Long parameterId, String waferId, double value, Instant ts, String tool, String recipe, String product, String lotId) {
        this.parameterId = parameterId;
        this.waferId = waferId;
        this.value = value;
        this.ts = ts;
        this.ingestedAt = Instant.now();
        this.tool = tool;
        this.recipe = recipe;
        this.product = product;
        this.lotId = lotId;
    }

    public Long getId() { return id; }
    public Long getParameterId() { return parameterId; }
    public String getWaferId() { return waferId; }
    public double getValue() { return value; }
    public Instant getTs() { return ts; }
    public Instant getIngestedAt() { return ingestedAt; }
    public String getTool() { return tool; }
    public String getRecipe() { return recipe; }
    public String getProduct() { return product; }
    public String getLotId() { return lotId; }
    public String getContextJson() { return contextJson; }

    public String deriveContextKey() {
        return "tool=" + (tool != null ? tool : "");
    }
}
