package com.waferalarm.collector;

import com.waferalarm.domain.CollectorRegistrationEntity;
import com.waferalarm.domain.CollectorRegistrationRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CollectorRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(CollectorRegistrationService.class);

    private final CollectorRegistrationRepository registrationRepo;
    private final CollectorConfig collectorConfig;
    private final String collectorId;

    public CollectorRegistrationService(CollectorRegistrationRepository registrationRepo,
                                        CollectorConfig collectorConfig) {
        this.registrationRepo = registrationRepo;
        this.collectorConfig = collectorConfig;
        this.collectorId = UUID.randomUUID().toString();
    }

    @PostConstruct
    public void register() {
        String ownedIds = collectorConfig.ownsAll()
                ? "*"
                : collectorConfig.getOwnedSourceSystemIds().stream()
                    .sorted()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));

        var existing = registrationRepo.findByCollectorId(collectorId);
        if (existing.isPresent()) {
            var reg = existing.get();
            reg.setOwnedSourceSystemIds(ownedIds);
            reg.heartbeat();
            registrationRepo.save(reg);
        } else {
            registrationRepo.save(new CollectorRegistrationEntity(collectorId, ownedIds));
        }
        log.info("Collector registered: id={}, ownedSourceSystems={}", collectorId, ownedIds);
    }

    @Scheduled(fixedDelay = 60000)
    public void heartbeat() {
        registrationRepo.findByCollectorId(collectorId).ifPresent(reg -> {
            reg.heartbeat();
            registrationRepo.save(reg);
        });
    }

    public String getCollectorId() {
        return collectorId;
    }

    public List<OverlapInfo> detectOverlaps() {
        List<CollectorRegistrationEntity> all = registrationRepo.findAll();

        // Build map: sourceSystemId -> list of collector IDs that claim it
        Map<Long, List<String>> claimants = new HashMap<>();
        for (var reg : all) {
            Set<Long> ids = parseOwnedIds(reg.getOwnedSourceSystemIds());
            for (Long id : ids) {
                claimants.computeIfAbsent(id, k -> new ArrayList<>()).add(reg.getCollectorId());
            }
        }

        // Check for "*" (owns-all) collectors
        List<String> ownsAllCollectors = all.stream()
                .filter(r -> "*".equals(r.getOwnedSourceSystemIds()))
                .map(CollectorRegistrationEntity::getCollectorId)
                .toList();

        List<OverlapInfo> overlaps = new ArrayList<>();

        // If there are multiple owns-all collectors, or an owns-all + specific, that's an overlap
        if (ownsAllCollectors.size() > 1) {
            overlaps.add(new OverlapInfo(null, ownsAllCollectors,
                    "Multiple collectors configured to own all source systems"));
        }
        if (!ownsAllCollectors.isEmpty() && all.size() > ownsAllCollectors.size()) {
            overlaps.add(new OverlapInfo(null, all.stream().map(CollectorRegistrationEntity::getCollectorId).toList(),
                    "A collector configured to own all source systems coexists with zone-specific collectors"));
        }

        // Specific ID overlaps
        for (var entry : claimants.entrySet()) {
            if (entry.getValue().size() > 1) {
                overlaps.add(new OverlapInfo(entry.getKey(), entry.getValue(),
                        "Source system " + entry.getKey() + " is claimed by multiple collectors"));
            }
        }

        return overlaps;
    }

    private Set<Long> parseOwnedIds(String ownedIds) {
        if (ownedIds == null || ownedIds.isBlank() || "*".equals(ownedIds)) {
            return Set.of();
        }
        return Arrays.stream(ownedIds.split(","))
                .map(String::trim)
                .map(Long::parseLong)
                .collect(Collectors.toSet());
    }

    public record OverlapInfo(Long sourceSystemId, List<String> collectorIds, String message) {}
}
