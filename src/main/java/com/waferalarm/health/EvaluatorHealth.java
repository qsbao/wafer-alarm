package com.waferalarm.health;

import java.time.Instant;

public record EvaluatorHealth(
        Instant lastTick,
        long watermarkLagSeconds,
        long errorCount
) {}
