package com.waferalarm.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "source_mapping")
public class SourceMappingEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_system_id", nullable = false)
    private Long sourceSystemId;

    @Column(name = "parameter_id", nullable = false)
    private Long parameterId;

    @Column(name = "query_template", nullable = false, columnDefinition = "TEXT")
    private String queryTemplate;

    @Column(name = "value_column", nullable = false)
    private String valueColumn;

    @Column(name = "watermark_column", nullable = false)
    private String watermarkColumn;

    @Column(name = "context_column_mapping", columnDefinition = "TEXT")
    private String contextColumnMapping;

    @Column(name = "poll_interval_seconds", nullable = false)
    private int pollIntervalSeconds = 300;

    @Column(name = "row_cap", nullable = false)
    private int rowCap = 10000;

    @Column(name = "query_timeout_seconds", nullable = false)
    private int queryTimeoutSeconds = 30;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "backfill_enabled", nullable = false)
    private boolean backfillEnabled = false;

    @Column(name = "backfill_window_days", nullable = false)
    private int backfillWindowDays = 30;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SourceMappingEntity() {}

    public SourceMappingEntity(Long sourceSystemId, Long parameterId, String queryTemplate,
                               String valueColumn, String watermarkColumn, String contextColumnMapping,
                               int pollIntervalSeconds, int rowCap, int queryTimeoutSeconds) {
        this.sourceSystemId = sourceSystemId;
        this.parameterId = parameterId;
        this.queryTemplate = queryTemplate;
        this.valueColumn = valueColumn;
        this.watermarkColumn = watermarkColumn;
        this.contextColumnMapping = contextColumnMapping;
        this.pollIntervalSeconds = pollIntervalSeconds;
        this.rowCap = rowCap;
        this.queryTimeoutSeconds = queryTimeoutSeconds;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getSourceSystemId() { return sourceSystemId; }
    public Long getParameterId() { return parameterId; }
    public String getQueryTemplate() { return queryTemplate; }
    public String getValueColumn() { return valueColumn; }
    public String getWatermarkColumn() { return watermarkColumn; }
    public String getContextColumnMapping() { return contextColumnMapping; }
    public int getPollIntervalSeconds() { return pollIntervalSeconds; }
    public int getRowCap() { return rowCap; }
    public int getQueryTimeoutSeconds() { return queryTimeoutSeconds; }
    public boolean isEnabled() { return enabled; }
    public boolean isBackfillEnabled() { return backfillEnabled; }
    public int getBackfillWindowDays() { return backfillWindowDays; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setSourceSystemId(Long sourceSystemId) { this.sourceSystemId = sourceSystemId; }
    public void setParameterId(Long parameterId) { this.parameterId = parameterId; }
    public void setQueryTemplate(String queryTemplate) { this.queryTemplate = queryTemplate; }
    public void setValueColumn(String valueColumn) { this.valueColumn = valueColumn; }
    public void setWatermarkColumn(String watermarkColumn) { this.watermarkColumn = watermarkColumn; }
    public void setContextColumnMapping(String contextColumnMapping) { this.contextColumnMapping = contextColumnMapping; }
    public void setPollIntervalSeconds(int pollIntervalSeconds) { this.pollIntervalSeconds = pollIntervalSeconds; }
    public void setRowCap(int rowCap) { this.rowCap = rowCap; }
    public void setQueryTimeoutSeconds(int queryTimeoutSeconds) { this.queryTimeoutSeconds = queryTimeoutSeconds; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setBackfillEnabled(boolean backfillEnabled) { this.backfillEnabled = backfillEnabled; }
    public void setBackfillWindowDays(int backfillWindowDays) { this.backfillWindowDays = backfillWindowDays; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
