package com.waferalarm.domain;

import java.time.Instant;

public record AlarmEvent(
        long ruleId,
        long parameterId,
        String contextKey,
        Severity severity,
        double violatingValue,
        double thresholdValue,
        Instant violationTime,
        String waferId,
        Long ruleVersionId
) {}
