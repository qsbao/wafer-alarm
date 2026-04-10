package com.waferalarm.evaluator;

import com.waferalarm.domain.RuleType;
import com.waferalarm.domain.Severity;

import java.time.Instant;

public record BacktestRequest(
        long parameterId,
        RuleType ruleType,
        Severity severity,
        Double absoluteDelta,
        Double percentageDelta,
        Double minimumBaseline,
        Instant from,
        Instant to
) {}
