package com.waferalarm.catalog;

import com.waferalarm.domain.*;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UnmappedDataService {

    private final StagingUnmappedRepository unmappedRepo;
    private final StagingDismissedRepository dismissedRepo;
    private final SourceSystemRepository sourceSystemRepo;

    public UnmappedDataService(StagingUnmappedRepository unmappedRepo,
                               StagingDismissedRepository dismissedRepo,
                               SourceSystemRepository sourceSystemRepo) {
        this.unmappedRepo = unmappedRepo;
        this.dismissedRepo = dismissedRepo;
        this.sourceSystemRepo = sourceSystemRepo;
    }

    public void recordUnmapped(Long sourceSystemId, String columnKey, String sampleValue) {
        if (dismissedRepo.existsBySourceSystemIdAndColumnKey(sourceSystemId, columnKey)) {
            return;
        }

        var existing = unmappedRepo.findBySourceSystemIdAndColumnKey(sourceSystemId, columnKey);
        if (existing.isPresent()) {
            existing.get().incrementOccurrence(sampleValue);
            unmappedRepo.save(existing.get());
        } else {
            unmappedRepo.save(new StagingUnmappedEntity(sourceSystemId, columnKey, sampleValue));
        }
    }

    public Map<String, List<StagingUnmappedEntity>> listGroupedBySourceSystem() {
        var allEntries = unmappedRepo.findAll();
        var sourceSystemNames = sourceSystemRepo.findAll().stream()
                .collect(Collectors.toMap(SourceSystemEntity::getId, SourceSystemEntity::getName));

        return allEntries.stream()
                .collect(Collectors.groupingBy(
                        e -> sourceSystemNames.getOrDefault(e.getSourceSystemId(), "Unknown"),
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    public List<StagingUnmappedEntity> listAll() {
        return unmappedRepo.findAll();
    }

    public void dismiss(Long sourceSystemId, String columnKey) {
        unmappedRepo.findBySourceSystemIdAndColumnKey(sourceSystemId, columnKey)
                .ifPresent(unmappedRepo::delete);
        if (!dismissedRepo.existsBySourceSystemIdAndColumnKey(sourceSystemId, columnKey)) {
            dismissedRepo.save(new StagingDismissedEntity(sourceSystemId, columnKey));
        }
    }
}
