package com.waferalarm.collector;

import com.waferalarm.audit.AuditLogger;
import com.waferalarm.domain.SourceMappingEntity;
import com.waferalarm.domain.SourceMappingRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SourceMappingService {

    private static final int DEFAULT_POLL_INTERVAL = 300;
    private static final int DEFAULT_ROW_CAP = 10000;
    private static final int DEFAULT_QUERY_TIMEOUT = 30;

    private final SourceMappingRepository repo;
    private final BackfillRunner backfillRunner;
    private final AuditLogger auditLogger;

    public SourceMappingService(SourceMappingRepository repo, BackfillRunner backfillRunner, AuditLogger auditLogger) {
        this.repo = repo;
        this.backfillRunner = backfillRunner;
        this.auditLogger = auditLogger;
    }

    public List<SourceMappingEntity> listAll() {
        return repo.findAll();
    }

    public SourceMappingEntity create(SourceMappingRequest req) {
        validate(req);
        var entity = new SourceMappingEntity(
                req.sourceSystemId(), req.parameterId(),
                req.queryTemplate(), req.valueColumn(), req.watermarkColumn(),
                req.contextColumnMapping(),
                req.pollIntervalSeconds() != null ? req.pollIntervalSeconds() : DEFAULT_POLL_INTERVAL,
                req.rowCap() != null ? req.rowCap() : DEFAULT_ROW_CAP,
                req.queryTimeoutSeconds() != null ? req.queryTimeoutSeconds() : DEFAULT_QUERY_TIMEOUT);

        if (Boolean.TRUE.equals(req.backfillEnabled())) {
            entity.setBackfillEnabled(true);
            entity.setBackfillWindowDays(req.backfillWindowDays() != null ? req.backfillWindowDays() : 30);
        }

        entity = repo.save(entity);
        auditLogger.log("SOURCE_MAPPING", entity.getId(), "CREATE", null, smSnapshot(entity));

        if (entity.isBackfillEnabled()) {
            backfillRunner.triggerBackfill(entity.getId());
        }

        return entity;
    }

    public SourceMappingEntity update(Long id, SourceMappingRequest req) {
        validate(req);
        var entity = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Source mapping not found: " + id));
        var before = smSnapshot(entity);
        entity.setSourceSystemId(req.sourceSystemId());
        entity.setParameterId(req.parameterId());
        entity.setQueryTemplate(req.queryTemplate());
        entity.setValueColumn(req.valueColumn());
        entity.setWatermarkColumn(req.watermarkColumn());
        entity.setContextColumnMapping(req.contextColumnMapping());
        entity.setPollIntervalSeconds(req.pollIntervalSeconds() != null ? req.pollIntervalSeconds() : DEFAULT_POLL_INTERVAL);
        entity.setRowCap(req.rowCap() != null ? req.rowCap() : DEFAULT_ROW_CAP);
        entity.setQueryTimeoutSeconds(req.queryTimeoutSeconds() != null ? req.queryTimeoutSeconds() : DEFAULT_QUERY_TIMEOUT);
        entity.setBackfillEnabled(Boolean.TRUE.equals(req.backfillEnabled()));
        entity.setBackfillWindowDays(req.backfillWindowDays() != null ? req.backfillWindowDays() : 30);
        var saved = repo.save(entity);
        auditLogger.log("SOURCE_MAPPING", saved.getId(), "UPDATE", before, smSnapshot(saved));
        return saved;
    }

    public SourceMappingEntity disable(Long id) {
        var entity = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Source mapping not found: " + id));
        var before = smSnapshot(entity);
        entity.setEnabled(false);
        var saved = repo.save(entity);
        auditLogger.log("SOURCE_MAPPING", saved.getId(), "DISABLE", before, smSnapshot(saved));
        return saved;
    }

    public SourceMappingEntity enable(Long id) {
        var entity = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Source mapping not found: " + id));
        var before = smSnapshot(entity);
        entity.setEnabled(true);
        var saved = repo.save(entity);
        auditLogger.log("SOURCE_MAPPING", saved.getId(), "ENABLE", before, smSnapshot(saved));
        return saved;
    }

    private Map<String, Object> smSnapshot(SourceMappingEntity e) {
        return Map.of(
                "queryTemplate", e.getQueryTemplate(),
                "parameterId", e.getParameterId(),
                "enabled", e.isEnabled());
    }

    private void validate(SourceMappingRequest req) {
        if (req.queryTemplate() == null || req.queryTemplate().isBlank()) {
            throw new IllegalArgumentException("queryTemplate is required");
        }
        if (req.valueColumn() == null || req.valueColumn().isBlank()) {
            throw new IllegalArgumentException("valueColumn is required");
        }
        if (req.watermarkColumn() == null || req.watermarkColumn().isBlank()) {
            throw new IllegalArgumentException("watermarkColumn is required");
        }
        if (req.sourceSystemId() == null) {
            throw new IllegalArgumentException("sourceSystemId is required");
        }
        if (req.parameterId() == null) {
            throw new IllegalArgumentException("parameterId is required");
        }
    }
}
