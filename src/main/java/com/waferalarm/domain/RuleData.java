package com.waferalarm.domain;

public record RuleData(
        long ruleId,
        long parameterId,
        RuleType ruleType,
        Severity severity,
        boolean enabled
) {}
