package com.waferalarm.collector;

import com.waferalarm.domain.SourceSystemEntity;
import com.waferalarm.domain.SourceSystemRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SourceSystemService {

    private final SourceSystemRepository repo;

    public SourceSystemService(SourceSystemRepository repo) {
        this.repo = repo;
    }

    public List<SourceSystemEntity> listAll() {
        return repo.findAll();
    }

    public SourceSystemEntity create(SourceSystemRequest req) {
        validate(req);
        return repo.save(new SourceSystemEntity(
                req.name(), req.host(), req.port(), req.dbName(),
                req.credentialsRef(), req.networkZone(), req.timezone()));
    }

    public SourceSystemEntity update(Long id, SourceSystemRequest req) {
        validate(req);
        var entity = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Source system not found: " + id));
        entity.setName(req.name());
        entity.setHost(req.host());
        entity.setPort(req.port());
        entity.setDbName(req.dbName());
        entity.setCredentialsRef(req.credentialsRef());
        entity.setNetworkZone(req.networkZone());
        entity.setTimezone(req.timezone() != null ? req.timezone() : "UTC");
        return repo.save(entity);
    }

    public SourceSystemEntity disable(Long id) {
        var entity = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Source system not found: " + id));
        entity.setEnabled(false);
        return repo.save(entity);
    }

    public SourceSystemEntity enable(Long id) {
        var entity = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Source system not found: " + id));
        entity.setEnabled(true);
        return repo.save(entity);
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
