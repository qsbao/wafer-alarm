package com.waferalarm.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "eval_run")
public class EvalRunEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;

    @Column(name = "measurements_processed", nullable = false)
    private int measurementsProcessed;

    @Column(name = "alarms_fired", nullable = false)
    private int alarmsFired;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(columnDefinition = "TEXT")
    private String error;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected EvalRunEntity() {}

    public EvalRunEntity(Instant startedAt, Instant finishedAt,
                         int measurementsProcessed, int alarmsFired,
                         long durationMs, String error) {
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.measurementsProcessed = measurementsProcessed;
        this.alarmsFired = alarmsFired;
        this.durationMs = durationMs;
        this.error = error;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public int getMeasurementsProcessed() { return measurementsProcessed; }
    public int getAlarmsFired() { return alarmsFired; }
    public long getDurationMs() { return durationMs; }
    public String getError() { return error; }
    public Instant getCreatedAt() { return createdAt; }
}
