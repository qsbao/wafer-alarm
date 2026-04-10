package com.waferalarm.collector;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CollectorConfig {

    private final Set<Long> ownedSourceSystemIds;

    @Autowired
    public CollectorConfig(@Value("${app.collector.owned-source-system-ids:}") String ids) {
        if (ids == null || ids.isBlank()) {
            this.ownedSourceSystemIds = Set.of();
        } else {
            this.ownedSourceSystemIds = Arrays.stream(ids.split(","))
                    .map(String::trim)
                    .map(Long::parseLong)
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    public CollectorConfig(Set<Long> ownedSourceSystemIds) {
        this.ownedSourceSystemIds = ownedSourceSystemIds != null ? ownedSourceSystemIds : Set.of();
    }

    public Set<Long> getOwnedSourceSystemIds() {
        return ownedSourceSystemIds;
    }

    public boolean ownsAll() {
        return ownedSourceSystemIds.isEmpty();
    }

    public boolean owns(Long sourceSystemId) {
        return ownsAll() || ownedSourceSystemIds.contains(sourceSystemId);
    }
}
