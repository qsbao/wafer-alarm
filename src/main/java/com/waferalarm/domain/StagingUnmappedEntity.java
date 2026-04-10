package com.waferalarm.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "staging_unmapped")
public class StagingUnmappedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_system_id", nullable = false)
    private Long sourceSystemId;

    @Column(name = "column_key", nullable = false)
    private String columnKey;

    @Column(name = "sample_value", length = 1000)
    private String sampleValue;

    @Column(name = "occurrence_count", nullable = false)
    private int occurrenceCount = 1;

    @Column(name = "first_seen", nullable = false, updatable = false)
    private Instant firstSeen;

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;

    protected StagingUnmappedEntity() {}

    public StagingUnmappedEntity(Long sourceSystemId, String columnKey, String sampleValue) {
        this.sourceSystemId = sourceSystemId;
        this.columnKey = columnKey;
        this.sampleValue = sampleValue;
        this.occurrenceCount = 1;
        this.firstSeen = Instant.now();
        this.lastSeen = Instant.now();
    }

    public Long getId() { return id; }
    public Long getSourceSystemId() { return sourceSystemId; }
    public String getColumnKey() { return columnKey; }
    public String getSampleValue() { return sampleValue; }
    public int getOccurrenceCount() { return occurrenceCount; }
    public Instant getFirstSeen() { return firstSeen; }
    public Instant getLastSeen() { return lastSeen; }

    public void incrementOccurrence(String newSampleValue) {
        this.occurrenceCount++;
        this.lastSeen = Instant.now();
        if (newSampleValue != null) {
            this.sampleValue = newSampleValue;
        }
    }
}
