package com.waferalarm.evaluator;

import java.util.Map;

/**
 * Immutable representation of a parameter_limit row for LimitResolver.
 * context_match is the parsed JSON map from context_match_json.
 * An empty map represents the global fallback.
 */
public record ParameterLimitData(
        long limitId,
        long parameterId,
        Map<String, String> contextMatch,
        Double upperLimit,
        Double lowerLimit
) {}
