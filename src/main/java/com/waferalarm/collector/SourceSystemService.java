package com.waferalarm.collector;

import com.waferalarm.audit.AuditLogger;
import com.waferalarm.domain.SourceSystemEntity;
import com.waferalarm.domain.SourceSystemRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SourceSystemService {

    private final SourceSystemRepository repo;
    private final AuditLogger auditLogger;

    public SourceSystemService(SourceSystemRepository repo, AuditLogger auditLogger) {
        this.repo = repo;
        this.auditLogger = auditLogger;
    }

    public List<SourceSystemEntity> listAll() {
        return repo.findAll();
    }

    public SourceSystemEntity create(SourceSystemRequest req) {
        validate(req);
        var saved = repo.save(new SourceSystemEntity(
                req.name(), req.host(), req.port(), req.dbName(),
                req.credentialsRef(), req.networkZone(), req.timezone()));
        auditLogger.log("SOURCE_SYSTEM", saved.getId(), "CREATE", null, ssSnapshot(saved));
        return saved;
    }

    public SourceSystemEntity update(Long id, SourceSystemRequest req) {
        validate(req);
        var entity = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Source system not found: " + id));
        var before = ssSnapshot(entity);
        entity.setName(req.name());
        entity.setHost(req.host());
        entity.setPort(req.port());
        entity.setDbName(req.dbName());
        entity.setCredentialsRef(req.credentialsRef());
        entity.setNetworkZone(req.networkZone());
        entity.setTimezone(req.timezone() != null ? req.timezone() : "UTC");
        var saved = repo.save(entity);
        auditLogger.log("SOURCE_SYSTEM", saved.getId(), "UPDATE", before, ssSnapshot(saved));
        return saved;
    }

    public SourceSystemEntity disable(Long id) {
        var entity = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Source system not found: " + id));
        var before = ssSnapshot(entity);
        entity.setEnabled(false);
        var saved = repo.save(entity);
        auditLogger.log("SOURCE_SYSTEM", saved.getId(), "DISABLE", before, ssSnapshot(saved));
        return saved;
    }

    public SourceSystemEntity enable(Long id) {
        var entity = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Source system not found: " + id));
        var before = ssSnapshot(entity);
        entity.setEnabled(true);
        var saved = repo.save(entity);
        auditLogger.log("SOURCE_SYSTEM", saved.getId(), "ENABLE", before, ssSnapshot(saved));
        return saved;
    }

    private Map<String, Object> ssSnapshot(SourceSystemEntity e) {
        return Map.of(
                "name", e.getName(),
                "host", e.getHost(),
                "port", e.getPort(),
                "enabled", e.isEnabled());
    }

    private void validate(SourceSystemRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (req.host() == null || req.host().isBlank()) {
            throw new IllegalArgumentException("host is required");
        }
        if (req.port() == null || req.port() <= 0) {
            throw new IllegalArgumentException("port must be positive");
        }
    }
}
