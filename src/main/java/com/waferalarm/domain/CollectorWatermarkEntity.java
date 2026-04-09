package com.waferalarm.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "collector_watermark")
public class CollectorWatermarkEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_key", nullable = false, unique = true)
    private String sourceKey;

    @Column(name = "last_ts", nullable = false)
    private Instant lastTs;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CollectorWatermarkEntity() {}

    public CollectorWatermarkEntity(String sourceKey, Instant lastTs) {
        this.sourceKey = sourceKey;
        this.lastTs = lastTs;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getSourceKey() { return sourceKey; }
    public Instant getLastTs() { return lastTs; }

    public void advanceTo(Instant newTs) {
        this.lastTs = newTs;
        this.updatedAt = Instant.now();
    }
}
