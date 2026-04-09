package com.waferalarm.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "source_system")
public class SourceSystemEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private int port;

    @Column(name = "db_name")
    private String dbName;

    @Column(name = "credentials_ref")
    private String credentialsRef;

    @Column(name = "network_zone")
    private String networkZone;

    @Column(nullable = false)
    private String timezone = "UTC";

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SourceSystemEntity() {}

    public SourceSystemEntity(String name, String host, int port, String dbName,
                              String credentialsRef, String networkZone, String timezone) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.dbName = dbName;
        this.credentialsRef = credentialsRef;
        this.networkZone = networkZone;
        this.timezone = timezone != null ? timezone : "UTC";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getDbName() { return dbName; }
    public String getCredentialsRef() { return credentialsRef; }
    public String getNetworkZone() { return networkZone; }
    public String getTimezone() { return timezone; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setName(String name) { this.name = name; }
    public void setHost(String host) { this.host = host; }
    public void setPort(int port) { this.port = port; }
    public void setDbName(String dbName) { this.dbName = dbName; }
    public void setCredentialsRef(String credentialsRef) { this.credentialsRef = credentialsRef; }
    public void setNetworkZone(String networkZone) { this.networkZone = networkZone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
