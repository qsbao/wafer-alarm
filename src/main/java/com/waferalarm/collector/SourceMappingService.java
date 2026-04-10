package com.waferalarm.collector;

import com.waferalarm.domain.SourceMappingEntity;
import com.waferalarm.domain.SourceMappingRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SourceMappingService {

    private static final int DEFAULT_POLL_INTERVAL = 300;
    private static final int DEFAULT_ROW_CAP = 10000;
    private static final int DEFAULT_QUERY_TIMEOUT = 30;

    private final SourceMappingRepository repo;
    private final BackfillRunner backfillRunner;

    public SourceMappingService(SourceMappingRepository repo, BackfillRunner backfillRunner) {
        this.repo = repo;
        this.backfillRunner = backfillRunner;
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

        if (entity.isBackfillEnabled()) {
            backfillRunner.triggerBackfill(entity.getId());
        }

        return entity;
    }

    public SourceMappingEntity update(Long id, SourceMappingRequest req) {
        validate(req);
        var entity = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Source mapping not found: " + id));
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
        return repo.save(entity);
    }

    public SourceMappingEntity disable(Long id) {
        var entity = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Source mapping not found: " + id));
        entity.setEnabled(false);
        return repo.save(entity);
    }

    public SourceMappingEntity enable(Long id) {
        var entity = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Source mapping not found: " + id));
        entity.setEnabled(true);
        return repo.save(entity);
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
