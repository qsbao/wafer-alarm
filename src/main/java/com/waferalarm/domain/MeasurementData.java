package com.waferalarm.domain;

import java.time.Instant;
import java.util.Map;

public record MeasurementData(
        long parameterId,
        String waferId,
        double value,
        Instant ts,
        String contextKey,
        Map<String, String> context
) {}
