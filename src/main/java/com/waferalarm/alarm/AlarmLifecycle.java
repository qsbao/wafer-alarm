package com.waferalarm.alarm;

import com.waferalarm.domain.*;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class AlarmLifecycle {

    public AlarmSnapshot apply(AlarmEvent event, AlarmSnapshot current, Instant now) {
        if (current == null || current.state() == AlarmState.RESOLVED) {
            return newFiringAlarm(event);
        }

        if (current.state() == AlarmState.SUPPRESSED) {
            if (current.suppressedUntil() != null && now.isBefore(current.suppressedUntil())) {
                return current; // within suppression window — ignore
            }
            return newFiringAlarm(event); // window expired — reopen
        }

        // FIRING or ACKNOWLEDGED: accumulate occurrence
        return current.withOccurrence(event.violatingValue(), event.violationTime());
    }

    public AlarmSnapshot onCleanWafer(AlarmSnapshot current, int autoCloseThreshold) {
        int newCleanCount = current.consecutiveCleanCount() + 1;
        AlarmState newState = newCleanCount >= autoCloseThreshold
                ? AlarmState.RESOLVED : current.state();
        return new AlarmSnapshot(
                current.alarmId(), current.ruleId(), current.parameterId(),
                current.contextKey(), newState, current.severity(),
                current.occurrenceCount(), current.firstViolationAt(),
                current.lastViolationAt(), current.lastValue(),
                current.thresholdValue(), newCleanCount, current.suppressedUntil());
    }

    public AlarmSnapshot acknowledge(AlarmSnapshot current) {
        if (current.state() != AlarmState.FIRING) {
            throw new IllegalStateException(
                    "Cannot acknowledge alarm in state " + current.state());
        }
        return current.withState(AlarmState.ACKNOWLEDGED);
    }

    public AlarmSnapshot resolve(AlarmSnapshot current) {
        if (current.state() == AlarmState.RESOLVED) {
            throw new IllegalStateException("Alarm is already resolved");
        }
        return current.withState(AlarmState.RESOLVED);
    }

    public AlarmSnapshot suppress(AlarmSnapshot current, Instant until) {
        if (current.state() == AlarmState.RESOLVED) {
            throw new IllegalStateException(
                    "Cannot suppress a resolved alarm");
        }
        return new AlarmSnapshot(
                current.alarmId(), current.ruleId(), current.parameterId(),
                current.contextKey(), AlarmState.SUPPRESSED, current.severity(),
                current.occurrenceCount(), current.firstViolationAt(),
                current.lastViolationAt(), current.lastValue(),
                current.thresholdValue(), current.consecutiveCleanCount(), until);
    }

    private AlarmSnapshot newFiringAlarm(AlarmEvent event) {
        return new AlarmSnapshot(
                null,
                event.ruleId(),
                event.parameterId(),
                event.contextKey(),
                AlarmState.FIRING,
                event.severity(),
                1,
                event.violationTime(),
                event.violationTime(),
                event.violatingValue(),
                event.thresholdValue(),
                0,
                null
        );
    }
}
