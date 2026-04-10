package com.waferalarm.catalog;

import com.waferalarm.audit.AuditLogger;
import com.waferalarm.domain.ParameterEntity;
import com.waferalarm.domain.ParameterRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class CatalogService {

    private final ParameterRepository repo;
    private final AuditLogger auditLogger;

    public CatalogService(ParameterRepository repo, AuditLogger auditLogger) {
        this.repo = repo;
        this.auditLogger = auditLogger;
    }

    public List<ParameterEntity> listAll() {
        return repo.findAll();
    }

    public ParameterEntity create(ParameterRequest req) {
        validateLimits(req.defaultLowerLimit(), req.defaultUpperLimit());
        var entity = new ParameterEntity(req.name(), req.unit(), req.defaultUpperLimit(), req.defaultLowerLimit());
        entity.setDescription(req.description());
        entity.setArea(req.area());
        var saved = repo.save(entity);
        auditLogger.log("PARAMETER", saved.getId(), "CREATE", null, toSnapshot(saved));
        return saved;
    }

    public ParameterEntity update(Long id, ParameterRequest req) {
        validateLimits(req.defaultLowerLimit(), req.defaultUpperLimit());
        var entity = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Parameter not found: " + id));
        var before = toSnapshot(entity);
        entity.setName(req.name());
        entity.setUnit(req.unit());
        entity.setDescription(req.description());
        entity.setArea(req.area());
        entity.setDefaultLowerLimit(req.defaultLowerLimit());
        entity.setDefaultUpperLimit(req.defaultUpperLimit());
        var saved = repo.save(entity);
        auditLogger.log("PARAMETER", saved.getId(), "UPDATE", before, toSnapshot(saved));
        return saved;
    }

    public ParameterEntity disable(Long id) {
        var entity = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Parameter not found: " + id));
        var before = toSnapshot(entity);
        entity.setEnabled(false);
        var saved = repo.save(entity);
        auditLogger.log("PARAMETER", saved.getId(), "DISABLE", before, toSnapshot(saved));
        return saved;
    }

    private Map<String, Object> toSnapshot(ParameterEntity e) {
        return Map.of(
                "name", e.getName(),
                "unit", e.getUnit() != null ? e.getUnit() : "",
                "enabled", e.isEnabled(),
                "defaultUpperLimit", e.getDefaultUpperLimit() != null ? e.getDefaultUpperLimit() : "",
                "defaultLowerLimit", e.getDefaultLowerLimit() != null ? e.getDefaultLowerLimit() : "");
    }

    private void validateLimits(Double lower, Double upper) {
        if (lower != null && upper != null && lower >= upper) {
            throw new IllegalArgumentException("lower limit must be less than upper limit");
        }
    }
}
