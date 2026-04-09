package com.waferalarm.web;

import com.waferalarm.domain.AlarmEntity;

import java.time.Instant;

public record AlarmDto(
        Long id,
        Long ruleId,
        Long parameterId,
        String contextKey,
        String state,
        String severity,
        int occurrenceCount,
        Instant firstViolationAt,
        Instant lastViolationAt,
        Double lastValue,
        Double thresholdValue,
        Instant createdAt,
        Instant suppressedUntil
) {
    public static AlarmDto from(AlarmEntity e) {
        return new AlarmDto(
                e.getId(), e.getRuleId(), e.getParameterId(),
                e.getContextKey(), e.getState().name(), e.getSeverity().name(),
                e.getOccurrenceCount(), e.getFirstViolationAt(),
                e.getLastViolationAt(), e.getLastValue(),
                e.getThresholdValue(), e.getCreatedAt(),
                e.getSuppressedUntil());
    }
}
