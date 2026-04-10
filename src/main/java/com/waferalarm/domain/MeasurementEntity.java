package com.waferalarm.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "measurement")
public class MeasurementEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parameter_id", nullable = false)
    private Long parameterId;

    @Column(name = "wafer_id", nullable = false)
    private String waferId;

    @Column(name = "measured_value", nullable = false)
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

    @Column(nullable = false)
    private boolean backfilled = false;

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
    public boolean isBackfilled() { return backfilled; }

    public void markBackfilled() { this.backfilled = true; }

    public String deriveContextKey() {
        return "tool=" + (tool != null ? tool : "");
    }

    public Map<String, String> deriveContextMap() {
        Map<String, String> ctx = new LinkedHashMap<>();
        if (tool != null) ctx.put("tool", tool);
        if (recipe != null) ctx.put("recipe", recipe);
        if (product != null) ctx.put("product", product);
        if (lotId != null) ctx.put("lot_id", lotId);
        return ctx;
    }
}
