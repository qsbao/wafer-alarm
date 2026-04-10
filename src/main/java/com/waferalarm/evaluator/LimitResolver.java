package com.waferalarm.evaluator;

import com.waferalarm.domain.LimitData;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.logging.Logger;

/**
 * Pure function: resolves the most-specific matching limit for a measurement's context.
 * No DB dependency — the Spring @Component is for wiring convenience only.
 */
@Component
public class LimitResolver {

    private static final Logger log = Logger.getLogger(LimitResolver.class.getName());

    public Optional<LimitData> resolve(
            long parameterId,
            Map<String, String> measurementContext,
            List<ParameterLimitData> limits) {

        // Filter to limits for this parameter that match the measurement context
        List<ParameterLimitData> matching = new ArrayList<>();
        for (ParameterLimitData limit : limits) {
            if (limit.parameterId() != parameterId) continue;
            if (matches(limit.contextMatch(), measurementContext)) {
                matching.add(limit);
            }
        }

        if (matching.isEmpty()) return Optional.empty();

        // Find the maximum specificity (number of context keys)
        int maxSpecificity = matching.stream()
                .mapToInt(l -> l.contextMatch().size())
                .max()
                .orElse(0);

        List<ParameterLimitData> mostSpecific = matching.stream()
                .filter(l -> l.contextMatch().size() == maxSpecificity)
                .toList();

        if (mostSpecific.size() > 1) {
            log.warning("Found " + mostSpecific.size() + " equally-specific limits for parameter "
                    + parameterId + " — using highest id as tie-breaker");
        }

        // Tie-breaker: highest id
        ParameterLimitData winner = mostSpecific.stream()
                .max(Comparator.comparingLong(ParameterLimitData::limitId))
                .get();

        return Optional.of(new LimitData(winner.parameterId(), winner.upperLimit(), winner.lowerLimit()));
    }

    /**
     * A limit matches if every key-value in contextMatch is present in the measurement context.
     * An empty contextMatch (global fallback) always matches.
     */
    private boolean matches(Map<String, String> contextMatch, Map<String, String> measurementContext) {
        for (Map.Entry<String, String> entry : contextMatch.entrySet()) {
            String actual = measurementContext.get(entry.getKey());
            if (!entry.getValue().equals(actual)) return false;
        }
        return true;
    }
}
