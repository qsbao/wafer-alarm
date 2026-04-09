package com.waferalarm.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "eval_watermark")
public class EvalWatermark {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "watermark_key", nullable = false, unique = true)
    private String watermarkKey;

    @Column(name = "last_ingested_at", nullable = false)
    private Instant lastIngestedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EvalWatermark() {}

    public EvalWatermark(String watermarkKey, Instant lastIngestedAt) {
        this.watermarkKey = watermarkKey;
        this.lastIngestedAt = lastIngestedAt;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getWatermarkKey() { return watermarkKey; }
    public Instant getLastIngestedAt() { return lastIngestedAt; }

    public void advanceTo(Instant newWatermark) {
        this.lastIngestedAt = newWatermark;
        this.updatedAt = Instant.now();
    }
}
