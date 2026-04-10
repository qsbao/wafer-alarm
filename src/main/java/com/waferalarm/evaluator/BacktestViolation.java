package com.waferalarm.evaluator;

import java.time.Instant;

public record BacktestViolation(
        Instant when,
        String contextKey,
        double value,
        double thresholdValue,
        String severity,
        String waferId
) {}
