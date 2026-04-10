package com.waferalarm.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "backfill_task")
public class BackfillTaskEntity {

    public enum Status { PENDING, RUNNING, COMPLETED, FAILED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_mapping_id", nullable = false)
    private Long sourceMappingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "backfill_from", nullable = false)
    private Instant backfillFrom;

    @Column(name = "backfill_to", nullable = false)
    private Instant backfillTo;

    @Column(name = "rows_processed", nullable = false)
    private int rowsProcessed = 0;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(columnDefinition = "TEXT")
    private String error;

    @Column(name = "last_processed_ts")
    private Instant lastProcessedTs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BackfillTaskEntity() {}

    public BackfillTaskEntity(Long sourceMappingId, Instant backfillFrom, Instant backfillTo) {
        this.sourceMappingId = sourceMappingId;
        this.backfillFrom = backfillFrom;
        this.backfillTo = backfillTo;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getSourceMappingId() { return sourceMappingId; }
    public Status getStatus() { return status; }
    public Instant getBackfillFrom() { return backfillFrom; }
    public Instant getBackfillTo() { return backfillTo; }
    public int getRowsProcessed() { return rowsProcessed; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public String getError() { return error; }
    public Instant getLastProcessedTs() { return lastProcessedTs; }
    public Instant getCreatedAt() { return createdAt; }

    public void markRunning() {
        this.status = Status.RUNNING;
        this.startedAt = Instant.now();
    }

    public void markCompleted() {
        this.status = Status.COMPLETED;
        this.finishedAt = Instant.now();
    }

    public void markFailed(String error) {
        this.status = Status.FAILED;
        this.error = error;
        this.finishedAt = Instant.now();
    }

    public void recordProgress(int additionalRows, Instant processedUpTo) {
        this.rowsProcessed += additionalRows;
        this.lastProcessedTs = processedUpTo;
    }
}
