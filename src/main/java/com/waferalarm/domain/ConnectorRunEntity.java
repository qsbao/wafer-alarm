package com.waferalarm.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "connector_run")
public class ConnectorRunEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_mapping_id", nullable = false)
    private Long sourceMappingId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;

    @Column(name = "rows_pulled", nullable = false)
    private int rowsPulled;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(columnDefinition = "TEXT")
    private String error;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ConnectorRunEntity() {}

    public ConnectorRunEntity(Long sourceMappingId, Instant startedAt, Instant finishedAt,
                              int rowsPulled, long durationMs, String error) {
        this.sourceMappingId = sourceMappingId;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.rowsPulled = rowsPulled;
        this.durationMs = durationMs;
        this.error = error;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getSourceMappingId() { return sourceMappingId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public int getRowsPulled() { return rowsPulled; }
    public long getDurationMs() { return durationMs; }
    public String getError() { return error; }
    public Instant getCreatedAt() { return createdAt; }
}
