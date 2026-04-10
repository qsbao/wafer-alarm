package com.waferalarm.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "staging_dismissed")
public class StagingDismissedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_system_id", nullable = false)
    private Long sourceSystemId;

    @Column(name = "column_key", nullable = false)
    private String columnKey;

    @Column(name = "dismissed_at", nullable = false)
    private Instant dismissedAt;

    protected StagingDismissedEntity() {}

    public StagingDismissedEntity(Long sourceSystemId, String columnKey) {
        this.sourceSystemId = sourceSystemId;
        this.columnKey = columnKey;
        this.dismissedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getSourceSystemId() { return sourceSystemId; }
    public String getColumnKey() { return columnKey; }
    public Instant getDismissedAt() { return dismissedAt; }
}
