package com.waferalarm.domain;

import java.time.Instant;

public record RuleStateData(
        long ruleId,
        String contextKey,
        double lastValue,
        Instant lastTs,
        String lastWaferId
) {}
