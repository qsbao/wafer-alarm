package com.waferalarm.domain;

public record RuleData(
        long ruleId,
        long parameterId,
        RuleType ruleType,
        Severity severity,
        boolean enabled,
        Long currentVersionId,
        Double absoluteDelta,
        Double percentageDelta,
        Double minimumBaseline
) {
    public RuleData(long ruleId, long parameterId, RuleType ruleType,
                    Severity severity, boolean enabled, Long currentVersionId) {
        this(ruleId, parameterId, ruleType, severity, enabled, currentVersionId, null, null, null);
    }
}
