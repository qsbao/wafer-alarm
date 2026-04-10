package com.waferalarm.health;

import java.time.Instant;

public record ConnectorHealth(
        Long sourceMappingId,
        Instant lastSuccessfulTick,
        long totalRowsPulled,
        long errorCount,
        boolean stalled,
        int consecutiveZeroRowTicks
) {}
