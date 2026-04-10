package com.waferalarm.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "collector_registration")
public class CollectorRegistrationEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "collector_id", nullable = false, unique = true)
    private String collectorId;

    @Column(name = "owned_source_system_ids", nullable = false, columnDefinition = "TEXT")
    private String ownedSourceSystemIds;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "last_heartbeat_at", nullable = false)
    private Instant lastHeartbeatAt;

    protected CollectorRegistrationEntity() {}

    public CollectorRegistrationEntity(String collectorId, String ownedSourceSystemIds) {
        this.collectorId = collectorId;
        this.ownedSourceSystemIds = ownedSourceSystemIds;
        this.registeredAt = Instant.now();
        this.lastHeartbeatAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getCollectorId() { return collectorId; }
    public String getOwnedSourceSystemIds() { return ownedSourceSystemIds; }
    public Instant getRegisteredAt() { return registeredAt; }
    public Instant getLastHeartbeatAt() { return lastHeartbeatAt; }

    public void setOwnedSourceSystemIds(String ownedSourceSystemIds) {
        this.ownedSourceSystemIds = ownedSourceSystemIds;
    }

    public void heartbeat() {
        this.lastHeartbeatAt = Instant.now();
    }
}
