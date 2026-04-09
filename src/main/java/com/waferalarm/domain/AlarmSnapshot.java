package com.waferalarm.domain;

import java.time.Instant;

public record AlarmSnapshot(
        Long alarmId,
        long ruleId,
        long parameterId,
        String contextKey,
        AlarmState state,
        Severity severity,
        int occurrenceCount,
        Instant firstViolationAt,
        Instant lastViolationAt,
        double lastValue,
        double thresholdValue,
        int consecutiveCleanCount,
        Instant suppressedUntil
) {
    public AlarmSnapshot withState(AlarmState newState) {
        return new AlarmSnapshot(alarmId, ruleId, parameterId, contextKey,
                newState, severity, occurrenceCount, firstViolationAt,
                lastViolationAt, lastValue, thresholdValue,
                consecutiveCleanCount, suppressedUntil);
    }

    public AlarmSnapshot withOccurrence(double value, Instant violationTime) {
        return new AlarmSnapshot(alarmId, ruleId, parameterId, contextKey,
                state, severity, occurrenceCount + 1, firstViolationAt,
                violationTime, value, thresholdValue,
                0, suppressedUntil);
    }
}
