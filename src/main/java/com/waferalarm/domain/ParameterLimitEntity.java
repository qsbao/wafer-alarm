package com.waferalarm.domain;

import com.waferalarm.evaluator.ParameterLimitData;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "parameter_limit")
public class ParameterLimitEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parameter_id", nullable = false)
    private Long parameterId;

    @Column(name = "context_match_json", nullable = false)
    private String contextMatchJson;

    @Column(name = "upper_limit")
    private Double upperLimit;

    @Column(name = "lower_limit")
    private Double lowerLimit;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ParameterLimitEntity() {}

    public ParameterLimitEntity(Long parameterId, String contextMatchJson, Double upperLimit, Double lowerLimit) {
        this.parameterId = parameterId;
        this.contextMatchJson = contextMatchJson;
        this.upperLimit = upperLimit;
        this.lowerLimit = lowerLimit;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getParameterId() { return parameterId; }
    public String getContextMatchJson() { return contextMatchJson; }
    public Double getUpperLimit() { return upperLimit; }
    public Double getLowerLimit() { return lowerLimit; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setUpperLimit(Double upperLimit) { this.upperLimit = upperLimit; this.updatedAt = Instant.now(); }
    public void setLowerLimit(Double lowerLimit) { this.lowerLimit = lowerLimit; this.updatedAt = Instant.now(); }
    public void setContextMatchJson(String contextMatchJson) { this.contextMatchJson = contextMatchJson; this.updatedAt = Instant.now(); }

    public ParameterLimitData toData() {
        Map<String, String> contextMatch = parseContextJson(contextMatchJson);
        return new ParameterLimitData(id, parameterId, contextMatch, upperLimit, lowerLimit);
    }

    private Map<String, String> parseContextJson(String json) {
        if (json == null || json.isBlank() || json.equals("{}")) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        // Simple JSON parsing for {"key":"value",...} format
        String inner = json.trim();
        if (inner.startsWith("{")) inner = inner.substring(1);
        if (inner.endsWith("}")) inner = inner.substring(0, inner.length() - 1);
        inner = inner.trim();
        if (inner.isEmpty()) return result;
        for (String pair : inner.split(",")) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                result.put(unquote(kv[0].trim()), unquote(kv[1].trim()));
            }
        }
        return result;
    }

    private String unquote(String s) {
        if (s.startsWith("\"") && s.endsWith("\"")) return s.substring(1, s.length() - 1);
        return s;
    }
}
