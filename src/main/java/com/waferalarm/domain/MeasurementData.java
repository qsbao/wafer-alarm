package com.waferalarm.domain;

import java.time.Instant;

public record MeasurementData(
        long parameterId,
        String waferId,
        double value,
        Instant ts,
        String contextKey
) {}
